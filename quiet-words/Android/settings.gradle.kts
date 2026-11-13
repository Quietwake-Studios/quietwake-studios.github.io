pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "quiet-words"

// Shared port of QuietKit/QuietCore, kept next to the Swift package.
include(":quietkit-core")
project(":quietkit-core").projectDir = file("../../QuietKit/Android/quietkit-core")

include(":words-core")
