plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
    // DSS'in kendi deposu (Maven Central'da bulunamazsa yedek)
    maven("https://ec.europa.eu/digital-building-blocks/artifact/content/repositories/esignaturedss/")
}

val dssVersion = "6.1"

dependencies {
    implementation("eu.europa.ec.joinup.sd-dss:dss-pades-pdfbox:$dssVersion")
    implementation("eu.europa.ec.joinup.sd-dss:dss-cades:$dssVersion")
    implementation("eu.europa.ec.joinup.sd-dss:dss-token:$dssVersion")
    implementation("eu.europa.ec.joinup.sd-dss:dss-utils-apache-commons:$dssVersion")
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("MainKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
