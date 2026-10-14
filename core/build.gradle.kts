plugins {
    kotlin("jvm")
}

val dssVersion = "6.1"

dependencies {
    api("eu.europa.ec.joinup.sd-dss:dss-pades-pdfbox:$dssVersion")
    api("eu.europa.ec.joinup.sd-dss:dss-cades:$dssVersion")
    api("eu.europa.ec.joinup.sd-dss:dss-token:$dssVersion")
    implementation("eu.europa.ec.joinup.sd-dss:dss-utils-apache-commons:$dssVersion")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
