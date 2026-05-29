import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val supportedTdLibAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val tdLibJniDir = project.layout.projectDirectory.dir("src/main/jniLibs").asFile
val tdLibAvailableAbis = supportedTdLibAbis.filter { abi ->
    File(tdLibJniDir, "$abi/libtdjni.so").exists()
}

android {
    namespace = "com.vaydelux.bousygram"
    compileSdk {
        version = release(37)
    }

    val properties = Properties().apply {
        val localProperties = rootProject.file("local.properties")
        if (localProperties.exists()) {
            localProperties.inputStream().use { stream ->
                load(stream)
            }
        }
    }

    val releaseStoreFile = properties.getProperty("RELEASE_STORE_FILE")
        ?: System.getenv("RELEASE_STORE_FILE")
        ?: ""
    val releaseStorePassword = properties.getProperty("RELEASE_STORE_PASSWORD")
        ?: System.getenv("RELEASE_STORE_PASSWORD")
        ?: ""
    val releaseKeyAlias = properties.getProperty("RELEASE_KEY_ALIAS")
        ?: System.getenv("RELEASE_KEY_ALIAS")
        ?: ""
    val releaseKeyPassword = properties.getProperty("RELEASE_KEY_PASSWORD")
        ?: System.getenv("RELEASE_KEY_PASSWORD")
        ?: ""
    val hasReleaseSigning = releaseStoreFile.isNotBlank() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank() &&
        file(releaseStoreFile).exists()

    signingConfigs {
        create("release") {
            if (releaseStoreFile.isNotBlank()) {
                storeFile = file(releaseStoreFile)
            }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    defaultConfig {
        applicationId = "com.vaydelux.bousygram"
        minSdk = 23
        targetSdk = 37
        versionCode = 3
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Phase 2 readiness: package only ABIs where TDLib native binary is present.
            // This keeps phone/tablet/TV installs aligned and avoids startup UnsatisfiedLinkError.
            abiFilters += tdLibAvailableAbis
        }

        val apiId = properties.getProperty("TELEGRAM_API_ID") ?: ""
        val apiHash = properties.getProperty("TELEGRAM_API_HASH") ?: ""
        buildConfigField("String", "TELEGRAM_API_ID", "\"$apiId\"")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$apiHash\"")

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Keep release installable for local validation while warning loudly.
                signingConfig = signingConfigs.getByName("debug")
                logger.warn(
                    "Release keystore credentials are missing or invalid. " +
                        "Using debug signing for local release verification."
                )
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("hardenedRelease") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        baseline = file("lint-baseline.xml")
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "IconLauncherShape",
            "ChromeOsAbiSupport"
        )
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

}

tasks.register("verifyTdlibAbiCoverage") {
    group = "verification"
    description = "Verify TDLib ABI coverage for release targets"
    doLast {
        if (tdLibAvailableAbis.isEmpty()) {
            throw GradleException(
                "Missing TDLib native binaries. Expected at least one libtdjni.so under src/main/jniLibs/<abi>/."
            )
        }
        if (!tdLibAvailableAbis.contains("arm64-v8a")) {
            logger.warn(
                "TDLib arm64-v8a binary is missing. Phone/tablet/TV modern devices may fail install/startup. " +
                    "Current available ABIs: $tdLibAvailableAbis"
            )
        }
        logger.lifecycle("TDLib available ABIs: $tdLibAvailableAbis")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.server.netty)
    implementation(libs.zxing.core)

    // Jetpack Compose (BOM + core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose for TV and image loading
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)

    // AndroidX Media3 ExoPlayer for premium native video playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.hls)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
