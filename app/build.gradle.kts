plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zorindisplays.display"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zorindisplays.display"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.text)
    implementation(libs.androidx.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lottie.compose)
    implementation(libs.emoji2)
    implementation(libs.androidx.compose.material3)
    implementation(libs.datastore.preferences)
    debugImplementation(libs.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
}

configurations.all {
    resolutionStrategy {
        force("androidx.emoji2:emoji2:1.3.0")
    }
}
