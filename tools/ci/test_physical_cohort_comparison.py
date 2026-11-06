#!/usr/bin/env python3
"""Synthetic-only regression fixtures; never real-device or Telegram evidence."""
from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "qualification"))
import compare_physical_cohorts as evidence  # noqa: E402


class CohortComparisonTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="synthetic-bousy-evidence-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.count = 0

    def capture(self, *, step=100, native=True, abi="armeabi-v7a",
                model="fixture", scenario="arm32-playback", interrupted=False,
                different_pid=False, altered=None, same_time=False):
        self.count += 1
        start = datetime(2026, 11, 6, tzinfo=timezone.utc) + timedelta(hours=self.count)
        fmt = lambda date: date.isoformat().replace("+00:00", "Z")
        samples = []
        for i in range(20):
            samples.append({
                "cycle": i + 1,
                "capturedUtc": fmt(start + timedelta(seconds=i + 1)),
                "processId": 67890 if different_pid and i == 16 else 12345,
                "totalPssKiB": 50000 + (i + 1) * step,
                "nativeHeapPssKiB": 12000 + (i + 1) * step // 4 if native else None,
                "manualPlaybackVerified": False,
                "manualReconnectVerified": False,
            })
        if same_time:
            for s in samples:
                s["capturedUtc"] = fmt(start + timedelta(seconds=1))
        report = {
            "schemaVersion": 1,
            "status": "observations-only",
            "productionQualified": False,
            "realAccountReconnectVerified": False,
            "hardwarePlaybackVerified": False,
            "nativeEnginePlaybackVerified": False,
            "scenario": scenario,
            "requestedCycles": 20,
            "capturedCycles": 20,
            "aborted": interrupted,
            "secondsBetweenActions": 60,
            "startedUtc": fmt(start),
            "endedUtc": fmt(start + timedelta(seconds=22)),
            "packageName": "com.vaydelux.bousygram",
            "candidateApkSha256": "a" * 64,
            "sourceCommitSha": "b" * 40,
            "installedApkMatchesCandidate": False,
            "device": {
                "manufacturer": "synthetic",
                "model": model,
                "androidVersion": "15",
                "abi": abi
            },
            "samples": samples,
            "memory": {
                "firstTotalPssKiB": samples[0]["totalPssKiB"],
                "lastTotalPssKiB": samples[-1]["totalPssKiB"],
                "peakTotalPssKiB": max(x["totalPssKiB"] for x in samples),
                "lastMinusFirstPssKiB": samples[-1]["totalPssKiB"] - samples[0]["totalPssKiB"],
                "distinctProcessIds": 2 if different_pid else 1
            }
        }
        if altered:
            altered(report)
        output = self.root / ("capture-" + str(self.count) + ".json")
        output.write_text(json.dumps(report), encoding="utf-8")
        return output

    def cohorts(self, before=100, after=110, native=True):
        baseline = [self.capture(step=before + i, native=native) for i in range(3)]
        candidate = [self.capture(step=after + i, native=native) for i in range(3)]
        return baseline, candidate

    def loaded(self, paths):
        return [evidence.load_report(str(p), 10) for p in paths]

    def test_three_independent_runs_produce_observations_not_release_approval(self):
        baseline, candidate = self.cohorts()
        report = evidence.compare(self.loaded(baseline), self.loaded(candidate), 3)
        self.assertEqual("observed", report["checks"]["memoryComparison"])
        self.assertEqual("observed", report["checks"]["independentRuns"])
        self.assertEqual("observed", report["checks"]["nativeHeapPss"])
        self.assertEqual("skipped_no_baseline_threshold", report["checks"]["thresholdDecision"])
        self.assertEqual("missing", report["operatorAttestation"])
        self.assertEqual("missing", report["checks"]["actualRenderedFrames"])
        self.assertEqual("skipped", report["checks"]["hardwareLab"])
        self.assertFalse(report["productionQualified"])
        self.assertFalse(report["hardwarePlaybackVerified"])
        self.assertEqual(3, report["candidate"]["runs"])
        self.assertGreater(report["comparison"]["candidateMinusBaselineMedianDriftKiB"], 0)
        self.assertEqual([], report["comparison"]["flags"])

    def test_threshold_can_flag_regression_without_granting_pass(self):
        baseline, candidate = self.cohorts(before=100, after=900)
        report = evidence.compare(
            self.loaded(baseline), self.loaded(candidate), 3,
            max_extra_drift=100, max_peak_increase=100
        )
        self.assertEqual("failed", report["checks"]["memoryComparison"])
        self.assertEqual("failed", report["checks"]["thresholdDecision"])
        self.assertEqual(["excess_pss_drift", "excess_peak_pss"],
                         report["comparison"]["flags"])
        self.assertFalse(report["productionQualified"])

    def test_insufficient_independent_runs_are_missing_not_zero_improvement(self):
        before, after = self.cohorts()
        report = evidence.compare(self.loaded(before[:2]), self.loaded(after[:1]), 3)
        self.assertEqual("missing", report["checks"]["memoryComparison"])
        self.assertIsNone(report["comparison"])
        self.assertEqual(2, report["baseline"]["runs"])
        self.assertEqual(1, report["candidate"]["runs"])

    def test_native_heap_absence_is_missing_not_synthetic_zero(self):
        before, after = self.cohorts(native=False)
        report = evidence.compare(self.loaded(before), self.loaded(after), 3)
        self.assertEqual("missing", report["checks"]["nativeHeapPss"])
        self.assertIsNone(report["baseline"]["medianNativeHeapDriftKiB"])
        self.assertIsNone(report["candidate"]["medianNativeHeapDriftKiB"])

    def test_reject_cross_abi_and_scenario_and_device_comparisons(self):
        valid = self.loaded([self.capture()])
        cases = [
            dict(abi="arm64-v8a", scenario="native-engines"),
            dict(model="different"),
            dict(scenario="tdlib-reconnect")
        ]
        for kw in cases:
            with self.subTest(kw=kw), self.assertRaisesRegex(evidence.EvidenceError,
                                                            "mismatched_device_or_scenario"):
                evidence.compare(valid, self.loaded([self.capture(**kw)]), 2)

    def test_reject_stopped_process_and_interrupted_and_fake_manual_attestation(self):
        cases = [
            (dict(different_pid=True), "process_restarted_during_capture"),
            (dict(interrupted=True), "interrupted_capture"),
            (dict(altered=lambda r: r.update(productionQualified=True)),
             "untrusted_qualification_claim"),
            (dict(altered=lambda r: r["samples"][1].update(manualPlaybackVerified=True)),
             "untrusted_sample_attestation"),
            (dict(same_time=True), "non_monotonic_sample_times")
        ]
        for kw, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(
                evidence.EvidenceError, message
            ):
                evidence.load_report(str(self.capture(**kw)), 10)

    def test_reject_sample_summary_tampering_and_secret_keys(self):
        cases = [
            (lambda r: r["memory"].update(peakTotalPssKiB=1), "sample_summary_mismatch"),
            (lambda r: r.update(authToken="private"), "private_field_present"),
            (lambda r: r["device"].update(serial="private"), "private_field_present"),
            (lambda r: r["samples"][0].update(totalPssKiB=-5), "invalid_process_or_pss"),
            (lambda r: r.update(capturedCycles=19), "incomplete_capture")
        ]
        for mutate, reason in cases:
            with self.subTest(reason=reason), self.assertRaisesRegex(
                evidence.EvidenceError, reason
            ):
                evidence.load_report(str(self.capture(altered=mutate)), 10)

    def test_duplicate_capture_reuse_and_mixed_builds_are_rejected(self):
        p = self.capture()
        q = self.capture()
        with self.assertRaisesRegex(evidence.EvidenceError, "duplicate_capture_reused"):
            evidence.compare(self.loaded([p, q]), self.loaded([q]), 2)
        first = self.loaded([self.capture()])
        other = self.loaded([self.capture(altered=lambda x:
                              x.update(sourceCommitSha="c" * 40))])
        with self.assertRaisesRegex(evidence.EvidenceError, "different_builds_within_cohort"):
            evidence.compare(first + other, self.loaded([self.capture()]), 2)

    def test_cli_outputs_sanitized_report_with_correct_exit_codes(self):
        before, after = self.cohorts(before=100, after=900)
        output = self.root / "summary.json"
        command = ["--baseline", *map(str, before), "--candidate", *map(str, after),
                   "--out", str(output)]
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, evidence.main(command))
        decoded = json.loads(output.read_text())
        self.assertEqual("observations-only", decoded["status"])
        self.assertFalse(decoded["productionQualified"])
        self.assertNotIn("samples", decoded)
        self.assertNotIn("processId", output.read_text())
        self.assertNotIn("authToken", output.read_text())
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(1, evidence.main(command + ["--max-extra-drift-kib", "100"]))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(2, evidence.main(
                ["--baseline", str(before[0]), "--candidate", str(after[0]),
                 "--out", str(output)]
            ))
        self.assertEqual("missing", json.loads(output.read_text())["checks"]["memoryComparison"])

    def test_incomplete_and_corrupt_json_cannot_create_output(self):
        bad = self.root / "broken.json"
        bad.write_text("{not valid json")
        output = self.root / "should-not-exist.json"
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, evidence.main([
                "--baseline", str(bad), "--candidate", str(self.capture()),
                "--out", str(output)
            ]))
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
