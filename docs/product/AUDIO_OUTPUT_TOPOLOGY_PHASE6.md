# Bousygram — Phase 6 Audio Output Topology Recovery

## Scope

Media3's session change and READY handling remain authoritative for playback
state. The ExoPlayer wrapper also registers an Android AudioDeviceCallback
on Media3's application Looper and checks for meaningful connected-output
topology changes (for example a Bluetooth or HDMI device being added/removed).

The callback reports connected devices, **not necessarily the output actually
selected by Android for playback**. We do not show route labels, promise
passthrough DSP, or report acoustic quality based only on this signal.

When enabled local sound effects and active media are present:
- If Media3 is READY and the per-player audio session is valid, release and
  reapply the requested EQ/Smart Volume once for that topology change.
- If buffering or the session has no usable ID, discard old effect instances
  and show Pending. The existing READY/session callback restores processing.
- If media is stopped or both effects are Off, do nothing.
- Never call play, pause, seek, replace a decoder, re-download, or touch TDLib.
- Unregister the device callback exactly once on engine release. Callback
  registrations attach only to the app context, never to an Activity.
- Diagnostics expose only aggregate topology event counts, effect refresh
  attempts, the registration state, and effect status; no device name, IDs,
  addresses, filenames, or account data.

## Reliability bounds

Android's `AudioDeviceCallback` is a coarse device-connection signal and
is not guaranteed to track the exact active sink. Some OEM effects and HDMI
passthrough routes bypass local DSP even when Android accepts an AudioFX API.
Hardware validation is required for actual route switches and audio continuity.

## Verification plan

1. Run Kotlin/lint/JVM checks and all debug APK ABI builds at the PR head.
2. Run emulator media/Cast UI smoke and TDLib generation at the PR head.
3. Verify wired/Bluetooth/HDMI connect/disconnect without playback restarting,
   AV drift, or effect resources accumulating; note the original audio status.
4. Record 30 route changes, 30 stop/restart cycles, and 30 engine switches on
   physical ARM32 and ARM64 TVs; record CPU, native PSS, interruptions, and
   hearing A/B checks. Do not infer physical results from green CI.
