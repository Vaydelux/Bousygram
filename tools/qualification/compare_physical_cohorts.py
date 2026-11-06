#!/usr/bin/env python3
"""Compare repeated, sanitized physical ARM memory captures; never approve releases.

Read only schema-1 JSON from Capture-BousygramPhysicalSoak.ps1. This is
offline-only, standard-library-only, and deliberately does not consume logcat,
APK binaries, Telegram media, operator credentials, or private network URLs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import statistics
import sys
from datetime import datetime
from pathlib import Path

PACKAGE = "com.vaydelux.bousygram"
SCENARIOS = {"arm32-playback", "tdlib-reconnect", "native-engines"}
ABIS = {"armeabi-v7a", "arm64-v8a"}
HASH64 = re.compile(r"^[a-fA-F0-9]{64}$")
HASH40 = re.compile(r"^[a-fA-F0-9]{40}$")
MAX_BYTES = 1_048_576
REDACTED_KEYS = {"password", "credential", "auth_token", "api_key", "secret",
                 "chat_id", "chatid", "phone", "serial", "private_url", "file_path",
                 "account_id", "accountid", "session_token"}


class EvidenceError(ValueError):
    """Fixed, non-sensitive validation reason."""


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise EvidenceError(reason)


def integer(value: object, minimum: int = 0) -> bool:
    return type(value) is int and value >= minimum


def timestamp(value: object) -> datetime:
    require(isinstance(value, str) and 10 <= len(value) <= 48,
            "invalid_timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise EvidenceError("invalid_timestamp") from exc
    require(parsed.tzinfo is not None, "timestamp_missing_timezone")
    return parsed


def reject_sensitive_keys(value: object) -> None:
    if isinstance(value, dict):
        for key, item in value.items():
            require(isinstance(key, str) and len(key) < 100, "invalid_key")
            normalized = re.sub(r"[^a-z0-9]", "", key.lower())
            require(not any(re.sub(r"[^a-z0-9]", "", bad) in normalized
                            for bad in REDACTED_KEYS), "private_field_present")
            reject_sensitive_keys(item)
    elif isinstance(value, list):
        for item in value:
            reject_sensitive_keys(item)


def load_report(path: str, minimum_cycles: int) -> dict:
    location = Path(path)
    require(location.is_file() and location.suffix.lower() == ".json",
            "missing_or_invalid_json_capture")
    require(location.stat().st_size <= MAX_BYTES, "oversized_capture")
    try:
        raw = json.loads(location.read_text(encoding="utf-8-sig"))
    except (UnicodeError, ValueError) as exc:
        raise EvidenceError("unreadable_json_capture") from exc
    require(isinstance(raw, dict), "invalid_capture_object")
    reject_sensitive_keys(raw)
    require(type(raw.get("schemaVersion")) is int and raw["schemaVersion"] == 1,
            "unsupported_capture_schema")
    require(raw.get("packageName") == PACKAGE and
            raw.get("status") == "observations-only", "invalid_capture_identity")
    for flag in ("productionQualified", "realAccountReconnectVerified",
                 "hardwarePlaybackVerified", "nativeEnginePlaybackVerified",
                 "installedApkMatchesCandidate"):
        require(raw.get(flag) is False, "untrusted_qualification_claim")
    require(raw.get("aborted") is False, "interrupted_capture")
    scenario = raw.get("scenario")
    require(isinstance(scenario, str) and scenario in SCENARIOS,
            "unknown_scenario")
    require(integer(raw.get("requestedCycles"), minimum_cycles) and
            raw.get("capturedCycles") == raw["requestedCycles"], "incomplete_capture")
    require(integer(raw.get("secondsBetweenActions"), 1) and
            raw["secondsBetweenActions"] <= 3600, "invalid_capture_interval")
    device = raw.get("device")
    require(isinstance(device, dict), "missing_device")
    for field in ("manufacturer", "model", "androidVersion", "abi"):
        value = device.get(field)
        require(isinstance(value, str) and 0 < len(value.strip()) <= 120,
                "invalid_device_metadata")
    require(device["abi"] in ABIS, "unsupported_abi")
    if raw["scenario"] == "arm32-playback":
        require(device["abi"] == "armeabi-v7a", "wrong_abi_for_arm32_scenario")
    started = timestamp(raw.get("startedUtc"))
    ended = timestamp(raw.get("endedUtc"))
    require(started < ended, "invalid_capture_time_window")
    samples = raw.get("samples")
    require(isinstance(samples, list) and
            len(samples) == raw["requestedCycles"], "incomplete_samples")
    pids = set()
    readings = []
    native = []
    previous_time = started
    for index, sample in enumerate(samples, 1):
        require(isinstance(sample, dict), "invalid_sample_object")
        require(sample.get("cycle") == index and type(sample.get("cycle")) is int,
                "out_of_order_samples")
        require(integer(sample.get("processId"), 1) and
                integer(sample.get("totalPssKiB"), 1), "invalid_process_or_pss")
        require(sample.get("manualPlaybackVerified") is False and
                sample.get("manualReconnectVerified") is False,
                "untrusted_sample_attestation")
        native_pss = sample.get("nativeHeapPssKiB")
        require(native_pss is None or integer(native_pss), "invalid_native_heap_pss")
        observed = timestamp(sample.get("capturedUtc"))
        require(previous_time < observed <= ended, "non_monotonic_sample_times")
        previous_time = observed
        pids.add(sample["processId"])
        readings.append(sample["totalPssKiB"])
        native.append(native_pss)
    require(len(pids) == 1, "process_restarted_during_capture")
    if isinstance(raw.get("memory"), dict):
        summary = raw["memory"]
        require(summary.get("firstTotalPssKiB") == readings[0] and
                summary.get("lastTotalPssKiB") == readings[-1] and
                summary.get("peakTotalPssKiB") == max(readings) and
                summary.get("distinctProcessIds") == 1 and
                summary.get("lastMinusFirstPssKiB") == readings[-1] - readings[0],
                "sample_summary_mismatch")
    apk = raw.get("candidateApkSha256")
    commit = raw.get("sourceCommitSha")
    require(apk is None or (isinstance(apk, str) and HASH64.fullmatch(apk)),
            "invalid_apk_hash")
    require(commit is None or (isinstance(commit, str) and HASH40.fullmatch(commit)),
            "invalid_commit_sha")
    window = max(2, len(readings) // 5)
    native_present = all(v is not None for v in native)
    def trend(values: list[int]) -> float:
        return float(statistics.median(values[-window:]) -
                     statistics.median(values[:window]))
    result = {
        "identity": (
            raw["scenario"], device["abi"], device["manufacturer"].casefold(),
            device["model"].casefold(), device["androidVersion"],
            raw["secondsBetweenActions"], raw["requestedCycles"]
        ),
        "started": started,
        "fingerprint": hashlib.sha256(
            json.dumps({"samples": samples, "startedUtc": raw["startedUtc"]},
                       sort_keys=True, separators=(",", ":")).encode()
        ).hexdigest(),
        "apk": apk.lower() if apk else None,
        "sourceCommit": commit.lower() if commit else None,
        "driftKiB": trend(readings),
        "peakKiB": max(readings),
        "nativeDriftKiB": trend(native) if native_present else None,
        "nativeSamplesMissing": native.count(None),
    }
    return result


def cohort_summary(reports: list[dict]) -> dict:
    native = [r["nativeDriftKiB"] for r in reports if r["nativeDriftKiB"] is not None]
    return {
        "runs": len(reports),
        "medianPssDriftKiB": statistics.median(r["driftKiB"] for r in reports),
        "medianPeakPssKiB": statistics.median(r["peakKiB"] for r in reports),
        "medianNativeHeapDriftKiB": statistics.median(native) if len(native) == len(reports) else None,
        "nativeHeapMissingSamples": sum(r["nativeSamplesMissing"] for r in reports),
        "apkProvenance": "operator_supplied" if all(r["apk"] for r in reports) else "missing",
        "sourceCommitProvenance": "operator_supplied" if all(r["sourceCommit"] for r in reports) else "missing"
    }


def compare(baseline: list[dict], candidate: list[dict], minimum_runs: int,
            max_extra_drift: float | None = None,
            max_peak_increase: float | None = None) -> dict:
    all_reports = baseline + candidate
    require(len({r["fingerprint"] for r in all_reports}) == len(all_reports),
            "duplicate_capture_reused")
    require(len({r["identity"] for r in all_reports}) == 1,
            "mismatched_device_or_scenario")
    for group in (baseline, candidate):
        hashes = {r["apk"] for r in group if r["apk"]}
        commits = {r["sourceCommit"] for r in group if r["sourceCommit"]}
        require(len(hashes) <= 1 and len(commits) <= 1,
                "different_builds_within_cohort")
    identity = all_reports[0]["identity"]
    report = {
        "schemaVersion": 1,
        "status": "observations-only",
        "productionQualified": False,
        "hardwarePlaybackVerified": False,
        "realAccountReconnectVerified": False,
        "nativeEnginePlaybackVerified": False,
        "operatorAttestation": "missing",
        "deviceLab": "skipped_not_executed_by_comparator",
        "scenario": identity[0],
        "device": {
            "abi": identity[1], "manufacturer": identity[2],
            "model": identity[3], "androidVersion": identity[4]
        },
        "capture": {"secondsBetweenActions": identity[5], "cyclesPerRun": identity[6]},
        "baseline": cohort_summary(baseline),
        "candidate": cohort_summary(candidate),
        "checks": {},
        "comparison": None,
        "notes": "Statistics are observations, not decoder, account, installed APK, or release verification."
    }
    checks = report["checks"]
    checks["independentRuns"] = ("observed" if len(baseline) >= minimum_runs and
                                 len(candidate) >= minimum_runs else "missing")
    checks["nativeHeapPss"] = ("observed" if
                                report["baseline"]["nativeHeapMissingSamples"] == 0 and
                                report["candidate"]["nativeHeapMissingSamples"] == 0
                                else "missing")
    checks["apkIdentity"] = ("operator_supplied_unverified" if
                              report["baseline"]["apkProvenance"] == "operator_supplied" and
                              report["candidate"]["apkProvenance"] == "operator_supplied"
                              else "missing")
    checks["physicalPlayback"] = "missing"
    checks["actualRenderedFrames"] = "missing"
    checks["authenticatedTdlibReconnect"] = "missing"
    checks["manualOperatorReview"] = "missing"
    checks["hardwareLab"] = "skipped"
    if checks["independentRuns"] == "missing":
        checks["memoryComparison"] = "missing"
        return report
    delta_drift = report["candidate"]["medianPssDriftKiB"] - report["baseline"]["medianPssDriftKiB"]
    delta_peak = report["candidate"]["medianPeakPssKiB"] - report["baseline"]["medianPeakPssKiB"]
    failures = []
    if max_extra_drift is not None and delta_drift > max_extra_drift:
        failures.append("excess_pss_drift")
    if max_peak_increase is not None and delta_peak > max_peak_increase:
        failures.append("excess_peak_pss")
    checks["memoryComparison"] = "failed" if failures else "observed"
    checks["thresholdDecision"] = ("failed" if failures else "observed" if
                                    max_extra_drift is not None or max_peak_increase is not None
                                    else "skipped_no_baseline_threshold")
    report["comparison"] = {
        "candidateMinusBaselineMedianDriftKiB": delta_drift,
        "candidateMinusBaselineMedianPeakKiB": delta_peak,
        "flags": failures
    }
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", nargs="+", required=True, help="2+ sanitized JSON captures")
    parser.add_argument("--candidate", nargs="+", required=True, help="2+ sanitized JSON captures")
    parser.add_argument("--out", required=True, help="local JSON summary path; never publish raw evidence")
    parser.add_argument("--min-runs", type=int, default=3)
    parser.add_argument("--min-cycles", type=int, default=10)
    parser.add_argument("--max-extra-drift-kib", type=float)
    parser.add_argument("--max-peak-increase-kib", type=float)
    args = parser.parse_args(argv)
    if args.min_runs < 2 or args.min_cycles < 2 or any(
        value is not None and (value < 0 or not math.isfinite(value))
        for value in (args.max_extra_drift_kib, args.max_peak_increase_kib)
    ):
        parser.error("minimum runs/cycles must be >=2; optional thresholds must be nonnegative finite KiB")
    try:
        baseline = [load_report(x, args.min_cycles) for x in args.baseline]
        candidate = [load_report(x, args.min_cycles) for x in args.candidate]
        results = compare(baseline, candidate, args.min_runs,
                          args.max_extra_drift_kib, args.max_peak_increase_kib)
        output = Path(args.out)
        require(output.suffix.lower() == ".json" and
                output.resolve() not in {
                    Path(x).resolve() for x in (args.baseline + args.candidate)
                }, "invalid_report_output_path")
        output.parent.mkdir(parents=True, exist_ok=True)
        # Write only fully validated summaries; partial JSON is not evidence.
        temporary = output.with_name(output.name + ".tmp")
        temporary.write_text(json.dumps(results, indent=2, sort_keys=True) + "\n",
                             encoding="utf-8")
        temporary.replace(output)
    except (EvidenceError, OSError) as exc:
        reason = str(exc) if isinstance(exc, EvidenceError) else "local_file_io_error"
        print("Device evidence rejected: " + reason, file=sys.stderr)
        return 2
    status = results["checks"]["memoryComparison"]
    print("Memory comparison: " + status + " (observations only; release approval remains pending)")
    return 2 if status == "missing" else 1 if status == "failed" else 0


if __name__ == "__main__":
    raise SystemExit(main())
