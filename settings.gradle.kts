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
        // Dynamsoft Capture Vision (document scanning) — not on Maven Central.
        maven { url = uri("https://download2.dynamsoft.com/maven/aar") }
    }
}

rootProject.name = "DocUpload"
include(":app")
