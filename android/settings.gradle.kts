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
    }
}

rootProject.name = "MENCH"
include(":app")
// Phase 1B completion: Macrobenchmark + Baseline Profile generator (needs a physical device or emulator API 28+).
include(":benchmark")
