"""Public tests generate SYNTHETIC exports only; never read a user's capture."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ReplayToolTest(unittest.TestCase):
    def setUp(self):
        scratch = ROOT / '.ops' / 'tool-test-scratch'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'SYNTHETIC.json'
        self.env = os.environ.copy()
        for key in ['TMPDIR', 'TEMP', 'TMP']:
            self.env[key] = str(scratch)

    def replay(self, data, *args):
        self.path.write_text(json.dumps(data), encoding='utf-8')
        return subprocess.run([sys.executable, str(ROOT / 'tools/replay_capture.py'), str(self.path), *args],
                              cwd=ROOT, env=self.env, capture_output=True, text=True, timeout=60)

    def fixture(self, v2=True):
        columns = ['relative_ms', 'raw_x_m_s2', 'raw_y_m_s2', 'raw_z_m_s2']
        samples = [[i * 20, 0.0, 0.0, 9.81] for i in range(50)]
        if v2:
            columns += ['received_relative_ms', 'freshness_accepted', 'sensitivity']
            samples = [r + [r[0] + 10, True, 100] for r in samples]
        return {'format': 'choptorch-diagnostic-v2' if v2 else 'choptorch-diagnostic-v1',
                'captureColumns': columns, 'samples': samples}

    def test_recorded_and_recomputed_admission_are_distinct_and_report_disagreement(self):
        data = self.fixture()
        data['samples'][20][4] += 300
        recorded = self.replay(data, '--mode', 'recorded-freshness')
        computed = self.replay(data, '--mode', 'recomputed-freshness')
        self.assertEqual(0, recorded.returncode, recorded.stderr)
        self.assertEqual(0, computed.returncode, computed.stderr)
        self.assertEqual(3, recorded.stdout.count('admissionRejected=0 freshnessDisagreements=1'))
        self.assertEqual(3, computed.stdout.count('admissionRejected=1 freshnessDisagreements=1'))
        self.assertIn('PRODUCTION_EVENTS_AGGREGATED_3_SCALES', computed.stdout)

    def test_auto_uses_v2_recorded_mask_including_rejected_samples(self):
        data = self.fixture()
        data['samples'][20][5] = False
        result = self.replay(data)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('mode=recorded-freshness', result.stdout)
        self.assertEqual(3, result.stdout.count('admissionRejected=1 freshnessDisagreements=1'))

    def test_v1_never_invents_receipt_time(self):
        result = self.replay(self.fixture(False))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('mode=detector-only', result.stdout)
        rejected = self.replay(self.fixture(False), '--mode', 'recomputed-freshness')
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn('V1 lacks receipt timestamps', rejected.stderr)

    def test_state_probe_is_opt_in_and_preserves_replay_and_admission(self):
        data = self.fixture()
        data['samples'][35][1] = 24.0
        data['samples'][36][4] += 300
        plain = self.replay(data, '--mode', 'recomputed-freshness')
        probe = self.replay(data, '--mode', 'recomputed-freshness', '--state')
        self.assertEqual(0, probe.returncode, probe.stderr)
        summaries = lambda text: [line for line in text.splitlines() if line.startswith('sensitivity=')]
        self.assertEqual(summaries(plain.stdout), summaries(probe.stdout))
        self.assertNotIn('STATE ', plain.stdout)
        self.assertIn('STATE ', probe.stdout)
        self.assertIn('arm=2.5', probe.stdout)
        self.assertIn('supportNs=', probe.stdout)
        self.assertIn('cooldownUntil=', probe.stdout)
        self.assertIn('Compiled detector class ChopDetector$StrokeScale.class SHA256=', probe.stdout)
        self.assertIn('admission=REJECTED', probe.stdout)

    def test_capture_checksum_mismatch_is_refused(self):
        result = self.replay(self.fixture(), '--expected-sha256', '0' * 64)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('checksum mismatch', result.stderr)

    def test_invalid_freshness_and_schema_are_refused(self):
        data = self.fixture()
        data['samples'][0][5] = 'true'
        result = self.replay(data)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Malformed receipt', result.stderr)
        data = self.fixture()
        data['captureColumns'][0] = 'unknown'
        self.assertNotEqual(0, self.replay(data).returncode)


if __name__ == '__main__':
    unittest.main()
