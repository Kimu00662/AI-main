plugins {
    id("com.android.application")
}

android {
    namespace = "com.aihellotalk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aihellotalk"
        minSdk = 27
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Xposed API (compileOnly，运行时不打包)
    compileOnly("de.robv.android.xposed:api:82")
    
    // OkHttp 4.x（打包进APK）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 👇 新增：DrawerLayout 侧滑菜单库
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
}
