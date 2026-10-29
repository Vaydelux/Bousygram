# Android emulator smoke test

The Android Emulator Cast Smoke action runs **real Android instrumentation tests**. The routine assembleDebugAndroidTest task only builds the test APK.

The workflow boots an API 35 Google APIs x86_64 emulator and executes CastRouteButtonThemeInstrumentedTest (regression for the reported translucent-background crash) and CastReceiverIntentInstrumentedTest. Results are uploaded for review.

This is a focused emulator test, **not production device qualification**. The x86_64 emulator has no ARM TDLib JNI, so it cannot prove authenticated Telegram streaming, mpv decoder performance, Chromecast receiver discovery, or Android TV D-pad behavior. Protected production device evidence still requires tests on actual ARM32 and ARM64 devices.

Trigger: PRs touching Cast, player UI, Android instrumentation or theme XML, plus manual workflow dispatch.

Real playback acceptance: QR/OTP login, browsing, play/pause, seeks, manual and automatic fallback, next/previous, background/resume, logout during active streaming and another-account login. Record crash logs, startup and seek latency, rebuffer ratio and isolation. Never report these as passed without measured device evidence.

## Expanded UI navigation coverage

The same emulator run also executes `MainActivitySmokeTest`, `AndroidTvNavigationSmokeTest`, and `DashboardPreviewNavigationTest`. These open the real app login and debug-only empty dashboard preview and send D-pad, select, Back and media keys. Startup checks do not use a fake Telegram login or force-stop the running instrumentation process.

The D-pad interactions on a simulated phone **do not certify Android TV focus navigation**. The Android TV hardware profile and actual Telegram session still require device acceptance.
