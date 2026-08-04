plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val keystorePath: String? = System.getenv("RING_KEYSTORE_PATH")

android {
    namespace = "dev.ringalarmwidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ringalarmwidget"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("RING_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RING_KEY_ALIAS")
                keyPassword = System.getenv("RING_KEY_PASSWORD")
            }
        }
    }

    androidResources {
        localeFilters += listOf("en", "fr", "nl", "de", "es")
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
