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
        // Xposed 真正的官方专属仓库
        maven { url = uri("https://api.xposed.info/") }
        // 增加 JitPack 备用防错
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "HT_AI_Translator"
include(":app")
