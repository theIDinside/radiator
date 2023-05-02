@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

fun composeVersion(): String {
    return rootProject.extra.get("compose_version") as String
}

android {
    namespace = "com.app.radiator"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.app.radiator"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        signingConfig = signingConfigs.getByName("debug")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = composeVersion()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeVersion = rootProject.extra.get("compose_version")
    api(project(":libraries:rustsdk"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.20")
    // required to be able to use rustsdk
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

  // Nav Controller
    val navVersion = "2.5.3"
    implementation("androidx.navigation:navigation-compose:$navVersion")
    implementation("androidx.core:core-ktx:1.10.0")
    implementation("androidx.activity:activity-compose:1.7.1")
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")

    // unfortunately we have to pull in this "old" lib for ModalBottomSheetLayout
    implementation("androidx.compose.material:material")

    implementation("androidx.compose.material3:material3:1.1.0-rc01")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
}