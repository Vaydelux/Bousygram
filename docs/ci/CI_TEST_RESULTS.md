# CI Test Results

Audited change: [PR #3](https://github.com/Vaydelux/Bousygram/pull/3), commit `d1f91f96b8ce5bc718ec759384d28a1c0e67789b`.

## Remote GitHub Actions

- [TDLib Java API generation run 40841661912](https://github.com/Vaydelux/Bousygram/actions/runs/40841661912): passed. Checked out TDLib `4c6377aa7386c39217878a9a56d581922ad37ca4`, configured the upstream Android example as a Linux host build with Unix Makefiles, then ran `tl_generate_tlo` and `tl_generate_java`. Runner: Ubuntu 26.04, GNU C/C++ 13.3.0. Configure and build logs and the generated source were uploaded.
- [Android Enterprise run 40841661942](https://github.com/Vaydelux/Bousygram/actions/runs/40841661942): passed. Actionlint, ShellCheck, Bash syntax, wrapper integrity and negative fixtures, Android SDK resolution and fixtures, TDLib ABI/binding gates, Kotlin/Java compilation, unit tests, lint, debug APK assembly, AndroidTest assembly, debug APK verification, and artifact upload all succeeded. Gradle reported 95 actionable tasks (89 executed, 6 from cache); build time was 5m47s.
- CI uploaded debug APKs as artifact `debug-apks-4f24a8985ad41a59a84bf10c3fe5bbb974809a12` (artifact ZIP SHA-256 `87bf06843de2ca4b851fb5d90815262f841884b9c9a376e73346b019ba35c0ad`). The verifier selected the APK containing both supported TDLib ABIs and checked the instrumentation APK archive.

## Local verification

- Required TDLib tasks passed: `:app:verifyTdlibAbiCoverage`, `:app:testTdlibBindingCompatibilityFixtures`, and `:app:verifyTdlibBindingCompatibility`.
- Full requested Gradle suite passed: Kotlin compilation, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest`. Result: `BUILD SUCCESSFUL`; 262 unit tests, 0 failures, 0 errors, 0 skipped.
- Binding revision: `4c6377aa7386c39217878a9a56d581922ad37ca4`; package `org.drinkless.tdlib`; generated `TdApi.java` size 5,424,652 bytes; SHA-256 `FF2DF4321DBCA8F07239A111C1602FDC710F06E20F3AABFD1E823BB3636C5382`.
- Native SHA-256, matching `tools/tdlib/BUILD_METADATA.txt`:
  - ARM64 `libtdjni.so`: `205BE77AB847ADF1CABEADBB2B004D7153C15839AD85E4E5E9707D1BD1679C19`.
  - ARM32 `libtdjni.so`: `2C8D3A9D2F049D3EEAF028271954FD89F9FA315426BE52BC4D1FB44E91969D7B`.
- Local debug APK: 193,286,359 bytes; SHA-256 `B6223D7BF8212EC0FBB198E90ADAAE434F56E932EE84422DEB88AFA044E32D4A`.
- Local AndroidTest APK: 631,730 bytes; SHA-256 `BE164A284D760F6FDC20F5C7E758C82DEC012172B50E9D86559DC962E06D9637`.

## Remaining qualification

Firebase Test Lab reported `NOT CONFIGURED`; instrumentation was skipped. ARM32/ARM64 physical-device qualification, production signing, the three production APK outputs, protected qualification evidence, and publication approval have not been completed. Do not publish a release. Private-media Cast remains disabled in production.
