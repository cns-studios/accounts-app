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
        // CI passes -PcnsVersionName=1.2.3 from the release tag; the code follows from it
        // (MAJOR*10000 + MINOR*100 + PATCH, see data/update/AppRelease.kt). Local builds stay 1.0.
        val releaseVersion = providers.gradleProperty("cnsVersionName").orNull
        versionName = releaseVersion ?: "1.0"
        versionCode = releaseVersion?.let { v ->
            val (major, minor, patch) = (v.split(".").map(String::toInt) + listOf(0, 0)).take(3)
            require(minor < 100 && patch < 100) { "version parts must be below 100: $v" }
            major * 10_000 + minor * 100 + patch
        } ?: 1

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
        // Sendly, for this app as a trusted Sendly device (-PcnsSendlyUrl=... for a local one).
        val sendlyUrl = providers.gradleProperty("cnsSendlyUrl").getOrElse("https://sendly.cns-studios.com")
        buildConfigField("String", "SENDLY_BASE_URL", "\"$sendlyUrl\"")
        // In-app updates: the latest GitHub release of this repository. Only release builds
        // signed with the release key and talking to production check (see buildTypes).
        buildConfigField("String", "UPDATE_RELEASES_URL", "\"https://api.github.com/repos/cns-studios/accounts-app/releases/latest\"")
        buildConfigField("boolean", "UPDATES_ENABLED", "false")

        // Push (Firebase Cloud Messaging). The Firebase values come from app/google-services.json
        // (downloaded from the Firebase console, kept out of git), read here instead of through
        // the google-services plugin so the ".local" builds, whose package the file doesn't list,
        // still build. Gradle properties (cnsFirebaseAppId, ...) override it. These identify the
        // Firebase project, they are not secrets. Without either the app builds and runs without push.
        val googleServices = file("google-services.json").takeIf { it.exists() }?.let {
            @Suppress("UNCHECKED_CAST")
            groovy.json.JsonSlurper().parse(it) as Map<String, Any?>
        }
        val googleServicesClient = (googleServices?.get("client") as? List<*>)?.map { it as Map<*, *> }?.firstOrNull { client ->
            val info = (client["client_info"] as Map<*, *>)["android_client_info"] as Map<*, *>
            info["package_name"] == "com.cns.accounts"
        }
        val projectInfo = googleServices?.get("project_info") as? Map<*, *>
        val fromFile = mapOf(
            "cnsFirebaseAppId" to (googleServicesClient?.get("client_info") as? Map<*, *>)?.get("mobilesdk_app_id"),
            "cnsFirebaseApiKey" to ((googleServicesClient?.get("api_key") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key"),
            "cnsFirebaseProjectId" to projectInfo?.get("project_id"),
            "cnsFirebaseSenderId" to projectInfo?.get("project_number"),
        )
        fun firebase(name: String) = providers.gradleProperty(name).orNull ?: fromFile[name]?.toString() ?: ""
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase("cnsFirebaseAppId")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebase("cnsFirebaseApiKey")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebase("cnsFirebaseProjectId")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${firebase("cnsFirebaseSenderId")}\"")
    }

    // The release key comes from the environment (CI: repository secrets, see
    // .github/workflows/release.yml). Without it the release variant is debug-signed, so it still
    // installs straight from Android Studio for performance testing, and never offers updates.
    val releaseKeystore = providers.environmentVariable("CNS_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("CNS_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("CNS_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("CNS_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
            val production = providers.gradleProperty("cnsApiBaseUrl").orNull.let { it == null || it == "https://api.cns-studios.com" }
            buildConfigField("boolean", "UPDATES_ENABLED", (releaseKeystore != null && production).toString())
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
    implementation(libs.androidx.fragment)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}