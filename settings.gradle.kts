pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 💡 修改这里：将 FAIL_ON_PROJECT_REPOS 改为 PREFER_PROJECT
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
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
