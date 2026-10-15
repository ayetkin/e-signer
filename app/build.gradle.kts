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

val pkcs11Exports: List<String> by rootProject.extra

application {
    mainClass.set("sh.yetkin.esigner.app.MainKt")
    // Trackpad pinch (MacGestures.kt); only a warning on other platforms.
    applicationDefaultJvmArgs = pkcs11Exports + listOf("--add-exports", "java.desktop/com.apple.eawt.event=ALL-UNNAMED")
}

// Shown in About and compared with the latest GitHub release.
val appVersion = providers.gradleProperty("appVersion").get()
tasks.processResources {
    inputs.property("appVersion", appVersion)
    filesMatching("version.properties") { expand("version" to appVersion) }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(pkcs11Exports)
}
