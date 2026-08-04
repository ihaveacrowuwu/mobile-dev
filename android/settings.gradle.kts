pluginManagement {
    // build-logic must be included FIRST so its convention plugins exist before this
    // build resolves plugins.
    includeBuild("build-logic")

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
    // Fail loudly if a module declares its own repositories: all dependency
    // resolution must go through this single, auditable block.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    // Speeds up configuration by resolving the JDK toolchain from a known registry
    // instead of scanning the machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

// `projects.core.model` instead of `project(":core:model")`, a renamed module then becomes
// a compile error rather than a runtime "project not found".
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "SkyCast"

include(":app")
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:testing")
