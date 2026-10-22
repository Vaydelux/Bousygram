# Bousygram

Bousygram is a video-first Android TV application for browsing and streaming videos from Telegram chats and Saved Messages. It is built with **Jetpack Compose**, **Media3 ExoPlayer**, TDLib, and a loopback streaming server.

Normal local playback connects directly to Telegram on the device through TDLib (`libtdjni.so`) and does not require a Bousygram backend. An optional **Fast-Link** mode can forward the selected Telegram message to a user-configured bot/service to obtain an HTTPS link; this is a separate media-delivery path and should be enabled only when the user trusts that bot/service. Link revocation is best-effort and cannot guarantee deletion of every remote copy.

> **Release status:** this repository archive is a source and mock-tested candidate. It does not contain a compiled APK. The Fast-Link and pure Kotlin mock tests are passing, but an Android Gradle build, instrumentation run, real Telegram playback, release signing, and device checks are still required before distribution. See `MOCK_TEST_REPORT.md`.

---

## Features

- **Jetpack Compose for TV**: Focus-aware Android TV interface built for D-pad navigation.
- **Playback Stability**: Media3 ExoPlayer integration with session-aware Telegram cache-ahead, retry recovery, and diagnostics.
- **Safe Cache Behavior**: Active cache/download work stops when playback exits or the app backgrounds, while partial cache is kept for faster resume.
- **Secure On-Device Sign-in**: Phone/code/2FA authentication and QR login support.
- **Direct local mode**: Direct MTProto connection between the TV client and Telegram for normal local streaming.
- **Library navigation**: Dedicated Home, Library, Favorites, Search, and Settings destinations, with return navigation preserved when moving between them.
- **Protected local stream URLs**: Short-lived, unpredictable URL tokens scoped to a specific TDLib file or generated playlist.
- **Fast-Link server safety**: The optional proxy requires trusted Telegram user IDs, a stable signing secret, and HTTPS outside localhost; it validates HTTP ranges and bounds upstream requests.
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

If signing values are missing, local release builds may fall back to debug signing for validation only. Do not distribute such an APK as a production release. The CI hardened-release workflow now requires the release keystore and credentials, plus `RELEASE_CERT_SHA256`, and fails if the built APK is not signed by that exact certificate. Configure GitHub Actions secrets `RELEASE_KEYSTORE_BASE64` (base64-encoded JKS/PKCS12), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, and `RELEASE_CERT_SHA256` (SHA-256 certificate digest; colons/spaces are optional).

### Offline/mock checks

The repository includes mock tests that do not sign in to Telegram or contact a real bot:

```powershell
python .\server\fastlink-proxy\run_mock_tests.py
```

The proxy route tests use a mocked upstream CDN, and the suite covers range parsing and expiring/scoped token behavior. See `tools/mock-tests/README.md` for standalone Kotlin checks. Passing these tests does **not** replace the Android Gradle build or device playback tests.

Optional Firebase Test Lab jobs are disabled unless the repository variable `FIREBASE_TEST_LAB_ENABLED` is set to `true`. When enabled, configure the `FIREBASE_SERVICE_ACCOUNT` and `FIREBASE_PROJECT_ID` Actions secrets. The CI job is restricted to pushes because pull requests from forks do not receive repository secrets.

### Build Commands

```powershell
.\gradlew.bat :app:verifyTdlibAbiCoverage
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Release variants:

```powershell
.\gradlew.bat :app:assembleStaging
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:assembleHardenedRelease
```

APK files are named with the app version, for example `Bousygram-v1.0-debug.apk`.

---

## Release Checklist

Before sharing an APK:

1. Run `.\gradlew.bat :app:verifyTdlibAbiCoverage`.
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
