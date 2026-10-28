# CI Test Results

## Completed

- Tracked wrapper mode: `100755`.
- Gradle wrapper distribution checksum added for Gradle 9.4.1.
- Gradle task discovery completed successfully; required task names exist.
- Actionlint 1.7.12: passed for both workflow files.
- TDLib compatibility fixture task: passed, including Java mismatch, ARM32/ARM64 mismatch, missing binary, wrong ELF, missing JNI marker, and invalid provenance cases.
- PowerShell TDLib scripts: syntax validated previously.
- TDLib ELF/ABI coverage: both ARM64 and ARM32 checks pass.

## Blocked or not run

- Full compile, lint, unit-test, and APK assembly: blocked before execution by the intentional TDLib Java/native mismatch gate.
- Firebase Test Lab: NOT CONFIGURED locally.
- Physical ARM64/ARM32 qualification: NOT RUN.
- GitHub Actions validation: not claimed; no post-change remote run has completed.
- Release signing/publication: not run and must remain blocked.
