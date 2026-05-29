# TDLib Native Build (Phase 2)

This project currently needs `libtdjni.so` for each target ABI it ships.

At runtime, Android loads native libs based on the process ABI. If `libtdjni.so` is missing for that ABI, startup fails with:

- `java.lang.UnsatisfiedLinkError: dlopen failed: library "libtdjni.so" not found`

## Current state

- Present: `app/src/main/jniLibs/armeabi-v7a/libtdjni.so`
- Missing long-term target: `arm64-v8a`

## Long-term target profile

- First-class: `arm64-v8a` (phone + tablet + Android TV)
- Optional later: `x86_64` (emulator/ChromeOS workflows)
- Legacy fallback: `armeabi-v7a`

## Build arm64 TDLib from source (Windows PowerShell)

From repository root:

```powershell
.\tools\tdlib\build_tdlib_android_arm64.ps1 -AndroidNdk "C:\Users\<you>\AppData\Local\Android\Sdk\ndk\<version>"
```

If `ANDROID_NDK_HOME` is set, `-AndroidNdk` can be omitted.

The script:

1. clones `https://github.com/tdlib/td.git`
2. configures CMake for Android `arm64-v8a`
3. builds `tdjni`
4. copies resulting `libtdjni.so` to:
   - `app/src/main/jniLibs/arm64-v8a/libtdjni.so`

## Verify ABI packaging

```powershell
.\gradlew :app:verifyTdlibAbiCoverage
.\gradlew :app:assembleDebug
```

## Recommended release gate

Before shipping builds for phone/tablet/TV:

- ensure `arm64-v8a/libtdjni.so` exists
- run `:app:verifyTdlibAbiCoverage`
- install and smoke-test on at least one physical arm64 phone and one arm64 Android TV device
