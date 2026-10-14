pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // DSS's own repository (fallback if an artifact is missing on Maven Central)
        maven("https://ec.europa.eu/digital-building-blocks/artifact/content/repositories/esignaturedss/")
    }
}

rootProject.name = "e-signer"

include("core", "app")
