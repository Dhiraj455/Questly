import java.util.Properties

plugins {
    id("questly.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Backend base URL. Defaults to the emulator's host alias for local dev; override in
// local.properties (QUESTLY_API_BASE_URL=https://your-app.onrender.com/v1) to use the deployed API.
val apiBaseUrl: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("QUESTLY_API_BASE_URL", "http://10.0.2.2:8081/v1")

android {
    namespace = "com.example.questly.core.network"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "QUESTLY_API_BASE_URL", "\"$apiBaseUrl\"")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
