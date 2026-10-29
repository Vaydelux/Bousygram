# CI Test Results

## Completed

- Tracked wrapper mode: `100755`.
- Gradle wrapper distribution checksum added for Gradle 9.4.1.
- Gradle task discovery completed successfully; required task names exist.
- Actionlint 1.7.12: passed for both workflow files.
- TDLib compatibility fixture task: passed, including Java mismatch, ARM32/ARM64 mismatch, missing binary, wrong ELF, missing JNI marker, and invalid provenance cases.
- PowerShell TDLib scripts: syntax validated previously.
- TDLib ELF/ABI coverage: both ARM64 and ARM32 checks pass.
- Gradle wrapper parser: passed against the checked-in properties, wrapper JAR hash, and wrong-URL/wrong-checksum fixtures.
- Release workflow: source-lineage, exact-commit check-run, protected qualification-record, signed-candidate hash, and safe manifest checks added.
- API 37 SDK setup fixtures: passed; the resolver accepts the runner's advertised `platforms;android-37.0`, validates `AndroidVersion.ApiLevel`, Build Tools, and runs an AGP 9.1.1 `preDebugBuild` resolution probe.
- GitHub Actions run `40816281013` confirmed the prior wrapper fix and then failed at the old hard-coded `platforms;android-37` installation step.
- Full local Gradle gate reached and passed TDLib ABI, TV, media-Cast, redaction, and fixture tasks before failing at binding compatibility with the real Java/native revision mismatch.

## Blocked or not run

- Full compile, lint, unit-test, and APK assembly: blocked by `TdApi.java=cb863c1600082404428f1a84e407b866b9d412a8` versus native `4c6377aa7386c39217878a9a56d581922ad37ca4`.
- Firebase Test Lab: NOT CONFIGURED locally.
- Physical ARM64/ARM32 qualification: NOT RUN.
- GitHub Actions run `40815011360` for `b3c2b981...` failed in the old `Validate Gradle wrapper` step before these parser changes; no post-change remote run has completed.
- Release signing/publication: not run and must remain blocked.
- TDLib regeneration: blocked locally because no host C++ linker/toolchain is installed. NDK Clang was found, but CMake failed linking the host generator due to missing Windows/MinGW libraries (`kernel32`, `msvcrt`, `libstdc++`, `mingw32`, and related libraries). No Java hash was changed and no fake regeneration was performed.
