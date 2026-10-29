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

## Blocked or not run

- Full compile, lint, unit-test, and APK assembly: blocked before execution by the intentional TDLib Java/native mismatch gate.
- Firebase Test Lab: NOT CONFIGURED locally.
- Physical ARM64/ARM32 qualification: NOT RUN.
- GitHub Actions run `40815011360` for `b3c2b981...` failed in the old `Validate Gradle wrapper` step before these parser changes; no post-change remote run has completed.
- Release signing/publication: not run and must remain blocked.
