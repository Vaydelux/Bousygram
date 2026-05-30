# Bousygram

Bousygram is a premium Android TV application for browsing and streaming videos, photos, and other media from Telegram chats on a TV. It is built with **Jetpack Compose for TV**, **Media3 ExoPlayer**, TDLib, and an optimized on-device loopback streaming server.

The app is **standalone and serverless**. It connects directly to Telegram from the TV client using TDLib (`libtdjni.so`), keeping media access on-device without a backend proxy.

---

## Features

- **Jetpack Compose for TV**: Focus-aware Android TV interface built for D-pad navigation.
- **Playback Stability**: Media3 ExoPlayer integration with session-aware Telegram cache-ahead, retry recovery, and diagnostics.
- **Safe Cache Behavior**: Active cache/download work stops when playback exits or the app backgrounds, while partial cache is kept for faster resume.
- **Secure On-Device Sign-in**: Phone/code/2FA authentication and QR login support.
- **No Middlemen**: Direct MTProto connection between the TV client and Telegram.

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
sdk.dir=C\:\\Users\\Jericho.Mosqueda\\AppData\\commandlinetools-win-14742923_latest\\cmdline-tools4
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

If signing values are missing, local release builds fall back to debug signing for validation only.

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
   - Settings storage manager reports cache size and prunes safely
7. Record known issues and APK path before release.

---

## How It Is Built

- **Jetpack Compose for TV** for the Android TV interface.
- **Kotlin Coroutines and Flow** for asynchronous state.
- **Media3 ExoPlayer** for media playback.
- **TDLib** through native JNI for direct Telegram connectivity.
- **Loopback Streaming** for local HTTP range reads from Telegram media.

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

Developed and published by **Vaydelux**.
