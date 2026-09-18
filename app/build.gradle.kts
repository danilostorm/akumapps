plugins {
    id("com.android.application")
}

android {
    namespace = "com.akuma.streamclub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.akuma.streamclub"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "KICK_DEFAULT_CHANNEL", "\"danilostorm\"")
        buildConfigField("String", "TWITCH_DEFAULT_CHANNEL", "\"danilostorm\"")
        buildConfigField("String", "APP_USER_AGENT", "\"AkumaStreamClub/1.0\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}
