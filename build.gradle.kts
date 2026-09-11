plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.thunderfire.jarvis3d"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thunderfire.jarvis3d"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

dependencies {
    implementation("com.google.android.filament:filament-android:1.76.1")
    implementation("com.google.android.filament:gltfio-android:1.76.1")
    implementation("com.google.android.filament:filament-utils-android:1.76.1")
}
