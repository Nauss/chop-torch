#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify retained accepted sources and compiled detector against supplied evidence."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    out = Path(sys.argv[1])
    baseline_path = out.parent / 'baseline-source-sha256.json'
    reference_path = out.parents[1] / 'sensitivity-next/author/verification.json'
    baseline = json.loads(baseline_path.read_text())
    reference = json.loads(reference_path.read_text())['compiled_detector_sha256']
    core_keys = [p for p in baseline if p.startswith('app/src/main/kotlin/') and not p.endswith('MainActivity.kt')]
    core = {p: {'baseline': baseline[p], 'current': sha(ROOT / p)} for p in core_keys}
    assert len(core) == 8 and all(v['baseline'] == v['current'] for v in core.values())
    accepted = {p: {'baseline': baseline[p], 'current': sha(ROOT / p)} for p in baseline
                if p in ('tools/ReplayCapture.java', 'tools/test_replay.py',
                         'app/src/test/kotlin/fr/nauss/choptorch/LightSensitivityTest.kt')}
    assert len(accepted) == 3 and all(v['baseline'] == v['current'] for v in accepted.values())
    compiled, disassembly = {}, {}
    for variant in ('debug', 'release'):
        folder = ROOT / f'app/build/intermediates/built_in_kotlinc/{variant}/compile{variant.title()}Kotlin/classes/fr/nauss/choptorch'
        compiled[variant] = {f.name: sha(f) for f in folder.glob('ChopDetector*.class')}
        assert set(compiled[variant]) == set(reference)
        disassembly[variant] = {}
        for name in reference:
            text = subprocess.check_output(['javap', '-p', '-c', str(folder / name)]).decode(errors='replace')
            # JVM constant pool indices are variant-specific; resolved symbolic operands remain.
            normalized = re.sub(r'#\d+', '#CP', text)
            disassembly[variant][name] = normalized
            (out / f'{variant}-{name}.javap.txt').write_text(text)
    assert compiled['debug'] == reference
    assert disassembly['debug'] == disassembly['release']
    suites = [ET.parse(f).getroot() for f in (ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
    tests = {key: sum(int(t.get(key, '0')) for t in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert tests == {'tests': 65, 'failures': 0, 'errors': 0, 'skipped': 0}
    report = {'core_sources': core, 'accepted_sensitivity_files': accepted,
        'compiled_detector_sha256': compiled, 'compiled_reference_sha256': reference,
        'debug_all_four_classes_match_reference': True,
        'release_vs_debug_executable_disassembly_equal_after_constant_pool_index_normalization': True,
        'release_hash_note': 'ChopDetector and StrokeScale have different Kotlin debug local-variable metadata; raw hashes are not equal to the supplied debug reference. javap -p -c symbolic instructions match in all four classes.',
        'jvm': tests}
    (out / 'core-hashes.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))

if __name__ == '__main__':
    main()
