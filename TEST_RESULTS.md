# Test Results

Last updated: 2026-10-27.

## Completed during hardening

- Targeted scheduler, HTTP range, DataSource, telemetry, and focus-prefetch suite: 48 tests passed.
- Buffer-profile, voice-language, and scheduler suite passed after adding high-memory and lease coverage.
- Complete unit suite: 246 tests, 0 failures, 0 errors, 0 skipped; final run completed in 3m21s.
- Strict debug lint: passed without a baseline in 7m16s.
- TDLib ABI and TV hardening gates: passed for `arm64-v8a` and `armeabi-v7a`.
- Final lint, ABI, TV-hardening, debug, and signed/R8 release gate: passed in 20m31s.
- Direct `apksigner verify` passed for all three release APKs (APK Signature Scheme v2). Artifact inspection confirmed the matching `libtdjni.so` in each split and both libraries in the universal APK.

## APK artifacts

| Variant | ABI | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Debug | arm64-v8a | 71,961,981 | `CFE012F0011EDF314AFCD46B90026DA0AF3719AB577E004C849582A1FB94651D` |
| Debug | armeabi-v7a | 63,903,949 | `C930D60C393831B27994749ECE3F401B620EEEAFC37BEAAC3C8D518365997FC1` |
| Debug | universal | 168,310,664 | `A3144BDB150F7FEC5F76F44D09712A6F588A0754B4CF05637B1662F2661E6963` |
| Release | arm64-v8a | 54,677,978 | `A2A5F1C00256DBA0F76FE3FDB83B03C297C185EDD1354CF3210BACB7B1EDB5FB` |
| Release | armeabi-v7a | 46,619,946 | `F5A2BD78AB2074FFB103FB6D40A397322B5C6C97AE8DD6A0B6B00C04ED6947AF` |
| Release | universal | 151,026,661 | `B1E4056D3C935D4990665ABB6C5A3E0A714608B204BEC2DD5D49302DE9975748` |

## Device limitations

ADB is not installed on the build host and no physical TV or emulator is connected. Instrumented UI, real decoder compatibility, microphone behavior, memory pressure, and 32/64-bit playback performance are therefore pending rather than reported as passing.
