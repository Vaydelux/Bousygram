import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val supportedTdLibAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val tdLibJniDir = project.layout.projectDirectory.dir("src/main/jniLibs").asFile
val tdLibAvailableAbis = supportedTdLibAbis.filter { abi ->
    val nativeLibrary = File(tdLibJniDir, "$abi/libtdjni.so")
    nativeLibrary.isFile && nativeLibrary.length() > 0L
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


android {
    namespace = "com.vaydelux.bousygram"
    compileSdk {
        version = release(37)
    }

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
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Phase 2 readiness: package only ABIs where TDLib native binary is present.
            // This keeps phone/tablet/TV installs aligned and avoids startup UnsatisfiedLinkError.
            abiFilters += tdLibAvailableAbis
        }

        val apiId = properties.getProperty("TELEGRAM_API_ID") ?: ""
        val apiHash = properties.getProperty("TELEGRAM_API_HASH") ?: ""
        val tmdbApiKey = properties.getProperty("TMDB_API_KEY") ?: System.getenv("TMDB_API_KEY") ?: ""
        buildConfigField("String", "TELEGRAM_API_ID", "\"$apiId\"")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$apiHash\"")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("boolean", "LEGACY_STREAMING_FALLBACK_ENABLED", "true")

    }

    val isBuildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    splits {
        abi {
            isEnable = !isBuildingBundle
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    bundle {
        abi {
            enableSplit = true
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "LEGACY_STREAMING_FALLBACK_ENABLED", "false")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    testOptions {
        unitTests.isReturnDefaultValues = true
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

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val versionName = output.versionName.orNull ?: "1.0"
            val abi = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
            val suffix = if (abi != null) "-$abi" else "-universal"
            output.outputFileName.set("Bousygram-v$versionName-${variant.name}$suffix.apk")
        }
    }
}

// Inform if production signing credentials are not present in local.properties.
if (!hasReleaseSigning) {
    logger.info(
        "Production signing is not configured. Set RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
            "RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD in local.properties for production signing."
    )
}

tasks.register("verifyTdlibAbiCoverage") {
    group = "verification"
    description = "Verify TDLib ABI coverage for release targets"
    val abis = tdLibAvailableAbis
    doLast {
        if (abis.isEmpty()) {
            throw GradleException(
                "Missing TDLib native binaries. Expected non-empty libtdjni.so files under src/main/jniLibs/<abi>/."
            )
        }
        if (!abis.contains("arm64-v8a")) {
            throw GradleException(
                "Missing required arm64-v8a/libtdjni.so. Bousygram targets modern Android phones and TVs; " +
                    "an APK without the arm64 TDLib runtime is not eligible for release. " +
                    "Available ABIs: $abis"
            )
        }
        logger.lifecycle("Verified non-empty TDLib native binaries for ABIs: $abis")
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

    // libmpv core engine
    implementation(libs.mpv)

    // Room database indexing
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
