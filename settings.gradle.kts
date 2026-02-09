pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // 1. 여기에도 있고 (잘 하셨습니다!)
        maven("https://repository.map.naver.com/archive/maven")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 2. ⭐ 여기에 반드시 추가해야 합니다! (실제 라이브러리용)
        maven("https://repository.map.naver.com/archive/maven")
    }
}

rootProject.name = "manager1"
include(":app")