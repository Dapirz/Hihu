plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.daffa.hihu"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.daffa.hihu"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    // Two apps from one codebase: the standard one has no account at all; Hihu G signs in to
    // YouTube Music. Hihu G has its own package, so both install side by side with their own data.
    flavorDimensions += "account"
    productFlavors {
        create("standard") {
            dimension = "account"
            isDefault = true
        }
        create("google") {
            dimension = "account"
            applicationIdSuffix = ".g"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal builds are signed with the debug key; a public release needs its own key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor uses Java APIs that older Android versions lack.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":views"))
    implementation(project(":music:local"))
    implementation(project(":music:youtube"))
    implementation(project(":music:lrclib"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    coreLibraryDesugaring(libs.desugar)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
}
