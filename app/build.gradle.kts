plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ajv009.gbvoice"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ajv009.gbvoice"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.3"
        ndk {
            abiFilters += "arm64-v8a"
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
    // Offline speech recognition (Whisper, Parakeet) with onnxruntime linked in
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-2.0.1.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.core:core-ktx:1.19.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
