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