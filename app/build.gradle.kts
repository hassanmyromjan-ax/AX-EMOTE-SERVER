
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.axemote.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.axemote.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        debug { }
        release {
            isMinifyEnabled = false
            val ks = System.getenv("AX_KEYSTORE_PATH")
            if (ks != null) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(ks)
                    storePassword = System.getenv("AX_STORE_PASSWORD")
                    keyAlias = System.getenv("AX_KEY_ALIAS")
                    keyPassword = System.getenv("AX_KEY_PASSWORD")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
