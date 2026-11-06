# Bousygram — Efficient EQ and measured ARM audio comparisons

## Changes

ExoPlayer avoids rewriting identical native equalizer bands when its session,
enabled effect, and effective tone curve are all unchanged. A change to Custom
band gain or EQ preset forces a rewrite. Smart Volume avoids re-enabling a
DynamicsProcessing instance if it is already enabled, controlled and configured
for the requested mode on the same session.

Native audio session changes, stopped players, and output topology changes
still invalidate these fast paths. Decoder playback state is unchanged.

Diagnostics now expose aggregate counters: `audioEqBandWrites`,
`audioEqUnchangedSkips`, `audioSmartEffectCreations`, and
`audioSmartUnchangedSkips`. No audio device ID, account data, file ID,
media path or content is recorded.

## Compare actual physical ARM32 and ARM64 data

Run the opt-in `AudioPlayingPhysicalPerformanceTest` from the Phase 7
instructions twice per device, with
`-e audioPerfBaseline true` for Original/Off and without it for effects on.
Capture each individual run's `BousyAudioPerf: final ...` log line into
its own text file. Avoid mixing old logcat runs.

```bash
python3 tools/qualification/compare_audio_processing.py \
  --arm32-baseline arm32-off.txt --arm32-effects arm32-effects.txt \
  --arm64-baseline arm64-off.txt --arm64-effects arm64-effects.txt \
  --output audio-performance-comparison.md
```

The comparator is fail-closed for missing/multiple samples, incorrect ABI,
swapped baseline/effects, incomplete cycles and highly mismatched wall times.
It reports measured process CPU (one-core equivalent), p95 settings cost,
and memory trends. These are NOT hardware certification, sound analysis,
peak clipping, LUFS or leak-free claims. Compare multiple runs with the same
media, firmware, volume, output route and thermal conditions before drawing
conclusions.
