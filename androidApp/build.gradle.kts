plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("android")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.anchor.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.anchor.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        create("release") {
            // Values injected at build time via environment variables (CI) or local.properties (dev).
            // Set ANCHOR_STORE_FILE, ANCHOR_STORE_PASSWORD, ANCHOR_KEY_ALIAS, ANCHOR_KEY_PASSWORD
            // as repository secrets in GitHub Actions.
            val storeFilePath = System.getenv("ANCHOR_STORE_FILE")
                ?: (project.findProperty("anchor.storeFile") as String?)
            val storePass = System.getenv("ANCHOR_STORE_PASSWORD")
                ?: (project.findProperty("anchor.storePassword") as String?)
            val keyAls = System.getenv("ANCHOR_KEY_ALIAS")
                ?: (project.findProperty("anchor.keyAlias") as String?)
            val keyPass = System.getenv("ANCHOR_KEY_PASSWORD")
                ?: (project.findProperty("anchor.keyPassword") as String?)

            if (storeFilePath != null && storePass != null && keyAls != null && keyPass != null) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAls
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    implementation(libs.kotlinx.datetime)
}
