import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 auto-applies built-in Kotlin for android modules; no separate
        // org.jetbrains.kotlin.android needed (applying it conflicts).
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = 37
            defaultConfig {
                minSdk = 24
                // Lets any library module run instrumented (androidTest) tests on a device/emulator.
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
    }
}
