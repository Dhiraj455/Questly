plugins {
    `kotlin-dsl`
}

group = "com.example.questly.buildlogic"

dependencies {
    compileOnly(libs.plugins.android.library.toDep())
    compileOnly(libs.plugins.kotlin.android.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "questly.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
