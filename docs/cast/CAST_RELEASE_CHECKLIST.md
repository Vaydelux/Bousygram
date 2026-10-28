# Cast release checklist

- [x] Dedicated branch `feat/google-cast-native-tdlib` created without discarding the existing worktree.
- [x] Official Cast sender dependency and Default Media Receiver configuration added.
- [x] Mobile playback route control added; Android TV layout does not show it.
- [x] LAN server binds to a selected private interface and uses resource-scoped random capabilities.
- [x] Cast stream delivery reuses the TDLib scheduler-backed progressive streamer.
- [x] Android 17 `ACCESS_LOCAL_NETWORK` declared and requested before discovery.
- [x] External intent payload is tied to an authorized Telegram message before local playback handoff.
- [x] Full unit/lint/build/ABI/TV hardening suite rerun after all Cast changes.
- [ ] Physical Chromecast discovery, load, controls, seek, handoff, network loss, TLS behavior, authentication failure/expiry, and screen-off tests.
- [x] Private-media Cast disabled in release and hardened-release variants pending verified trusted TLS and physical receiver validation.
- [ ] Android 17 permission grant, denial, revocation, and reconnect tests on API 37 hardware.
- [x] Cast LAN server starts a visible media-playback foreground service and stops it on teardown.
- [ ] Screen-off, battery optimization, memory pressure, and unexpected process termination validation.
- [ ] Resolve HTTP bearer-token confidentiality risk and validate receiver transport constraints.
- [ ] Test API 37 local-network permission grant, denial, revocation, and network change.
- [ ] Verify background service/lifecycle requirements and process-death behavior.
- [ ] Validate H.264/AAC metadata and unsupported media handling on real receiver hardware.
- [ ] Review Android TV regression results and production signing/release artifacts.

**Release readiness: INCOMPLETE.** The code compiles, but receiver and security acceptance gates remain open.
