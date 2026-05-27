plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pocketarcade"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketarcade"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        // Swap these for prod values in your release build config or CI secrets
        buildConfigField("String", "ADMOB_APP_ID",      "\"ca-app-pub-3940256099942544~3347511713\"")
        buildConfigField("String", "BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
        buildConfigField("String", "BILLING_SKU_AD_FREE", "\"ad_free\"")
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)
    implementation(libs.work.runtime.ktx)
}
