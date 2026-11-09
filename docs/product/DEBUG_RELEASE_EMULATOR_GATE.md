# Debug APK prerequisite: exact-commit emulator smoke

The publisher is triggered when either Android Enterprise Checks or Android
Emulator Cast Smoke completes successfully on main. It only publishes
once both have passed for the identical source commit; the initial event
exits cleanly and waits for the other event to trigger a fresh publisher job.

Android Enterprise must supply its successful build/test and native ABI
artifact jobs. The emulator gate requires the latest exact-SHA successful
mainline push run, not an older commit, PR, wrong repository, pending run
or failed run. A manual publisher dispatch must meet both conditions.

Emulator smoke now runs on every mainline push, avoiding path-filtered
commits with no emulator result. PR builds remain path-filtered.
Idempotent immutable release tags and one-at-a-time publishing remain intact.

Tests check invalid repository/branch/event/commit, incomplete runs, outdated
prior successes and malformed evidence. The release notes expose both exact
CI run URLs.

This does not substitute for actual 32-bit/64-bit Android TV testing.
