@file:Suppress("UnstableApiUsage")

include(":loader:sbl")


include(":loader:hookapi")


enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}


dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://jitpack.io")
        maven("https://api.xposed.info/") {
            content {
                includeGroup("de.robv.android.xposed")
            }
        }

        maven (url = "https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        mavenLocal {
            content {
                includeGroup("io.github.libxposed")
            }
        }
        versionCatalogs {
            create("libs")
        }
        mavenCentral()
    }
}

buildscript {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://storage.googleapis.com/r8-releases/raw")
        }
    }
    dependencies {
        classpath("com.android.tools:r8:8.7.18")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
}

rootProject.name = "ono"

includeBuild("build-logic")

include(
    ":app",
    ":libs:stub:qq-stub",
    ":libs:ui:xView",
    ":libs:util:libxposed:api",
    ":libs:util:libxposed:service",
    ":libs:util:ezxhelper",
    ":libs:util:annotation-scanner",
)

