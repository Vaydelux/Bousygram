#!/usr/bin/env python3
"""Compare *measured* BousyAudioPerf physical logs; never fabricate samples.

Source: AudioPlayingPhysicalPerformanceTest's final summary line.
The actual ARM32/ARM64 devices, source APK identity and ambient conditions
must be qualified separately. This utility is an evidence parser, not a test
of audio quality or a release-certification oracle.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import re
from typing import Sequence

SUMMARY = re.compile(
    r"\bfinal baselineOnly=(true|false) process64bit=(true|false) "
    r"cycles=(\d+) nativeEffectsApplied=(\d+) p95ApplyMs=(\d+) "
    r"maxApplyMs=(\d+) pssDeltaKiB=(-?\d+) "
    r"nativePssDeltaKiB=(-?\d+) cpuDeltaMs=(\d+) wallDeltaMs=(\d+)\b"
)


@dataclass(frozen=True)
class AudioPerfSummary:
    baseline: bool
    is64: bool
    cycles: int
    native_applied: int
    p95_ms: int
    max_ms: int
    pss_delta_kib: int
    native_pss_delta_kib: int
    cpu_ms: int
    wall_ms: int

    @property
    def core_equivalent_pct(self) -> float:
        # 100% means consuming 1 full CPU core; multi-thread values can exceed it.
        return 100 * self.cpu_ms / self.wall_ms


def parse_summary(data: str) -> AudioPerfSummary:
    matches = [SUMMARY.search(line) for line in data.splitlines()]
    found = [match for match in matches if match is not None]
    if len(found) != 1:
        raise ValueError(
            f"Expected exactly one 'BousyAudioPerf: final ...' sample; got {len(found)}. "
            "Clear/filter logcat for one instrumented run per file."
        )
    match = found[0]
    assert match is not None
    mode, bitness, *nums = match.groups()
    values = tuple(map(int, nums))
    summary = AudioPerfSummary(mode == "true", bitness == "true", *values)
    if summary.cycles != 30 or summary.wall_ms <= 0:
        raise ValueError("Incomplete run: expected 30 cycles and a positive wall duration")
    if summary.native_applied > summary.cycles or min(
        summary.p95_ms, summary.max_ms, summary.cpu_ms, summary.native_applied
    ) < 0:
        raise ValueError("Invalid performance counter or native Applied count")
    if summary.p95_ms > summary.max_ms:
        raise ValueError("p95 apply time cannot exceed maximum apply time")
    return summary


def read_summary(path: Path) -> AudioPerfSummary:
    return parse_summary(path.read_text(encoding="utf-8", errors="replace"))


def compare_pair(
    baseline: AudioPerfSummary,
    effects: AudioPerfSummary,
    is64: bool,
) -> dict[str, float | int]:
    if baseline.is64 != is64 or effects.is64 != is64:
        raise ValueError("ARM32/ARM64 log pair was supplied to the wrong ABI slot")
    if not baseline.baseline or effects.baseline:
        raise ValueError("Pass a baselineOnly=true log and an effects-on log")
    # A wildly different wall duration makes a CPU comparison misleading.
    duration_ratio = effects.wall_ms / baseline.wall_ms
    if not 0.7 <= duration_ratio <= 1.3:
        raise ValueError("Wall durations differ by >30%; repeat under comparable conditions")
    return {
        "baseline_cpu_core_pct": baseline.core_equivalent_pct,
        "effects_cpu_core_pct": effects.core_equivalent_pct,
        "cpu_core_delta_pp": effects.core_equivalent_pct - baseline.core_equivalent_pct,
        "baseline_p95_apply_ms": baseline.p95_ms,
        "effects_p95_apply_ms": effects.p95_ms,
        "p95_apply_delta_ms": effects.p95_ms - baseline.p95_ms,
        "baseline_pss_delta_kib": baseline.pss_delta_kib,
        "effects_pss_delta_kib": effects.pss_delta_kib,
        "pss_trend_difference_kib": effects.pss_delta_kib - baseline.pss_delta_kib,
        "baseline_native_pss_delta_kib": baseline.native_pss_delta_kib,
        "effects_native_pss_delta_kib": effects.native_pss_delta_kib,
        "native_pss_trend_difference_kib":
            effects.native_pss_delta_kib - baseline.native_pss_delta_kib,
        "effects_applied_cycles": effects.native_applied,
    }


def render_markdown(results: dict[str, dict[str, float | int]]) -> str:
    rows = [
        "# Bousygram physical audio performance — measured comparison",
        "",
        "| Metric | ARM32 baseline | ARM32 effects | ARM64 baseline | ARM64 effects |",
        "|---|---:|---:|---:|---:|",
    ]
    def fmt(abi: str, key: str, suffix: str = "") -> str:
        value = results.get(abi, {}).get(key)
        return "—" if value is None else f"{value:.2f}{suffix}" if isinstance(value, float) else f"{value}{suffix}"
    columns = (
        ("Process CPU (1-core equivalent, %)", "baseline_cpu_core_pct", "effects_cpu_core_pct", "%"),
        ("EQ apply p95 (ms)", "baseline_p95_apply_ms", "effects_p95_apply_ms", ""),
        ("Total PSS change during run (KiB)", "baseline_pss_delta_kib", "effects_pss_delta_kib", ""),
        ("Native PSS change during run (KiB)", "baseline_native_pss_delta_kib", "effects_native_pss_delta_kib", ""),
    )
    for label, base, effects, suffix in columns:
        rows.append(
            f"| {label} | {fmt('arm32', base, suffix)} | {fmt('arm32', effects, suffix)} "
            f"| {fmt('arm64', base, suffix)} | {fmt('arm64', effects, suffix)} |"
        )
    rows += [
        "",
        "**Differences (effects minus baseline)**",
        "",
        "| ABI | CPU core-equivalent (pp) | p95 apply (ms) | Total PSS trend (KiB) | Native PSS trend (KiB) |",
        "|---|---:|---:|---:|---:|",
    ]
    for abi in ("arm32", "arm64"):
        result = results.get(abi)
        if result is not None:
            rows.append(
                f"| {abi.upper()} | {result['cpu_core_delta_pp']:+.2f} "
                f"| {result['p95_apply_delta_ms']:+d} "
                f"| {result['pss_trend_difference_kib']:+d} "
                f"| {result['native_pss_trend_difference_kib']:+d} |"
            )
    rows += [
        "",
        "These are snapshots from physical instrumentation logs, not LUFS, acoustic, "
        "audio latency, battery-power, or leak-free certifications. CPU includes "
        "other application work; change in PSS is not peak PSS. Capture repeated "
        "same-firmware runs at comparable volume and thermal state before claiming "
        "an actual sustained performance improvement.",
        "",
    ]
    return "\n".join(rows)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for abi in ("arm32", "arm64"):
        parser.add_argument(f"--{abi}-baseline", type=Path)
        parser.add_argument(f"--{abi}-effects", type=Path)
    parser.add_argument("--output", type=Path, help="Optional Markdown report file")
    args = parser.parse_args(argv)
    results: dict[str, dict[str, float | int]] = {}
    for abi, is64 in (("arm32", False), ("arm64", True)):
        baseline = getattr(args, f"{abi}_baseline")
        effects = getattr(args, f"{abi}_effects")
        if (baseline is None) != (effects is None):
            parser.error(f"Both --{abi}-baseline and --{abi}-effects are required together")
        if baseline is not None:
            try:
                results[abi] = compare_pair(
                    read_summary(baseline), read_summary(effects), is64
                )
            except (OSError, ValueError) as exc:
                parser.error(f"{abi}: {exc}")
    if not results:
        parser.error("Supply at least one physical-device baseline/effects log pair")
    report = render_markdown(results)
    if args.output:
        args.output.write_text(report, encoding="utf-8")
    print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
