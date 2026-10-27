import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.secrets)
}

val supportedTdLibAbis = listOf("arm64-v8a", "armeabi-v7a")
val tdLibJniDir = project.layout.projectDirectory.dir("src/main/jniLibs").asFile
val tdLibAvailableAbis = supportedTdLibAbis.filter { abi ->
    val nativeLibrary = File(tdLibJniDir, "$abi/libtdjni.so")
    nativeLibrary.isFile && nativeLibrary.length() > 0L
}

val releaseStoreFile = System.getenv("RELEASE_STORE_FILE") ?: ""
val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: ""
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
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

    val devAbi = project.findProperty("devAbi") as? String

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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

        if (!devAbi.isNullOrBlank()) {
            ndk {
                abiFilters += listOf(devAbi)
            }
        }

        buildConfigField("boolean", "LEGACY_STREAMING_FALLBACK_ENABLED", "true")
    }

    androidResources {
        localeFilters += listOf("en")
    }

    val isBuildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    val isReleaseTask = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
    splits {
        abi {
            isEnable = isReleaseTask && devAbi.isNullOrBlank() && !isBuildingBundle
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
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "LEGACY_STREAMING_FALLBACK_ENABLED", "false")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
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
            buildConfigField("boolean", "LEGACY_STREAMING_FALLBACK_ENABLED", "false")
            matchingFallbacks += listOf("release")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "IconLauncherShape",
            "ChromeOsAbiSupport",
            // Style/resource hygiene is handled separately; release lint remains strict for
            // API compatibility, manifest, security, correctness, and Media3 opt-in issues.
            "ModifierParameter",
            "ObsoleteSdkInt",
            "UnusedResources",
            "UseKtx",
            "UseTomlInstead"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}

baselineProfile {
    warnings {
        maxAgpVersion = false
    }
}

// Production artifacts must never be unsigned or silently use the debug certificate. Keep the
// check in its own task: mutating AGP package tasks with doFirst closures breaks Gradle's
// configuration cache because those Android task types cannot serialize script references.
val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fail closed unless production release signing is configured"
    val signingConfigured = hasReleaseSigning
    inputs.property("signingConfigured", signingConfigured)
    doLast {
        if (!signingConfigured) {
            throw GradleException(
                "Refusing to build a production artifact: production signing is not configured. " +
                    "Set RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, and " +
                    "RELEASE_KEY_PASSWORD in local.properties or the build environment."
            )
        }
    }
}

tasks.matching {
    it.name in setOf("assembleRelease", "assembleHardenedRelease", "bundleRelease", "bundleHardenedRelease") ||
        it.name.startsWith("packageRelease") || it.name.startsWith("packageHardenedRelease")
}.configureEach {
    dependsOn(verifyReleaseSigning)
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
        if (!abis.contains("armeabi-v7a")) {
            throw GradleException(
                "Missing required armeabi-v7a/libtdjni.so. Bousygram targets 32-bit budget Android TVs; " +
                    "an APK without the 32-bit ARM TDLib runtime is not eligible for release. " +
                    "Available ABIs: $abis"
            )
        }
        logger.lifecycle("Verified non-empty TDLib native binaries for ABIs: $abis")
    }
}

tasks.register("verifyTvHardeningContract") {
    group = "verification"
    description = "Verify Android TV playback/navigation product invariants"

    val manifestFile = file("src/main/AndroidManifest.xml")
    val mainActivityFile = file("src/main/java/com/vaydelux/bousygram/app/MainActivity.kt")
    val playerScreenFile = file("src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt")
    val browseScreenFile = file("src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt")
    val dashboardFile = file("src/main/java/com/vaydelux/bousygram/presentation/layout/TvHybridHomeDashboard.kt")

    inputs.files(manifestFile, mainActivityFile, playerScreenFile, browseScreenFile, dashboardFile)

    doLast {
        val manifest = manifestFile.readText()
        val mainActivity = mainActivityFile.readText()
        val playerScreen = playerScreenFile.readText()
        val browseScreen = browseScreenFile.readText()
        val dashboard = dashboardFile.readText()

        require("supportsPictureInPicture" !in manifest) {
            "Android TV hardening regression: MainActivity must not declare PiP support."
        }
        require(listOf("PictureInPictureParams", "onUserLeaveHint", "enterPictureInPictureMode").none { it in mainActivity }) {
            "Android TV hardening regression: automatic or explicit PiP code returned to MainActivity."
        }
        require("PiP" !in playerScreen && "onEnterPip" !in playerScreen) {
            "Android TV hardening regression: the player exposes PiP again."
        }
        require("SeriesPosterCard(" !in browseScreen && "SeriesDetailScreen(" !in browseScreen) {
            "Browsing must present Telegram media directly instead of inferred series cards."
        }
        require("seriesGroups = emptyList()" in browseScreen) {
            "Home browsing must not receive inferred series groups."
        }
        require("filterNot { it == com.vaydelux.bousygram.domain.model.MediaCategoryTab.SERIES }" in dashboard) {
            "The inferred Series category must remain hidden from TV navigation."
        }
    }
}

tasks.matching {
    it.name in listOf(
        "preReleaseBuild",
        "preHardenedReleaseBuild",
        "assembleRelease",
        "assembleHardenedRelease",
        "bundleRelease",
        "bundleHardenedRelease"
    )
}.configureEach {
    dependsOn("verifyTdlibAbiCoverage")
    dependsOn("verifyTvHardeningContract")
}

tasks.register<Copy>("copyApksToDist") {
    group = "distribution"
    description = "Copies generated APKs to root dist/ directory and creates README.txt"
    dependsOn(tasks.matching { it.name in listOf("packageDebug", "packageRelease") })
    mustRunAfter(tasks.matching { it.name.startsWith("package") })
    val distDir = rootProject.file("dist")
    from(layout.buildDirectory.dir("outputs/apk")) {
        include("**/*.apk")
        eachFile {
            path = name
        }
    }
    into(distDir)
    includeEmptyDirs = false

    doLast {
        val readmeFile = File(distDir, "README.txt")
        distDir.mkdirs()
        readmeFile.writeText(
            """
            ================================================================================
            Bousygram APK Distribution Guide
            ================================================================================

            This directory contains APK builds optimized for Android TV and mobile devices.

            1. Bousygram-v<version>-<variant>-arm64-v8a.apk
               - Target Devices: Modern 64-bit Android TVs (Chromecast with Google TV 4K,
                 Nvidia Shield TV, high-end Sony Bravia / Philips TVs) and modern 64-bit Android phones/tablets.
               - Characteristics: Highest performance, lowest memory usage, 64-bit native code.

            2. Bousygram-v<version>-<variant>-armeabi-v7a.apk
               - Target Devices: Budget 32-bit Android TVs and streaming sticks (e.g., Xiaomi TV A Pro,
                 Mi Box / Mi TV Stick, Realme Smart TV Stick, Fire TV Stick HD / Lite / 4K 1st Gen,
                 Chromecast with Google TV HD, and low-RAM 1GB/2GB TVs running 32-bit Android).
               - Characteristics: Fully compatible with 32-bit-only kernels. Includes full TDLib
                 and libmpv/FFmpeg native libraries compiled for 32-bit ARM.

            3. Bousygram-v<version>-<variant>-universal.apk
               - Target Devices: Any ARM-based Android device (contains both arm64-v8a and armeabi-v7a).
               - Characteristics: Larger file size because it bundles all native architectures.
                 Use when device architecture is unknown or for general distribution.

            --------------------------------------------------------------------------------
            Installation on Android TV via ADB:
               PowerShell: .\scripts\install-tv.ps1 -TvIp <TV_IP_ADDRESS>
            ================================================================================
            """.trimIndent()
        )
        logger.lifecycle("APKs and README.txt successfully copied to: ${distDir.absolutePath}")
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

    // libmpv core engine
    implementation(libs.mpv)

    // Room database indexing
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
