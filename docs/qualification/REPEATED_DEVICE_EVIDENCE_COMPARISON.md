# P2.1 — Repeated physical-device evidence comparison

**Implementation:** multi-run, offline-only analysis, synthetic fixture tests,
and a CI validation gate. **No physical ARM32/ARM64 device has been attached
to this work.** This is not a performance pass or release certification.

The comparer accepts **only** sanitized schema-1 outputs from
`tools/qualification/Capture-BousygramPhysicalSoak.ps1`. It never reads
Telegram authentication, media URLs, logs, raw video, accounts, or APK binaries.

## A controlled run

Install the exact build under investigation on a physical device, then
capture three or more separate, complete runs of the same scenario. Use
the same model, Android version, ABI, clip/codec, network, power state,
cycle count and sampling interval between before/after groups.

Capture baseline and candidate using the existing sampler:

```powershell
.\tools\qualification\Capture-BousygramPhysicalSoak.ps1 -Scenario arm32-playback -Cycles 60 -SampleSeconds 60 -ApkPath ".\baseline.apk" -SourceCommit "<40-char-baseline-commit>"
.\tools\qualification\Capture-BousygramPhysicalSoak.ps1 -Scenario arm32-playback -Cycles 60 -SampleSeconds 60 -ApkPath ".\candidate.apk" -SourceCommit "<40-char-candidate-commit>"
```

Run each command **at least three independent times** and rename the resulting
JSON files locally to distinguish runs. The sampler's APK hash is
**operator-supplied provenance**, not proof that the installed APK matches.

On a computer with Python 3.10+:

```powershell
python tools/qualification/compare_physical_cohorts.py --baseline ".\qualification-evidence\before-1.json" ".\qualification-evidence\before-2.json" ".\qualification-evidence\before-3.json" --candidate ".\qualification-evidence\after-1.json" ".\qualification-evidence\after-2.json" ".\qualification-evidence\after-3.json" --min-runs 3 --min-cycles 20 --out ".\qualification-evidence\comparison.json"
```

The single-PSS-pair PowerShell comparator remains available for exploratory
debugging, but this command requires repeated runs by default.

## What the output measures

- Median **early-to-late Total PSS drift** within each run, taking the first
  and last 20% of valid memory samples. Reports the median across independent
  runs and the candidate-minus-baseline difference.
- Median **peak Total PSS** across independent runs, plus its
  candidate-minus-baseline difference.
- Optional native heap drift, recorded as **missing** if any capture lacks
  valid native heap samples. Unknown values are never converted to zero.
- A separately controlled **regression flag** may be enabled only after
  collecting representative physical baselines:

```powershell
python tools/qualification/compare_physical_cohorts.py --baseline ".\qualification-evidence\before-1.json" ".\qualification-evidence\before-2.json" ".\qualification-evidence\before-3.json" --candidate ".\qualification-evidence\after-1.json" ".\qualification-evidence\after-2.json" ".\qualification-evidence\after-3.json" --max-extra-drift-kib 4096 --max-peak-increase-kib 8192 --out ".\qualification-evidence\comparison.json"
```

The **4096/8192 KiB thresholds above are illustrative command arguments, not
adopted Bousygram limits**. Use thresholds only when justified by repeat runs
on the target physical device; a flagged regression is a prompt to investigate,
not a scientific proof that memory leaked.

## Fail-closed evidence checks

The analyzer rejects:

- Nonphysical/invalid primary ABIs, non-ARM32 inputs for the ARM32 scenario,
  mismatched devices, scenarios, Android versions, cadence or cycle counts.
- Interrupted, empty, incomplete, reordered, duplicate, non-monotonic,
  process-restarted or altered-sample captures.
- Falsely set human verification or production approval flags, inconsistent
  Total PSS summary, private-looking field names and oversized/unreadable JSON.
- Multiple different APK or source-commit hashes within the **same** cohort;
  a baseline and candidate may differ because code changes are being measured.

The output uses **observed**, **missing**, **failed**, and **skipped** checks,
while all manual authorization, actual rendered frames and native-engine
behavior stay **missing** until reviewed by an operator. There is no
automatic status named "hardware passed." All output contains
`productionQualified: false`.

| Exit code | Meaning |
| --- | --- |
| `0` | Enough comparable observations; no optional threshold flagged |
| `1` | One or more **opt-in** numeric regression thresholds flagged |
| `2` | Missing minimum runs or invalid/incompatible evidence |

A code 0 **does not** mean Bousygram has passed device qualification.

## CI versus device-lab roles

The Android Enterprise workflow invokes only Python's standard-library
`unittest` suite against **fabricated** memory histories and deliberate
failure fixtures. It does not upload real-device captures, passwords or logs.

The existing optional Firebase Test Lab jobs require
`FIREBASE_TEST_LAB_ENABLED=true` and configured protected credentials/device
variables. Firebase emulator/device instrumentation is **not** equivalent
to authenticated Telegram reconnect, physical ARM32 memory profiling, or
manual rendered-video/audio/subtitle verification. These remain separately
documented in [physical-device priority gates](PHYSICAL_DEVICE_PRIORITY_GATE.md).

For real release approval, keep operator notes and media/decoder observations
private; follow [release qualification](../release/PLAYBACK_DEVICE_QUALIFICATION.md)
and protected reviewer approval. **Do not commit physical JSON captures to
the public repository or upload them to public CI artifacts.**
