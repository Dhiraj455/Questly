plugins {
    id("questly.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.questly.core.database"
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
