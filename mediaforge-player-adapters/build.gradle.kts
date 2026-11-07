plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.vaydelux.mediaforge.players"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {
    api(project(":mediaforge-core"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation("androidx.media3:media3-exoplayer-dash:" + libs.versions.media3.get())
    implementation(libs.androidx.media3.ui)
    implementation("androidx.media3:media3-datasource-okhttp:" + libs.versions.media3.get())
    implementation("com.squareup.okhttp3:okhttp:4.12.1")
    implementation(libs.kotlinx.coroutines.android)
}
