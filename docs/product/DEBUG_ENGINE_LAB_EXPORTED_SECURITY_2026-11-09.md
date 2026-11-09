# Debug Engine Lab exported-component hardening (2026-11-09)

The Android manifest previously exposed the debug-only
`EngineLabActivity` as `android:exported="true"` without a caller permission.
Production variants set `engineLabEnabled=false` and the activity checks
`BuildConfig.DEBUG`, but distributed debug APKs still allowed unrelated
third-party apps to launch the native diagnostic surface/decoder UI.

## Fix

- Require `android:permission="android.permission.DUMP"` on the exported
  Engine Lab activity. The Android shell declares this privileged permission,
  enabling authorized ADB diagnostics, while ordinary third-party apps
  cannot acquire the signature/privileged/development capability.
- Keep the debug-only `engineLabEnabled` manifest placeholder and runtime
  `BuildConfig.DEBUG` check. Production/release variants remain disabled.
- Make `:app:verifyPrivateMediaCastReleaseGate` depend on the merged debug
  manifest as well as release/hardened manifests, and assert all variants
  require DUMP. Existing release/hardened disabled assertions remain.
- No changes to media network URLs, TDLib authorization, Cast controls,
  memory cleanup or player selection.

## Verification

Run exact-head Android Enterprise Kotlin/JVM/lint/debug APK ABI matrix,
Android emulator media/Cast smoke, and TDLib Java generation; inspect
merged debug/release/hardened manifest output. On physical debug builds
verify the normal in-app developer entry works, an authorized adb shell can
open Engine Lab, and a separate unprivileged app cannot.

A production APK is **not** approved merely because the debug-only activity
is now protected.
