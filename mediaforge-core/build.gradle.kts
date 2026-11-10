plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.vaydelux.mediaforge.core"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {
    // The public session API exposes StateFlow; keep its type on the AAR API path.
    api(libs.kotlinx.coroutines.android)
    implementation("com.squareup.okhttp3:okhttp:4.12.1")
    testImplementation(libs.junit)
}
