# Bousygram

Bousygram is a premium, high-performance Android TV application designed to let you browse and stream videos, photos, and other media from your Telegram chats right on your TV. Built from the ground up using **Jetpack Compose for TV** and utilizing an optimized on-device local streaming server, Bousygram delivers a stunning, lag-free movie-theater experience.

This app is **100% standalone and serverless**—it connects directly to Telegram from your TV client using TDLib (`libtdjni.so`), ensuring absolute privacy and data security.

---

## Features

- **Jetpack Compose for TV**: A beautiful, focus-aware Netflix-style user interface.
- **Premium Playback & Buffering**: Custom Media3 ExoPlayer integration with local prefetch buffers to completely eliminate streaming lag.
- **D-pad Native Interceptors**: Seamless TV remote navigation (fast seeking, playback speed control, visual playlist browsing).
- **Secure On-Device Sign-in**: Standard Phone/Code/2FA authentication and instant login via QR Code scan.
- **No Middlemen**: Direct secure MTProto connection between your TV and Telegram cloud servers.

---

## Getting Your API Credentials

Before you can use Bousygram, you need your own Telegram API ID and Hash. This is how Telegram verifies the application connecting on your behalf:

1. Go to [https://my.telegram.org/apps](https://my.telegram.org/apps)
2. Log in with your Telegram phone number.
3. Click on **API development tools**.
4. Fill in the form:
   - **App title**: Bousygram
   - **Short name**: bousygram
   - **Platform**: Android
   - **Description**: Premium Android TV client for Telegram
5. Click **Create application**.
6. Save your **api_id** and **api_hash** somewhere secure. Keep them private!

---

## Installation

### Option 1: USB or File Manager
1. Download the latest release APK (`Bousygram.apk`).
2. Copy the APK file to a USB flash drive.
3. Insert the USB drive into your Android TV device.
4. Use a file manager app on your TV to locate and install the APK.

### Option 2: Android Debug Bridge (ADB)
1. Enable Developer Options and Network Debugging on your Android TV.
2. Find your TV's IP address (Settings > Network).
3. Connect and install using adb:
   ```bash
   adb connect <your-tv-ip>
   adb install Bousygram.apk
   ```

---

## Initial Setup

1. **Enter API Credentials**: Input the API ID and API Hash you generated from Telegram.
2. **Sign In**: Standard phone verification or scan the generated secure QR code using your phone's Telegram scanner (Settings > Devices > Link Desktop Device).
3. **Enjoy Streaming**: Browse your chats, groups, channels, or Saved Messages, select any video, and start streaming instantly!

---

## How It's Built

- **Jetpack Compose for TV** for a modern, fluid material interface.
- **Kotlin Coroutines & Flow** for asynchronous, reactive data streams.
- **Media3 ExoPlayer** for low-latency native media streaming.
- **TDLib (Telegram Database Library)** compiled native JNI for direct on-device protocol support.
- **Optimized Loopback Streaming**: Customized localized HTTP chunk pre-buffering to bypass standard streaming bottlenecks.

---

## Developer Guide: Building from Source

To build and compile the APK yourself:

1. Clone this repository:
   ```bash
   git clone https://github.com/vaydelux/Bousygram.git
   cd Bousygram
   ```
2. Create a `local.properties` file in the root directory:
   ```properties
   TELEGRAM_API_ID=your_api_id_here
   TELEGRAM_API_HASH=your_api_hash_here
   ```
3. Build the release APK using the Gradle wrapper:
   ```bash
   ./gradlew assembleRelease
   ```

---

## License

This project is licensed under the MIT License. See the [LICENSE](file:///C:/Users/CLIENT/Downloads/Tele-main/Tele-main/LICENSE) file for details.

Developed and published by **Vaydelux**.
