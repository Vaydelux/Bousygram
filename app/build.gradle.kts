import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
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


// Android accepts version codes <= 2_100_000_000. Reserving three digits for
// minor and patch keeps each future tag strictly greater than the previous one.
fun bousygramVersionCode(version: String): Int {
    val parts = version.split(".")
    require(parts.size == 3 && parts.all { it.matches(Regex("(0|[1-9][0-9]*)")) }) {
        "releaseVersionName must be numeric MAJOR.MINOR.PATCH with no leading zeros"
    }
    val major = parts[0].toLongOrNull()
    val minor = parts[1].toLongOrNull()
    val patch = parts[2].toLongOrNull()
    require(major != null && major in 0L..2099L &&
        minor != null && minor in 0L..999L &&
        patch != null && patch in 0L..999L
    ) { "Release version exceeds Android versionCode limits (major<=2099, minor/patch<=999)" }
    val code = major * 1_000_000L + minor * 1_000L + patch
    require(code in 1L..2_100_000_000L) { "Android versionCode must be positive and <= 2100000000" }
    return code.toInt()
}

// Exercise actual Gradle version mapping, including negative fixtures. The release task
// uses the identical function, so these checks cannot accidentally diverge from packaging.
tasks.register("verifyReleaseVersionPolicy") {
    group = "verification"
    description = "Verify ordered Android APK versionCode mapping and reject invalid versions"
    // Evaluate the pure Gradle policy at configuration time, but capture only serializable
    // Booleans in the task action. Calling a script function from doLast captures the whole
    // Gradle script instance and breaks Gradle 9's configuration cache.
    val expected = mapOf(
        "1.0.0" to 1_000_000,
        "1.0.1" to 1_000_001,
        "1.1.0" to 1_001_000,
        "2.0.0" to 2_000_000,
        "2099.999.999" to 2_099_999_999
    )
    val positiveFixturesPass = expected.all { (version, code) ->
        bousygramVersionCode(version) == code
    }
    val negativeFixturesPass = listOf(
        "0.0.0", "1.0.1000", "1.1000.0", "2100.0.0",
        "v1.0.0", "01.0.0", "1.0.-1", "broken"
    ).all { version -> runCatching { bousygramVersionCode(version) }.isFailure }
    inputs.property("positiveFixturesPass", positiveFixturesPass)
    inputs.property("negativeFixturesPass", negativeFixturesPass)
    doLast {
        check(positiveFixturesPass) { "Valid release version mappings did not match expected Android codes" }
        check(negativeFixturesPass) { "Invalid release version was accepted by the Android version policy" }
    }
}

android {
    namespace = "com.vaydelux.bousygram"
    compileSdk {
        version = release(37)
    }

    val devAbi = project.findProperty("devAbi") as? String
    val releaseVersionName = providers.gradleProperty("releaseVersionName").orElse("1.0.0").get()
    // A versionName-only update is not installable over the previous Android APK unless
    // versionCode also increases. Encode semver into an ordered, Play-compatible integer.
    // v1.0.0 = 1_000_000, v1.0.1 = 1_000_001, v1.1.0 = 1_001_000.
    val releaseVersionCode = bousygramVersionCode(releaseVersionName)

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
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (!devAbi.isNullOrBlank()) {
            ndk {
                abiFilters += listOf(devAbi)
            }
        }

        manifestPlaceholders["engineLabEnabled"] = "false"
        buildConfigField("boolean", "LEGACY_STREAMING_FALLBACK_ENABLED", "true")
        // Private-media Cast uses cleartext LAN HTTP until receiver-trusted TLS is verified.
        // Keep it available for development only; production variants stay fail-closed.
        buildConfigField("boolean", "PRIVATE_MEDIA_CAST_ENABLED", "false")
        buildConfigField("boolean", "CRASH_REPORTING_ENABLED", "false")
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
            // Use the Android Gradle Plugin's generated debug keystore. A clean
            // checkout must not depend on a repository-root debug.keystore.
            buildConfigField("boolean", "PRIVATE_MEDIA_CAST_ENABLED", "true")
            manifestPlaceholders["engineLabEnabled"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "LEGACY_STREAMING_FALLBACK_ENABLED", "false")
            buildConfigField("boolean", "PRIVATE_MEDIA_CAST_ENABLED", "false")
            manifestPlaceholders["engineLabEnabled"] = "false"
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
            buildConfigField("boolean", "PRIVATE_MEDIA_CAST_ENABLED", "false")
            manifestPlaceholders["engineLabEnabled"] = "false"
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
                "Refusing to build a production artifact: Production signing is not configured. " +
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

tasks.matching {
    it.name.startsWith("packageRelease") || it.name.startsWith("packageHardenedRelease")
}.configureEach {
    dependsOn("verifyPrivateMediaCastReleaseGate")
    dependsOn("verifySensitiveLogRedaction")
}

tasks.register("verifyTdlibAbiCoverage") {
    group = "verification"
    description = "Verify TDLib ABI coverage, ELF identity, JNI markers, and page alignment"
    val nativeFiles = supportedTdLibAbis.associateWith { abi -> File(tdLibJniDir, "$abi/libtdjni.so") }
    val metadataFile = rootProject.file("tools/tdlib/BUILD_METADATA.txt")
    val resolvedCommitFile = rootProject.file("build-tools/tdlib/RESOLVED_COMMIT.txt")
    inputs.files(nativeFiles.values + listOf(metadataFile, resolvedCommitFile))
    doLast {
        val abiSpecs = mapOf(
            "arm64-v8a" to Triple(2, 183, 16_384L),
            "armeabi-v7a" to Triple(1, 40, 4_096L)
        )
        abiSpecs.forEach { (abi, spec) ->
            val nativeFile = nativeFiles.getValue(abi)
            require(nativeFile.isFile && nativeFile.length() >= 1_000_000L) {
                "Missing, empty, or implausibly small TDLib library: ${nativeFile.absolutePath}"
            }
            val elf = nativeFile.readBytes()
            require(elf.size >= 64 && elf.copyOfRange(0, 4).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) {
                "${nativeFile.absolutePath} is not an ELF binary (or is a Git LFS pointer)."
            }
            require((elf[4].toInt() and 0xff) == spec.first && (elf[5].toInt() and 0xff) == 1) {
                "${nativeFile.absolutePath} has the wrong ELF class or is not little-endian."
            }
            val header = ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN)
            require((header.getShort(18).toInt() and 0xffff) == spec.second) {
                "${nativeFile.absolutePath} has an unexpected ELF machine type."
            }
            val is64Bit = spec.first == 2
            val programOffset = if (is64Bit) header.getLong(32) else header.getInt(28).toLong() and 0xffff_ffffL
            val programEntrySize = header.getShort(if (is64Bit) 54 else 42).toInt() and 0xffff
            val programCount = header.getShort(if (is64Bit) 56 else 44).toInt() and 0xffff
            val minimumEntrySize = if (is64Bit) 56 else 32
            require(programOffset >= 0L && programEntrySize >= minimumEntrySize && programCount > 0) {
                "${nativeFile.absolutePath} has an invalid ELF program-header table."
            }
            val loadAlignments = mutableListOf<Long>()
            repeat(programCount) { index ->
                val position = programOffset + index.toLong() * programEntrySize
                require(position >= 0L && position + programEntrySize <= elf.size) {
                    "${nativeFile.absolutePath} has a truncated ELF program-header table."
                }
                val offset = position.toInt()
                if (header.getInt(offset) == 1) { // PT_LOAD
                    val alignment = if (is64Bit) header.getLong(offset + 48) else header.getInt(offset + 28).toLong() and 0xffff_ffffL
                    loadAlignments += alignment
                }
            }
            require(loadAlignments.isNotEmpty() && loadAlignments.all { it >= spec.third }) {
                "${nativeFile.absolutePath} PT_LOAD segments do not meet ${spec.third}-byte alignment: $loadAlignments"
            }
            val strings = String(elf, Charsets.ISO_8859_1)
            val requiredJniMarkers = listOf(
                "JNI_OnLoad",
                "createNativeClient",
                "nativeClientSend",
                "nativeClientReceive",
                "nativeClientExecute",
                "nativeClientSetLogMessageHandler"
            )
            require(requiredJniMarkers.all { marker -> strings.contains(marker) }) {
                "${nativeFile.absolutePath} is missing one or more JNI markers for org.drinkless.tdlib.Client."
            }
            logger.lifecycle("Verified $abi ELF machine=${spec.second}, PT_LOAD alignments=$loadAlignments, JNI markers present")
        }

        require(metadataFile.isFile && resolvedCommitFile.isFile) {
            "TDLib build metadata and resolved source commit record are required."
        }
        val metadata = metadataFile.readText()
        val resolvedCommit = Regex("[0-9a-fA-F]{40}").find(resolvedCommitFile.readText())?.value
        require(resolvedCommit != null && resolvedCommit in metadata) {
            "TDLib source commit metadata does not match ${resolvedCommitFile.path}."
        }
    }
}

tasks.register("verifyTdlibBindingCompatibility") {
    group = "verification"
    description = "Verify TdApi.java and every TDLib JNI binary come from one pinned revision"
    dependsOn("verifyTdlibAbiCoverage")
    dependsOn("testTdlibBindingCompatibilityFixtures")
    val tdApiFile = file("src/main/java/org/drinkless/tdlib/TdApi.java")
    val metadataFile = rootProject.file("tools/tdlib/BUILD_METADATA.txt")
    val resolvedCommitFile = rootProject.file("build-tools/tdlib/RESOLVED_COMMIT.txt")
    val verificationFile = rootProject.file("tools/tdlib/BUILD_VERIFICATION.txt")
    val nativeFiles = supportedTdLibAbis.associateWith { abi -> File(tdLibJniDir, "$abi/libtdjni.so") }
    inputs.files(tdApiFile, metadataFile, resolvedCommitFile, nativeFiles.values)
    outputs.file(verificationFile)
    doLast {
        val commitPattern = Regex("[0-9a-fA-F]{40}")
        val javaCommit = Regex("GIT_COMMIT_HASH\\s*=\\s*\\\"([0-9a-fA-F]{40})\\\"")
            .find(tdApiFile.readText())?.groupValues?.get(1)?.lowercase()
            ?: throw GradleException("TdApi.java does not contain a readable GIT_COMMIT_HASH.")
        val resolvedCommit = commitPattern.find(resolvedCommitFile.readText())?.value?.lowercase()
            ?: throw GradleException("RESOLVED_COMMIT.txt does not contain a 40-character commit.")
        require(javaCommit == resolvedCommit) {
            "TDLib Java/native mismatch: TdApi.java=$javaCommit, pinned native source=$resolvedCommit."
        }
        require("TDLib commit: $resolvedCommit" in metadataFile.readText()) {
            "BUILD_METADATA.txt does not prove the pinned TDLib commit $resolvedCommit."
        }
        val provenance = metadataFile.readText()
        val expectedHashes = Regex("(?m)^SHA256\\s+(\\S+)\\s+([0-9a-fA-F]{64})\\s*$")
            .findAll(provenance).associate { it.groupValues[1] to it.groupValues[2].lowercase() }
        val javaHash = MessageDigest.getInstance("SHA-256").digest(tdApiFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        require(expectedHashes["TdApi.java"] == javaHash) {
            "TdApi.java does not match the complete generated API hash in BUILD_METADATA.txt. Regenerate from the pinned source."
        }
        val report = mutableListOf("TDLib commit: $resolvedCommit", "Java commit: $javaCommit")
        report += "SHA256 TdApi.java $javaHash"
        nativeFiles.forEach { (abi, nativeFile) ->
            require(nativeFile.isFile && nativeFile.length() > 1_000_000L) {
                "Missing or implausibly small TDLib JNI library for $abi: ${nativeFile.absolutePath}"
            }
            val bytes = nativeFile.readBytes()
            val binaryText = String(bytes, Charsets.ISO_8859_1)
            require(binaryText.contains(resolvedCommit)) {
                "$abi libtdjni.so does not contain the pinned TDLib revision $resolvedCommit."
            }
            require(binaryText.contains("JNI_OnLoad")) { "$abi libtdjni.so is missing JNI_OnLoad." }
            listOf("createNativeClient", "nativeClientSend", "nativeClientReceive", "nativeClientExecute", "nativeClientSetLogMessageHandler")
                .forEach { symbol -> require(binaryText.contains(symbol)) { "$abi libtdjni.so is missing JNI marker $symbol." } }
            val requiredDependencies = listOf("libc.so", "libdl.so", "liblog.so", "libm.so", "libz.so")
            requiredDependencies.forEach { dependency ->
                require(binaryText.contains(dependency)) {
                    "$abi libtdjni.so does not advertise required ELF dependency $dependency."
                }
            }
            val elfClass = bytes[4].toInt() and 0xff
            val machine = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(18).toInt() and 0xffff
            val expectedClass = if (abi == "arm64-v8a") 2 else 1
            val expectedMachine = if (abi == "arm64-v8a") 183 else 40
            require(elfClass == expectedClass && machine == expectedMachine) {
                "$abi libtdjni.so has ELF class=$elfClass machine=$machine; expected class=$expectedClass machine=$expectedMachine."
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(expectedHashes[abi] == hash) {
                "$abi libtdjni.so does not match the approved SHA-256 in BUILD_METADATA.txt."
            }
            report += "SHA256 $abi $hash"
            report += "ELF $abi class=$elfClass machine=$machine size=${bytes.size}"
            report += "JNI $abi markers=verified revision=embedded dependencies=${requiredDependencies.joinToString(",")}"
        }
        verificationFile.writeText(report.joinToString("\n") + "\n")
        logger.lifecycle(report.joinToString("; "))
    }
}

tasks.register("testTdlibBindingCompatibilityFixtures") {
    group = "verification"
    description = "Exercise negative TDLib Java/JNI compatibility fixtures"
    doLast {
        val commit = "4c6377aa7386c39217878a9a56d581922ad37ca4"
        fun validate(javaCommit: String, nativeCommit: String?, abi: String, elfClass: Int?, machine: Int?, markers: Set<String>, provenance: Boolean) {
            require(provenance) { "invalid provenance" }
            require(javaCommit == nativeCommit) { "revision mismatch" }
            require(nativeCommit == commit) { "native revision mismatch" }
            val expectedClass = if (abi == "arm64-v8a") 2 else 1
            val expectedMachine = if (abi == "arm64-v8a") 183 else 40
            require(elfClass == expectedClass && machine == expectedMachine) { "ELF architecture mismatch" }
            listOf("JNI_OnLoad", "createNativeClient", "nativeClientSend", "nativeClientReceive", "nativeClientExecute", "nativeClientSetLogMessageHandler")
                .forEach { require(it in markers) { "missing JNI marker $it" } }
        }
        fun fails(name: String, block: () -> Unit) {
            runCatching { block() }.onSuccess { throw GradleException("Fixture '$name' unexpectedly passed") }
        }
        validate(commit, commit, "arm64-v8a", 2, 183, setOf("JNI_OnLoad", "createNativeClient", "nativeClientSend", "nativeClientReceive", "nativeClientExecute", "nativeClientSetLogMessageHandler"), true)
        fails("incorrect Java commit") { validate("cb863c1600082404428f1a84e407b866b9d412a8", commit, "arm64-v8a", 2, 183, emptySet(), true) }
        fails("incorrect ARM32 binary") { validate(commit, "cb863c1600082404428f1a84e407b866b9d412a8", "armeabi-v7a", 1, 40, emptySet(), true) }
        fails("incorrect ARM64 binary") { validate(commit, "cb863c1600082404428f1a84e407b866b9d412a8", "arm64-v8a", 2, 183, emptySet(), true) }
        fails("missing native binary") { validate(commit, null, "arm64-v8a", null, null, emptySet(), true) }
        fails("wrong ELF architecture") { validate(commit, commit, "arm64-v8a", 1, 40, emptySet(), true) }
        fails("missing JNI markers") { validate(commit, commit, "arm64-v8a", 2, 183, emptySet(), true) }
        fails("invalid provenance") { validate(commit, commit, "arm64-v8a", 2, 183, emptySet(), false) }
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

tasks.register("verifyPrivateMediaCastReleaseGate") {
    group = "verification"
    description = "Verify private-media Cast and diagnostic entry points remain disabled in production"
    dependsOn(
        "generateDebugBuildConfig",
        "generateReleaseBuildConfig",
        "generateHardenedReleaseBuildConfig",
        "processReleaseMainManifest",
        "processHardenedReleaseMainManifest"
    )

    val releaseConfig = layout.buildDirectory.file(
        "generated/source/buildConfig/release/com/vaydelux/bousygram/BuildConfig.java"
    ).get().asFile
    val debugConfig = layout.buildDirectory.file(
        "generated/source/buildConfig/debug/com/vaydelux/bousygram/BuildConfig.java"
    ).get().asFile
    val hardenedConfig = layout.buildDirectory.file(
        "generated/source/buildConfig/hardenedRelease/com/vaydelux/bousygram/BuildConfig.java"
    ).get().asFile
    val releaseManifest = layout.buildDirectory.file(
        "intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml"
    ).get().asFile
    val hardenedManifest = layout.buildDirectory.file(
        "intermediates/merged_manifest/hardenedRelease/processHardenedReleaseMainManifest/AndroidManifest.xml"
    ).get().asFile
    val castServer = file("src/main/java/com/vaydelux/bousygram/core/cast/CastLanStreamingServer.kt")
    val castController = file("src/main/java/com/vaydelux/bousygram/core/cast/CastSessionController.kt")
    val castService = file("src/main/java/com/vaydelux/bousygram/core/cast/CastStreamingService.kt")
    val playerScreen = file("src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt")
    val settingsScreen = file("src/main/java/com/vaydelux/bousygram/presentation/settings/SettingsScreen.kt")
    val engineLabActivity = file("src/main/java/com/vaydelux/bousygram/presentation/debug/EngineLabActivity.kt")
    inputs.files(castServer, castController, castService, playerScreen, settingsScreen, engineLabActivity)
    outputs.upToDateWhen { false }

    doLast {
        listOf(releaseConfig, hardenedConfig).forEach { config ->
            require(config.isFile && "PRIVATE_MEDIA_CAST_ENABLED = false" in config.readText()) {
                "Private-media Cast must remain disabled in ${config.absolutePath}."
            }
        }
        listOf(debugConfig, releaseConfig, hardenedConfig).forEach { config ->
            require(config.isFile && "CRASH_REPORTING_ENABLED = false" in config.readText()) {
                "External crash reporting must remain disabled in ${config.absolutePath}."
            }
        }
        listOf(releaseManifest, hardenedManifest).forEach { manifestFile ->
            val manifest = manifestFile.readText()
            require(
                Regex(
                    """<activity\b(?=[^>]*android:name="com\.vaydelux\.bousygram\.presentation\.debug\.EngineLabActivity")(?=[^>]*android:enabled="false")[^>]*>"""
                ).containsMatchIn(manifest)
            ) {
                "EngineLabActivity must be disabled in ${manifestFile.absolutePath}."
            }
        }
        require("if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED) return null" in castServer.readText()) {
            "The LAN media server must reject release Cast requests."
        }
        require("if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED)" in castController.readText()) {
            "The Cast session controller must reject release Cast requests."
        }
        require(
            "!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED ||" in castService.readText() &&
                "if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED) return" in castService.readText()
        ) {
            "The Cast foreground service must reject release Cast starts."
        }
        require("BuildConfig.PRIVATE_MEDIA_CAST_ENABLED" in playerScreen.readText()) {
            "The player UI must enforce the private-media Cast build gate."
        }
        require("if (BuildConfig.DEBUG && debugHudVisible)" in playerScreen.readText()) {
            "The developer video HUD must be inaccessible in production."
        }
        require(
            Regex("""if \(BuildConfig\.DEBUG\) \{\s*// Developer Tools Section""")
                .containsMatchIn(settingsScreen.readText()) &&
                "if (!BuildConfig.DEBUG)" in engineLabActivity.readText()
        ) {
            "Developer settings and Engine Lab entry must require the debug build flag."
        }
    }
}

tasks.register("verifySensitiveLogRedaction") {
    group = "verification"
    description = "Reject logging of URLs and bearer-bearing stream values"
    val sources = listOf(
        file("src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt"),
        file("src/main/java/com/vaydelux/bousygram/core/media/MpvPlayerEngine.kt"),
        file("src/main/java/com/vaydelux/bousygram/core/tdlib/utils/ThumbnailExtractor.kt"),
        file("src/main/java/com/vaydelux/bousygram/data/repository/TmdbRepository.kt"),
        file("src/main/java/com/vaydelux/bousygram/core/observability/CrashReporter.kt"),
        file("src/main/java/com/vaydelux/bousygram/core/observability/StructuredPlaybackLogger.kt"),
        file("src/main/java/com/vaydelux/bousygram/core/tdlib/server/TelegramLogWrapper.kt"),
        file("src/main/java/com/vaydelux/bousygram/core/tdlib/utils/QrCodeGenerator.kt"),
        file("src/main/java/com/vaydelux/bousygram/core/cast/CastReceiverManager.kt"),
        file("src/main/java/com/vaydelux/bousygram/core/tdlib/prefetch/FocusPrefetchManager.kt"),
        file("src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesSubtitle.kt")
    )
    inputs.files(sources)
    doLast {
        val sensitiveNames = listOf(
            "url", "newUrl", "hotSwappedUrl", "streamUrl", "imageUrl", "urlString",
            "text", "prefix", "fileName", "title", "payload.title", "item.title"
        )
        val logCall = Regex("""Log\.(?:v|d|i|w|e)\s*\([^)]*\)""", RegexOption.DOT_MATCHES_ALL)
        sources.forEach { source ->
            logCall.findAll(source.readText()).forEach { match ->
                require(sensitiveNames.none { name -> "\$$name" in match.value || "\${$name}" in match.value }) {
                    "Potentially sensitive authentication or media value logged in ${source.path}."
                }
            }
        }
        require("if (!BuildConfig.CRASH_REPORTING_ENABLED) return false" in sources
            .first { it.name == "CrashReporter.kt" }.readText()) {
            "External crash reporting must require the explicit build flag."
        }
    }
}

tasks.matching {
    it.name in listOf(
        "preDebugBuild",
        "preReleaseBuild",
        "preHardenedReleaseBuild",
        "assembleRelease",
        "assembleHardenedRelease",
        "bundleRelease",
        "bundleHardenedRelease"
    )
}.configureEach {
    dependsOn("verifyTdlibAbiCoverage")
    dependsOn("testTdlibBindingCompatibilityFixtures")
    dependsOn("verifyTdlibBindingCompatibility")
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
    implementation(libs.google.play.services.cast.framework)

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
