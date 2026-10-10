plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "pl.izzy.zlap"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.izzy.zlap"
        minSdk = 29
        targetSdk = 34
        // Każdy build na GitHubie dostaje wyższy numer, więc nowa wersja instaluje się na starą
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.$versionCode"
    }

    // Stały klucz podpisu z sekretów GitHuba: aktualizacje instalują się bez odinstalowywania
    // (i bez utraty listy). Bez sekretu (np. lokalnie) build podpisuje się kluczem debug.
    val keystorePath = System.getenv("ZLAP_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("zlap") {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("ZLAP_KEYSTORE_PASSWORD")
                keyAlias = "zlap"
                keyPassword = System.getenv("ZLAP_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystorePath != null) "zlap" else "debug")
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
