#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Local PRIVATE capture replay; no capture is bundled or sent over the network.
Build :app:compileDebugKotlin first. Supply an explicit JSON export path.
Output includes personal detection times: redirect outside Git.
Modes are explicit. V2 defaults to recorded admission; v1 lacks receipt timestamps.
Per-line sensitivity is validated but overridden with fixed 0/50/100 for comparison.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--expected-sha256')
    parser.add_argument('--detail', action='store_true')
    parser.add_argument('--mode', choices=['auto', 'detector-only', 'recorded-freshness', 'recomputed-freshness'], default='auto')
    args = parser.parse_args()
    raw = args.capture.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if args.expected_sha256 and digest != args.expected_sha256:
        raise ValueError('Capture checksum mismatch; original was not modified by this tool')
    data = json.loads(raw)
    expected = ['relative_ms', 'raw_x_m_s2', 'raw_y_m_s2', 'raw_z_m_s2']
    if data.get('captureColumns', [])[:4] != expected:
        raise ValueError('Unknown capture schema')
    if data.get('format') not in ('choptorch-diagnostic-v1', 'choptorch-diagnostic-v2'):
        raise ValueError('Unknown export format')
    samples = data['samples']
    v2 = data['format'] == 'choptorch-diagnostic-v2'
    mode = ('recorded-freshness' if v2 else 'detector-only') if args.mode == 'auto' else args.mode
    if mode != 'detector-only' and not v2:
        raise ValueError('V1 lacks receipt timestamps; freshness cannot be inferred')
    if v2 and data['captureColumns'] != expected + ['received_relative_ms', 'freshness_accepted', 'sensitivity']:
        raise ValueError('Unknown v2 columns')
    for row in samples:
        if len(row) != len(data['captureColumns']) or any(type(x) not in (int, float) or not math.isfinite(x) for x in row[:4]):
            raise ValueError('Malformed capture sample')
        if v2 and (type(row[4]) not in (int, float) or not math.isfinite(row[4]) or type(row[5]) is not bool or type(row[6]) is not int or not 0 <= row[6] <= 100):
            raise ValueError('Malformed receipt/admission/sensitivity data')
    classes = ROOT / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
    if not (classes / 'fr/nauss/choptorch/ChopDetector.class').exists():
        raise RuntimeError('Compile the production Kotlin detector first')
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    jars = list((cache / 'caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.2.10').glob('*/*.jar'))
    if len(jars) != 1:
        raise RuntimeError('Expected pinned Kotlin stdlib 2.2.10 in Gradle cache')
    # A temporary CSV is derived, in order, from the original JSON, never edited.
    scratch = Path(os.environ.get('TMPDIR', str(ROOT / '.ops')))
    scratch.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='choptorch-private-', dir=scratch) as temp:
        csv = Path(temp) / 'samples.csv'
        csv.write_text(''.join(','.join(str(v).lower() if type(v) is bool else str(v) for v in row) + '\n' for row in samples), encoding='utf-8')
        command = ['java', '-cp', os.pathsep.join([str(classes), str(jars[0])]), str(ROOT / 'tools/ReplayCapture.java'), str(csv), mode]
        if args.detail:
            command.append('--detail')
        print('PRIVATE UNANNOTATED capture; sha256=' + digest, flush=True)
        print('Replay at fixed sensitivities 0/50/100; mode=' + mode + '; per-sample sensitivity is validated but deliberately overridden.', flush=True)
        print('Compiled detector SHA256=' + hashlib.sha256((classes / 'fr/nauss/choptorch/ChopDetector.class').read_bytes()).hexdigest(), flush=True)
        subprocess.run(command, check=True, cwd=ROOT)
    if hashlib.sha256(args.capture.read_bytes()).hexdigest() != digest:
        raise RuntimeError('Original changed during replay')

if __name__ == '__main__':
    main()
