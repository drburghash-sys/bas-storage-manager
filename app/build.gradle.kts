plugins {
    id("com.android.application")
}

android {
    namespace = "com.bas.storage"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bas.storage"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
