plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gtasa.coop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gtasa.coop"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }
}
