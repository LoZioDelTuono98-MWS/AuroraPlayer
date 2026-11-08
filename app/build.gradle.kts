plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aurora.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aurora.player"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }

    signingConfigs {
        // Chiave fissa nel progetto: ogni nuova build si installa sopra la precedente.
        create("aurora") {
            storeFile = file("aurora.keystore")
            storePassword = "aurora123"
            keyAlias = "aurora"
            keyPassword = "aurora123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("aurora")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("aurora")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Sfocatura "vetro" in tempo reale dietro ai pannelli (backdrop blur)
    implementation("dev.chrisbanes.haze:haze:1.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
