# Bousygram

Bousygram is a video-first Android TV application for browsing and streaming videos from Telegram chats and Saved Messages. It is built with **Jetpack Compose**, **Media3 ExoPlayer**, TDLib, and a loopback streaming server.

Normal local playback connects directly to Telegram on the device through TDLib (`libtdjni.so`) and does not require a Bousygram backend.

> **Release status:** source-level checks and local Android builds are automated, but real Telegram playback, physical 32-bit/64-bit TV validation, and production-certificate verification are still required before distribution. See `RELEASE_STATUS.md`.

---

## Features

- **Jetpack Compose for TV**: Focus-aware Android TV interface built for D-pad navigation.
- **Playback Stability**: Media3 ExoPlayer integration with session-aware Telegram cache-ahead, retry recovery, and diagnostics.
- **Safe Cache Behavior**: Active cache/download work stops when playback exits or the app backgrounds, while partial cache is kept for faster resume.
- **Secure On-Device Sign-in**: Phone/code/2FA authentication and QR login support.
- **Direct local mode**: Direct MTProto connection between the TV client and Telegram for normal local streaming.
- **Library navigation**: Dedicated Home, Library, Favorites, Search, and Settings destinations, with return navigation preserved when moving between them.
- **Protected local stream URLs**: Short-lived, unpredictable URL tokens scoped to a specific TDLib file or generated playlist.
- **Protected credential storage**: Telegram API credentials are encrypted using an AES-GCM key held by Android Keystore; new TDLib databases receive a persisted encryption key when the installation is initialized fresh.

---

## Getting Telegram API Credentials

Before using Bousygram, create your own Telegram API ID and Hash:

1. Go to [https://my.telegram.org/apps](https://my.telegram.org/apps).
2. Log in with your Telegram phone number.
3. Open **API development tools**.
4. Create an Android app:
   - **App title**: Bousygram
   - **Short name**: bousygram
   - **Platform**: Android
   - **Description**: Android TV client for Telegram media
5. Save your **api_id** and **api_hash** somewhere secure.

---

## Installation

### Option 1: USB or File Manager

1. Download the latest release APK.
2. Copy the APK file to a USB flash drive.
3. Insert the USB drive into your Android TV device.
4. Use a file manager app on the TV to locate and install the APK.

### Option 2: Android Debug Bridge

1. Enable Developer Options and Network Debugging on the Android TV device.
2. Find the TV IP address in Settings > Network.
3. Connect and install with ADB:
   ```bash
   adb connect <your-tv-ip>
   adb install app-debug.apk
   ```

---

## Initial Setup

1. Enter the Telegram API ID and API Hash.
2. Sign in with phone verification or scan the QR code using Telegram on your phone.
3. Browse chats, channels, groups, or Saved Messages and start playback.

---

## Developer Guide

### Local Properties

Create `local.properties` in the repository root. This file is ignored by Git and must not be committed.

```properties
sdk.dir=C\:\path\to\Android\Sdk
TELEGRAM_API_ID=your_api_id_here
TELEGRAM_API_HASH=your_api_hash_here
```

Adjust `sdk.dir` for your own machine if needed.

Optional release signing can be supplied from `local.properties` or environment variables:

```properties
RELEASE_STORE_FILE=C\:\\path\\to\\release.keystore
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

Release and hardened-release tasks fail closed when those signing values are missing; they never fall back to the debug certificate. The CI hardened-release job additionally requires `RELEASE_CERT_SHA256` and verifies every generated split/universal APK against that certificate. Configure GitHub Actions secrets `RELEASE_KEYSTORE_BASE64` (base64-encoded JKS/PKCS12), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, and `RELEASE_CERT_SHA256` (SHA-256 certificate digest; colons/spaces are optional).

For the protected `production-signing` GitHub environment, certificate verification, device-qualification prerequisites, and how to resolve a missing-signing-secrets failure, see [RELEASE_SIGNING_SETUP.md](RELEASE_SIGNING_SETUP.md).

See `tools/mock-tests/README.md` for standalone Kotlin checks. Passing these tests does **not** replace the Android Gradle build or device playback tests.

Firebase Test Lab smoke tests run when both `FIREBASE_SERVICE_ACCOUNT` and `FIREBASE_PROJECT_ID` Actions secrets are available. Secret-less and fork pull requests safely skip the protected device and release jobs.

### Build Commands

```powershell
.\gradlew.bat :app:verifyTdlibAbiCoverage
.\gradlew.bat :app:verifyTvHardeningContract
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Production-signed hardened release build:

```powershell
.\gradlew.bat :app:assembleHardenedRelease
```

APK files are named with the app version, variant, and ABI split, for example `Bousygram-v1.0-release-arm64-v8a.apk`.

---

## Release Checklist

Before sharing an APK:

1. Run `.\gradlew.bat :app:verifyTdlibAbiCoverage :app:verifyTvHardeningContract`.
2. Run `.\gradlew.bat :app:compileDebugKotlin`.
3. Run `.\gradlew.bat :app:testDebugUnitTest`.
4. Run `.\gradlew.bat :app:assembleDebug`.
5. Confirm `CHANGELOG.md` has notes for the version being shared.
6. Install on an Android TV device and verify:
   - cold play of a large Telegram video
   - repeated seek forward/back
   - Back button stops playback cache work
   - app backgrounding stops active cache work
   - reopening the same video resumes from partial cache
   - Settings diagnostics copy/export works
   - Settings storage manager reports cache size and preserves pinned/active media during safe tracked-file pruning
7. Record known issues and APK path before release.

---

## How It Is Built

- **Jetpack Compose for TV** for the Android TV interface.
- **Kotlin Coroutines and Flow** for asynchronous state.
- **Media3 ExoPlayer** for media playback.
- **TDLib** through native JNI for direct Telegram connectivity.
- **Loopback Streaming** for local HTTP range reads from Telegram media, with strict single-range parsing and per-resource capability tokens.
- **Keystore-backed protection** for API credentials and encryption keys for newly created TDLib databases. Existing unencrypted TDLib databases deliberately keep their prior empty-key configuration during upgrade so the app does not accidentally lock existing users out; migrate such databases only through a separate tested migration procedure.

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

Developed and published by **Vaydelux**.

## Latest media-loading performance revision

The video grid now prioritizes TDLib media metadata: video-oriented searches run concurrently and publish partial results as they arrive. Thumbnail downloads are low-priority and bounded, and moving focus across cards no longer downloads video bytes. Library and compact grids fetch more metadata when the user nears the end. Selecting a video still uses the existing stream-preparation and ExoPlayer path.

See [`MEDIA_LOADING_OPTIMIZATION.md`](MEDIA_LOADING_OPTIMIZATION.md) for the exact behavior, tests, and remaining device-verification steps. A full Android Gradle build and real Telegram playback test are still required before release.

## Latest source-picker revision

See [SOURCE_PICKER_REVISION.md](SOURCE_PICKER_REVISION.md) for the all-chat source picker, video-only browsing behavior, format-recognition additions, and the current verification limits.
