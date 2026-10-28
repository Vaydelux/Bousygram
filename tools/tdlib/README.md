# TDLib Native Build

This project currently needs `libtdjni.so` for each target ABI it ships.

At runtime, Android loads native libs based on the process ABI. If `libtdjni.so` is missing for that ABI, startup fails with:

- `java.lang.UnsatisfiedLinkError: dlopen failed: library "libtdjni.so" not found`

## Current state

- Required: `app/src/main/jniLibs/armeabi-v7a/libtdjni.so`
- Required: `app/src/main/jniLibs/arm64-v8a/libtdjni.so`

## Long-term target profile

- First-class: `arm64-v8a` (phone + tablet + Android TV)
- Optional later: `x86_64` (emulator/ChromeOS workflows)
- Legacy fallback: `armeabi-v7a`

## Build both target ABIs from one TDLib revision (Windows PowerShell)

From repository root:

```powershell
.\tools\tdlib\build_tdlib_android_arm64.ps1 -TdlibRef <pinned-commit> -AndroidNdk "C:\Users\<you>\AppData\Local\Android\Sdk\ndk\<version>" -OpenSslRoot "C:\path\to\openssl"
```

If `ANDROID_NDK_HOME` is set, `-AndroidNdk` can be omitted.

The script:

1. clones `https://github.com/tdlib/td.git`
2. checks out one TDLib revision for both ABIs
3. configures and builds `tdjni` for `arm64-v8a` and `armeabi-v7a`
4. copies each resulting library to its matching `jniLibs` directory
5. records the resolved commit, NDK, ABIs, and timestamp in `BUILD_METADATA.txt`

The OpenSSL root must contain `arm64-v8a` and `armeabi-v7a` directories, each with
`include/openssl/ssl.h` and `lib/libcrypto.a`. TDLib's `example/android/build-openssl.sh`
can produce these per-ABI static libraries when run in Git Bash or WSL.

## Verify ABI packaging

```powershell
.\gradlew :app:verifyTdlibAbiCoverage
.\gradlew :app:assembleDebug
```

## Recommended release gate

Before shipping builds for phone/tablet/TV:

- ensure both ABI libraries exist and are non-empty
- run `:app:verifyTdlibAbiCoverage`
- install and smoke-test on at least one physical arm64 phone and one arm64 Android TV device
