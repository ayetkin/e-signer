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

// Pkcs11Scanner uses the JDK's internal PKCS#11 wrapper to list cards without logging in.
val pkcs11Exports = listOf(
    "--add-modules", "jdk.crypto.cryptoki",
    "--add-exports", "jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED",
)

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(pkcs11Exports)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(pkcs11Exports)
}
