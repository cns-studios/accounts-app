plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cns.accounts"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cns.accounts"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Override per machine with -PcnsApiBaseUrl=... (e.g. a local auth-v2 on http://10.0.2.2:3845
        // from the emulator; plain-http hosts also need a network security config entry).
        val apiBaseUrl = providers.gradleProperty("cnsApiBaseUrl").getOrElse("https://api.cns-studios.com")
        // A build against another backend installs next to the real app instead of over it, so
        // its sessions and 2FA keys never mix with the production ones on the same device.
        if (apiBaseUrl != "https://api.cns-studios.com") applicationIdSuffix = ".local"
        val accountsWebUrl = providers.gradleProperty("cnsAccountsWebUrl").getOrElse("https://accounts.cns-studios.com")
        buildConfigField("String", "CNS_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "CNS_ACCOUNTS_WEB_URL", "\"$accountsWebUrl\"")

        // Push (Firebase Cloud Messaging), configured without google-services.json: set the
        // Firebase Android app's values as Gradle properties (e.g. in ~/.gradle/gradle.properties).
        // These identify the Firebase project, they are not secrets. Without them the app
        // builds and runs without push.
        fun firebase(name: String) = providers.gradleProperty(name).getOrElse("")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase("cnsFirebaseAppId")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebase("cnsFirebaseApiKey")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebase("cnsFirebaseProjectId")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${firebase("cnsFirebaseSenderId")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // Debug-signed so the release variant installs straight from Android Studio for
            // performance testing (debug builds are far slower in Compose). Swap for a real
            // upload key before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}