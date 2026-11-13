plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.shimpyo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shimpyo.app"
        minSdk = 28 // Pleos Connect requirement (signing key rotation / lineage)
        targetSdk = 34 // Pleos Connect Emulator v2.1.0 runs API 34
        versionCode = 1
        versionName = "0.1.0"

        // Demo strip (time scale, check-in, arrival, answer buttons) in every build of the contest entry:
        // judges run the release APK on the emulator and cannot drive for 90 minutes to reach a check-in.
        // TODO(release): Set to false for Pleos App Market builds.
        buildConfigField("boolean", "DEMO_CONTROLS", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // TODO(release): The contest needs a runnable release APK (팀명_앱이름_release.apk).
            //  Debug signing is used until a team keystore exists; Pleos App Market needs a proper key
            //  with signing key rotation (lineage) support.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Pleos Connect SDK (Pleos Connect SDK License Agreement, 42dot — accepted 2026-11-09)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")

    // DI
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Storage, widget, coroutines
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
