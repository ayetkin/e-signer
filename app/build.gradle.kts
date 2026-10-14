plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    application
}

// Skiko native binaries are platform specific. Builds run inside a Linux container,
// so the target desktop platform is chosen explicitly: -PcomposeTarget=macos-arm64
val composeTarget = providers.gradleProperty("composeTarget").orNull

dependencies {
    implementation(project(":core"))
    implementation(
        when (composeTarget) {
            null -> compose.desktop.currentOs
            "macos-arm64" -> compose.desktop.macos_arm64
            "macos-x64" -> compose.desktop.macos_x64
            "windows-x64" -> compose.desktop.windows_x64
            "linux-arm64" -> compose.desktop.linux_arm64
            "linux-x64" -> compose.desktop.linux_x64
            else -> error("Unknown composeTarget: $composeTarget")
        }
    )
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

// The card scanner in core uses the JDK's internal PKCS#11 wrapper.
val pkcs11Exports = listOf(
    "--add-modules", "jdk.crypto.cryptoki",
    "--add-exports", "jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED",
)

application {
    mainClass.set("sh.yetkin.esigner.app.MainKt")
    applicationDefaultJvmArgs = pkcs11Exports
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(pkcs11Exports)
}
