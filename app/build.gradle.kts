plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.athena.offline"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.athena.offline"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
