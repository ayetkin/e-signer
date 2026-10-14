plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
}

// Pkcs11Scanner (core) uses the JDK's internal PKCS#11 wrapper to list cards without logging in.
// Needed to compile core and to run tests and the app; run.sh repeats it for the packaged app.
extra["pkcs11Exports"] = listOf(
    "--add-modules", "jdk.crypto.cryptoki",
    "--add-exports", "jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED",
)
