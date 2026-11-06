"""Strict unit fixtures only; these do NOT simulate physical ARM hardware."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "qualification"))
from compare_audio_processing import parse_summary, compare_pair, render_markdown


def sample(*, baseline=False, is64=True, cpu=600, wall=6000, p95=7, pss=50):
    return (
        f"I/BousyAudioPerf: final baselineOnly={str(baseline).lower()} "
        f"process64bit={str(is64).lower()} cycles=30 nativeEffectsApplied=8 "
        f"p95ApplyMs={p95} maxApplyMs=20 pssDeltaKiB={pss} "
        f"nativePssDeltaKiB=25 cpuDeltaMs={cpu} wallDeltaMs={wall}\n"
    )


class AudioPerfComparisonTest(unittest.TestCase):
    def test_accepts_measured_pair_and_reports_actual_deltas(self):
        a = parse_summary(sample(baseline=True, is64=False, cpu=300, p95=3))
        b = parse_summary(sample(is64=False, cpu=600, p95=7))
        result = compare_pair(a, b, is64=False)
        self.assertAlmostEqual(5.0, result["cpu_core_delta_pp"])
        self.assertEqual(4, result["p95_apply_delta_ms"])
        self.assertIn("ARM32 baseline", render_markdown({"arm32": result}))

    def test_rejects_wrong_abi_and_swapped_modes(self):
        a = parse_summary(sample(baseline=True, is64=True))
        b = parse_summary(sample(is64=True))
        with self.assertRaises(ValueError):
            compare_pair(a, b, is64=False)
        with self.assertRaises(ValueError):
            compare_pair(b, a, is64=True)

    def test_rejects_missing_and_multiple_final_samples(self):
        with self.assertRaises(ValueError):
            parse_summary("no device observations")
        with self.assertRaises(ValueError):
            parse_summary(sample() + sample())

    def test_rejects_incomplete_or_incomparable_runs(self):
        a = parse_summary(sample(baseline=True))
        b = parse_summary(sample(wall=1000))
        with self.assertRaises(ValueError):
            compare_pair(a, b, is64=True)
        with self.assertRaises(ValueError):
            parse_summary(sample().replace("cycles=30", "cycles=25"))
        with self.assertRaises(ValueError):
            parse_summary(sample().replace("p95ApplyMs=7", "p95ApplyMs=99"))


if __name__ == "__main__":
    unittest.main()
