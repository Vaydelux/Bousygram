# Release Checklist

- [x] Full JVM unit suite is green and order independent.
- [x] Debug lint passes without a baseline or suppressed unresolved errors.
- [x] TDLib ABI gate verifies non-empty arm64-v8a and armeabi-v7a libraries.
- [x] TV hardening contract passes.
- [x] Debug and signed release APKs assemble; hashes and sizes are recorded.
- [ ] Playback opens from click and D-pad OK without a second activation.
- [ ] Left/Right cannot change videos; seek preview confirm/cancel works.
- [x] HTTP 200/206/416 and disconnect cases pass in host tests.
- [x] Multiple transient consumers do not cancel each other; pinned files are protected by the scheduler.
- [ ] Forum topic media remains isolated by real topic ID.
- [x] Keystore, loopback binding, capability expiry/revocation, and backup exclusion are intact in static/unit verification.
- [ ] Cold/warm playback and seek metrics are captured on representative 32-bit and 64-bit TVs.

## Rollback

Changes are grouped by subsystem. Revert the UI/search changes independently from streaming. If the lease coordinator must be rolled back, restore the prior scheduler/DataSource/HTTP files together; do not remove loopback HTTP while mpv remains enabled. Telemetry fields are backward-compatible because missing JSON fields parse to defaults. Database series fields are retained, so the Telegram-first UI change requires no data migration.
