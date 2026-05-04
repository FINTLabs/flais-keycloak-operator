import org.gradle.kotlin.dsl.invoke

plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.docker.compose)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.fabric8.generator)
}

group = "no.novari"
version = "1.0.0"

sourceSets {
    main { java { srcDirs(layout.buildDirectory.dir("generated/source/kubernetes/main")) } }
}

val operatorImage = "flais-keycloak-operator:dev"
val operatorImageRepository = operatorImage.substringBeforeLast(":")
val operatorImageTag = operatorImage.substringAfterLast(":")
val operatorImageTar = layout.buildDirectory.file("docker/flais-keycloak-operator-dev.tar")

dependencies {
    testRuntimeOnly(libs.bundles.junit)
    testRuntimeOnly(libs.slf4j.simple)

    testImplementation(libs.keycloak.admin.client)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp)
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.helm.java)

    implementation(platform(libs.http4k.bom))
    implementation(platform(libs.koin.bom))

    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.bundles.fabric8.generator)
    implementation(libs.bundles.operator)

    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.bundles.http4k)

    implementation(libs.bundles.koin)

    implementation(libs.jackson.module.kotlin)

    implementation(libs.bundles.hoplite)
    implementation(libs.bundles.logging)

    implementation(libs.keycloak.admin.client)
}

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

tasks {
    javaGen {
        source = file(layout.projectDirectory.dir("src/main/resources/kubernetes"))
        target = file(layout.buildDirectory.dir("generated/source/kubernetes/main"))
    }

    compileKotlin {
        dependsOn(crd2java)
    }

    compileJava {
        dependsOn(crd2java)
    }

    register<GenerateCrdsTask>("generateCrds") {
        description = "Generate CRDs for operator"
        group = "crd"

        sourceSet = sourceSets.main
        includePackages = listOf("no.novari.application.api")
        targetDirectory =
            project.layout.projectDirectory.dir("charts/flais-keycloak-operator-crd/charts/crds/templates")

        dependsOn(compileJava, compileKotlin)
    }
}

dockerCompose {
    environment.put("KEYCLOAK_VERSION", libs.versions.keycloak.get())

    environment.put("KEYCLOAK_BASE_URL", "http://keycloak:8080")
    environment.put("KEYCLOAK_EXTERNAL_URL", "http://localhost:8080")
    environment.put("KEYCLOAK_ADMIN_USERNAME", "admin")
    environment.put("KEYCLOAK_ADMIN_PASSWORD", "admin")
}

@Suppress("UnstableApiUsage")
fun JvmTestSuite.addSuiteSources(name: String) {
    sources {
        kotlin {
            srcDir("src/test/$name/kotlin")
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    systemProperty("project.rootDir", rootProject.projectDir.absolutePath)
    systemProperty("project.version", project.version.toString())
    systemProperty("operator.image", operatorImage)
    systemProperty("operator.image.tar", operatorImageTar.get().asFile.absolutePath)

    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())
}

@Suppress("UnstableApiUsage", "unused")
testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()

            dependencies {
                implementation(project())
            }

            if (name != "test") {
                configurations {
                    named("${name}Implementation").configure {
                        extendsFrom(configurations.testImplementation.get())
                    }
                    named("${name}RuntimeOnly").configure {
                        extendsFrom(configurations.testRuntimeOnly.get())
                    }
                    named("${name}CompileOnly").configure {
                        extendsFrom(configurations.testCompileOnly.get())
                    }
                }
            }
        }

        val integrationTest by registering(JvmTestSuite::class) {
            addSuiteSources("integration")

            targets {
                all {
                    testTask.configure {
                        description = "Runs integration tests."
                        group = "verification"

                        dependsOn("saveOperatorDockerImage")

                        systemProperty("operator.image.tar", operatorImageTar.get().asFile.absolutePath)
                    }
                }
            }
        }
    }
}

tasks.register<Exec>("buildOperatorDockerImage") {
    group = "docker"
    description = "Build operator Docker image for integration tests"

    dependsOn("generateCrds")

    workingDir(rootDir)

    commandLine(
        "docker",
        "build",
        "-t",
        operatorImage,
        ".",
    )
}

tasks.register<Exec>("saveOperatorDockerImage") {
    group = "docker"
    description = "Save operator Docker image as tar for K3s integration tests"

    dependsOn("buildOperatorDockerImage")

    val tarFile = operatorImageTar.get().asFile

    doFirst {
        tarFile.parentFile.mkdirs()
    }

    commandLine(
        "docker",
        "save",
        "-o",
        tarFile.absolutePath,
        operatorImage,
    )
}

tasks.register<Exec>("importOperatorImageToK3s") {
    group = "docker"
    description = "Import operator Docker image into k3s"

    dependsOn("buildOperatorDockerImage")

    commandLine(
        "bash", "-c",
        """
        docker save "$operatorImage" | docker compose exec -T server ctr -n k8s.io images import -
        docker save "$operatorImage" | docker compose exec -T agent ctr -n k8s.io images import -
        """.trimIndent()
    )
}

tasks.register<InstallOperatorTask>("installOperator") {
    group = "docker"
    description = "Build and install operator into local k3s cluster"

    dependsOn("importOperatorImageToK3s")

    kubeConfig = file("$rootDir/data/kubeconfig/kubeconfig.yaml")
    namespace = "default"
    image = operatorImage
    crdChartDir = file("$rootDir/charts/flais-keycloak-operator-crd")
    operatorChartDir = file("$rootDir/charts/flais-keycloak-operator")
    chartVersion = project.version.toString()
}

tasks.register("runDev") {
    group = "docker"
    description = "Run local dev with compose"

    doLast {
        dockerCompose.dockerExecutor.execute(
            "compose",
            "-f",
            "docker-compose.yaml",
            "-f",
            "docker-compose.dev.yaml",
            "up",
            "-d",
            "--build",
            "keycloak",
        )
        println("Built & started flais-keycloak-operator dev environment")
    }

    finalizedBy("installOperator")
}

tasks.register("stopDev") {
    group = "docker"
    description = "Stop local dev"

    doLast {
        dockerCompose.dockerExecutor.execute("compose", "stop")
        println("Stopped flais-keycloak-operator dev environment")
    }
}

tasks.register("cleanupDev") {
    group = "docker"
    description = "Remove local dev docker"

    doLast {
        dockerCompose.dockerExecutor.execute("compose", "rm", "-s", "-f")
        println("Cleaned up flais-keycloak-operator dev environment")
    }
}

tasks.register<Exec>("checkDeps") {
    group = "tools"
    description = "Check dependencies for new versions"
    doFirst {
        commandLine("./gradlew", "dependencyUpdates")
    }
}

tasks.jar {
    archiveFileName.set("app.jar")

    manifest {
        attributes["Main-Class"] = "no.novari.ApplicationKt"
        attributes["Class-Path"] =
            configurations.runtimeClasspath.get().joinToString(" ") { it.name }
    }

    val runtimeDeps = configurations.runtimeClasspath.get().map { it.toPath().toFile() }
    val buildDirectory = layout.buildDirectory

    doLast {
        runtimeDeps.forEach {
            val file = buildDirectory.file("libs/${it.name}").get().asFile
            if (!file.exists()) {
                it.copyTo(file)
            }
        }
    }
}
