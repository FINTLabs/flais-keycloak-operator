plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("gradle-plugin"))

    implementation(libs.fabric8.generator.api)
    implementation(libs.fabric8.generator.collector)
    implementation(libs.helm.java)
}