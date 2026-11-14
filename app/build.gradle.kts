plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autoclicker.multi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autoclicker.multi"
        minSdk = 24          // dispatchGesture() nécessite Android 7.0+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Signature fixe : les mises à jour s'installent par-dessus sans perdre tes configurations
    signingConfigs {
        create("perso") {
            storeFile = file("autoclicker.keystore")
            storePassword = "autoclick123"
            keyAlias = "autoclicker"
            keyPassword = "autoclick123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("perso")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("perso")
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
