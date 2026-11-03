"""Freeze destination safety: SYNTHETIC directories only, no device or private data."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class FreezeB3Test(unittest.TestCase):
    def test_existing_delivery_directory_is_never_overwritten(self):
        scratch = ROOT / '.ops' / 'freeze-test-scratch'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            # An external durable-shaped directory; root is passed only as a
            # relative path below. Validation must reject worktree paths FIRST.
            out = Path(directory) / 'artifacts/lot-b3/delivery-v04'
            out.mkdir(parents=True)
            sentinel = out / 'sentinel.txt'
            sentinel.write_text('DO NOT OVERWRITE', encoding='utf-8')
            result = subprocess.run([sys.executable, str(ROOT/'tools/freeze_lot_b3.py'),
                '--output', str(out), '--previous-apk', str(out/'missing.apk')],
                capture_output=True, text=True, timeout=30)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('outside the build worktree', result.stderr)
            self.assertEqual('DO NOT OVERWRITE', sentinel.read_text(encoding='utf-8'))

    def test_wrong_destination_is_rejected_before_build(self):
        result = subprocess.run([sys.executable, str(ROOT/'tools/freeze_lot_b3.py'),
            '--output', str(ROOT/'.ops/wrong-delivery'), '--previous-apk', 'missing.apk'],
            capture_output=True, text=True, timeout=30)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('artifacts/lot-b3/delivery-v04', result.stderr)
        self.assertFalse((ROOT/'.ops/wrong-delivery').exists())

if __name__ == '__main__': unittest.main()
