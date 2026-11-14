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

rootProject.name = "quiet-numbers"

// Shared port of QuietKit (QuietCore and QuietUI), kept next to the Swift package.
include(":quietkit-core")
project(":quietkit-core").projectDir = file("../../QuietKit/Android/quietkit-core")

include(":quietkit-ui")
project(":quietkit-ui").projectDir = file("../../QuietKit/Android/quietkit-ui")

include(":numbers-core")
include(":numbers-app")
