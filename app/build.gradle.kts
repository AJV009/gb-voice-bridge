plugins {
    id("com.android.application")
}

android {
    namespace = "com.ajv009.gbvoice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ajv009.gbvoice"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Offline speech recognition (Whisper) with onnxruntime linked in; Kotlin API needs the stdlib
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-2.0.1.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
}
