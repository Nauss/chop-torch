"""Tests for the public SYNTHETIC harness; no private capture dependency."""
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class SpeedSweepToolTest(unittest.TestCase):
    def run_sweep(self, *args):
        return subprocess.run([sys.executable, str(ROOT / 'tools/speed_sweep.py'), *args],
                              cwd=ROOT, capture_output=True, text=True, timeout=60)

    def test_normal_control_is_one_pair_and_invalid_duration_is_refused(self):
        normal = self.run_sweep('--require-pair', '280')
        self.assertEqual(0, normal.returncode, normal.stderr)
        self.assertIn('SYNTHETIC periodMs=280 amplitude=6 sensitivity=100 hits=1', normal.stdout)
        self.assertIn('Compiled detector class ChopDetector$StrokeScale.class SHA256=', normal.stdout)
        invalid = self.run_sweep('--require-pair', '70')
        self.assertNotEqual(0, invalid.returncode)
        self.assertIn('invalid choice', invalid.stderr)

    def test_sweep_covers_unique_duration_amplitude_setting_axis_cadence_curve_combinations(self):
        result = self.run_sweep()
        self.assertEqual(0, result.returncode, result.stderr)
        rows = [line.split('\t') for line in result.stdout.splitlines() if line[:1].isdigit()]
        keys = [tuple(row[:6]) for row in rows]
        expected = 7 * 3 * 3 * 4 * 4 * 2
        self.assertEqual(expected, len(rows))
        self.assertEqual(expected, len(set(keys)))
        self.assertTrue(all(len(row) == 8 and int(row[6]) in (0, 1) for row in rows))
        self.assertIn('SYNTHETIC detector-only', result.stdout)


if __name__ == '__main__':
    unittest.main()
