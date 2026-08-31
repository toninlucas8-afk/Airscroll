pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "AirScroll"

include(":app")

// --- Core: motore, indipendente dalle singole app ---
include(":core:common")
include(":core:settings")
include(":core:vision")
include(":core:camera")
include(":core:gesture")
include(":core:control")
include(":core:overlay")
include(":core:power")
include(":core:health")
include(":core:voice")
include(":core:designsystem")

// --- App profiles: per supportare una nuova app basta aggiungere un modulo qui ---
include(":apps:api")
include(":apps:browser")
include(":apps:social")
include(":apps:reader")
