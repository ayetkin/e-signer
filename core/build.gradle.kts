plugins {
    kotlin("jvm")
}

val dssVersion = "6.1"

dependencies {
    api("eu.europa.ec.joinup.sd-dss:dss-pades-pdfbox:$dssVersion")
    api("eu.europa.ec.joinup.sd-dss:dss-cades:$dssVersion")
    api("eu.europa.ec.joinup.sd-dss:dss-token:$dssVersion")
    implementation("eu.europa.ec.joinup.sd-dss:dss-utils-apache-commons:$dssVersion")
    // Validation: reports, default ETSI policy, online OCSP/CRL/AIA
    api("eu.europa.ec.joinup.sd-dss:dss-validation:$dssVersion")
    implementation("eu.europa.ec.joinup.sd-dss:dss-policy-jaxb:$dssVersion")
    implementation("eu.europa.ec.joinup.sd-dss:dss-service:$dssVersion")
    // CRL parsing has no default implementation; without it the first online CRL check fails at runtime.
    implementation("eu.europa.ec.joinup.sd-dss:dss-crl-parser-stream:$dssVersion")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

kotlin {
    jvmToolchain(21)
}

val pkcs11Exports: List<String> by rootProject.extra

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(pkcs11Exports)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(pkcs11Exports)
}
