plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.local.questwebcinema"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.local.questwebcinema"
        minSdk = 34
        targetSdk = 34
        versionCode = 12
        versionName = "0.10.2"
        ndk { abiFilters += "arm64-v8a" }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getenv("CINEMA_KEYSTORE") ?: "../../signing/quest-web-cinema-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
dependencies {
    implementation("com.meta.spatial:meta-spatial-sdk-toolkit:0.14.0")
    implementation("com.meta.spatial:meta-spatial-sdk-vr:0.14.0")
}
