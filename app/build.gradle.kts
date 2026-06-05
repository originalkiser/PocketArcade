import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

val keystoreProps = Properties().also { props ->
    val f = rootProject.file("keystore.properties")
    if (f.exists()) props.load(f.inputStream())
}

android {
    namespace = "com.pocketarcade"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketarcade"
        minSdk = 26
        targetSdk = 34
        versionCode = 39
        versionName = "2.0.14"

        buildConfigField("String", "ADMOB_APP_ID",        "\"ca-app-pub-3940256099942544~3347511713\"")
        buildConfigField("String", "BANNER_AD_UNIT_ID",   "\"ca-app-pub-3940256099942544/6300978111\"")
        buildConfigField("String", "BILLING_SKU_AD_FREE", "\"ad_free\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseCfg = signingConfigs.findByName("release")
            if (releaseCfg != null) signingConfig = releaseCfg
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
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
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
}
