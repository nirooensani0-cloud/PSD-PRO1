plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ir.mylog.missionlog"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.mylog.missionlog"
        minSdk = 24
        targetSdk = 34
        // هر نسخه جدید باید versionCode بالاتری داشته باشد تا اندروید آن را آپدیت تشخیص دهد
        versionCode = 3
        versionName = "2.1"
    }

    // کلید امضای ثابت داخل پروژه تا همه بیلدها (محلی و GitHub Actions) امضای یکسان داشته باشند
    // و نصب نسخه جدید روی نسخه قبلی بدون حذف و بدون از دست رفتن داده انجام شود.
    signingConfigs {
        create("release") {
            storeFile = file("../kashef.keystore")
            storePassword = "Kashef@1405"
            keyAlias = "kashef"
            keyPassword = "Kashef@1405"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    androidResources {
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // SQLCipher full database encryption
    implementation("net.zetetic:sqlcipher-android:4.6.1")

    // Offline Face Detection + MobileFaceNet TFLite
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // Hybrid offline-cached map (Road + Satellite)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Optional PDF password protection & encrypted ZIP backup/share
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
}
