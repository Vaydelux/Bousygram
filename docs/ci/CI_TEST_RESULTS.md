# CI Test Results

## Completed

- Tracked wrapper mode: `100755`.
- Gradle wrapper distribution checksum added for Gradle 9.4.1.
- Gradle task discovery completed successfully; required task names exist.
- Actionlint 1.7.12: previously recorded pass before the current workflow edits; not installed locally to rerun against these edits.
- TDLib compatibility fixture task: passed, including Java mismatch, ARM32/ARM64 mismatch, missing binary, wrong ELF, missing JNI marker, and invalid provenance cases.
- PowerShell TDLib scripts: syntax validated previously.
- TDLib ELF/ABI coverage: both ARM64 and ARM32 checks pass.
- Gradle wrapper parser: passed against the checked-in properties, wrapper JAR hash, and wrong-URL/wrong-checksum fixtures.
- Release workflow: source-lineage check and protected qualification-record/hash gates are present. The exact-source CI qualification now resolves the workflow run and its required job; release publication remains disabled during this implementation pass.
- API 37 SDK setup fixtures: passed; the resolver accepts the runner's advertised `platforms;android-37.0`, validates `AndroidVersion.ApiLevel`, Build Tools, and runs an AGP 9.1.1 `preDebugBuild` resolution probe.
- Latest GitHub Actions run `40817939430` on `e11e09705361b7ad203566fb9a02aac1039c29f0`: wrapper integrity, wrapper negative fixtures, SDK installation, and SDK probe passed; `verifyTdlibAbiCoverage`, TV hardening, log redaction, and binding fixtures passed before `verifyTdlibBindingCompatibility` failed on the real Java/native revision mismatch. Diagnostic artifact uploaded; debug APK artifact skipped. Firebase reported NOT CONFIGURED and the instrumentation job was skipped.

## Blocked or not run

- Full compile, lint, unit-test, and APK assembly: blocked on the mismatch above; remote run 40817939430 stopped before those tasks.
- Firebase Test Lab: NOT CONFIGURED locally.
- Physical ARM64/ARM32 qualification: NOT RUN.
- No remote Actions run has executed the uncommitted workflow changes in this working tree.
- Release signing/publication: not run and must remain blocked.
- TDLib regeneration: pinned source checkout is at `4c6377aa7386c39217878a9a56d581922ad37ca4`. The prerequisites bundle provides CMake 3.29.2/4.5.0-rc1, Ninja 1.13.2, gperf, and GNU C++ 13.2.0. A direct compile/link/run probe with that compiler passed, but both upstream CMake configure attempts stalled at `Detecting CXX compiler ABI info` before generating the `tl_generate_java` target. No Java hash was changed and no generated code was fabricated. Docker and WSL are unavailable.
- Requested local Gradle suite exited 1 after 58 seconds at `:app:verifyTdlibBindingCompatibility`; reported mismatch is unchanged. The remaining compile, lint, test, and APK tasks did not run.
