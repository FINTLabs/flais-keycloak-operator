plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.docker.compose)
    alias(libs.plugins.fabric8.generator)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.ktlint)
}

group = "no.novari"
version = "1.0.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

val operatorImage = "flais-keycloak-operator:dev"
val operatorImageRepository = operatorImage.substringBeforeLast(":")
val operatorImageTag = operatorImage.substringAfterLast(":")
val operatorImageTar = layout.buildDirectory.file("docker/flais-keycloak-operator-dev.tar")

dependencies {
    implementation(platform(libs.http4k.bom))
    implementation(platform(libs.koin.bom))

    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.bundles.fabric8.generator)
    implementation(libs.bundles.operator)

    implementation(libs.bundles.http4k)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.hoplite)
    implementation(libs.bundles.logging)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.keycloak.admin.client)
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.helm.java)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.koin)

    testRuntimeOnly(libs.bundles.junit)
    testRuntimeOnly(libs.slf4j.simple)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val mainSourceSet = sourceSets.named("main")

tasks.register<GenerateCrdsTask>("generateCrds") {
    group = "crd"
    description = "Generate CRDs for operator"

    compileClasspath.from(mainSourceSet.map { it.compileClasspath })
    outputClassesDirs.from(mainSourceSet.map { it.output.classesDirs })

    includePackages.set(listOf("no.novari.application.api"))

    targetDirectory.set(
        layout.projectDirectory.dir("charts/flais-keycloak-operator-crd/charts/crds/templates")
    )

    dependsOn(tasks.named("classes"))
}

dockerCompose {
    environment.put("KEYCLOAK_VERSION", libs.versions.keycloak.get())

    environment.put("KEYCLOAK_BASE_URL", "http://keycloak:8080")
    environment.put("KEYCLOAK_EXTERNAL_URL", "http://localhost:8080")
    environment.put("KEYCLOAK_ADMIN_USERNAME", "admin")
    environment.put("KEYCLOAK_ADMIN_PASSWORD", "admin")
}

tasks.withType<Test>().configureEach {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
    systemProperty("project.rootDir", rootProject.projectDir.absolutePath)
    systemProperty("project.version", project.version.toString())
    systemProperty("operator.image", operatorImage)
    systemProperty("operator.image.tar", operatorImageTar.get().asFile.absolutePath)

    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())
}

@Suppress("UnstableApiUsage")
fun JvmTestSuite.addSuiteSources(name: String) {
    sources {
        kotlin {
            srcDir("src/test/$name/kotlin")
        }
    }
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
                    named("${name}Implementation") {
                        extendsFrom(configurations.testImplementation.get())
                    }

                    named("${name}RuntimeOnly") {
                        extendsFrom(configurations.testRuntimeOnly.get())
                    }

                    named("${name}CompileOnly") {
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
                        group = "verification"
                        description = "Runs integration tests."

                        dependsOn("saveOperatorDockerImage")

                        systemProperty(
                            "operator.image.tar",
                            operatorImageTar.get().asFile.absolutePath,
                        )
                    }
                }
            }
        }
    }
}

tasks.register<Exec>("buildOperatorDockerImage") {
    group = "docker"
    description = "Build operator Docker image for integration tests"

    dependsOn("jar", "generateCrds")

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
        "bash",
        "-c",
        """
        docker save "$operatorImage" | docker compose exec -T server ctr -n k8s.io images import -
        docker save "$operatorImage" | docker compose exec -T agent ctr -n k8s.io images import -
        """.trimIndent(),
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

    commandLine("./gradlew", "dependencyUpdates")
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
            val targetFile = buildDirectory.file("libs/${it.name}").get().asFile

            if (!targetFile.exists()) {
                it.copyTo(targetFile)
            }
        }
    }
}
