plugins { id("com.android.application") }
android {
    namespace = "fr.nauss.choptorch"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "fr.nauss.choptorch.diagnostic"
        minSdk = 37
        targetSdk = 37
        versionCode = 4
        versionName = "0.4-diagnostic"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint {
        abortOnError = true
        warningsAsErrors = true
        // Deliberately pinned, documented compatible toolchain; updates are reviewed, not automatic.
        disable += "AndroidGradlePluginVersion"
    }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
dependencies { testImplementation("junit:junit:4.13.2") }
