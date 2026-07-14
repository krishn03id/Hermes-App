pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Termux terminal-view/-emulator are published here (native libtermux.so
        // is prebuilt inside the AAR, so no NDK is needed to build Hermes).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Hermes"
include(":app")
