#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""P1 local evidence: all detector classes, resources and production removals."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT.parents[1] / 'artifacts/finish/design' if ROOT.parent.name == '.worktrees' else ROOT / 'artifacts/finish/design'

def engine():
    folder = ROOT / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/fr/nauss/choptorch'
    files = sorted(list(folder.glob('ChopDetector*.class')) + list(folder.glob('SampleFreshness*.class')))
    assert len(files) > 2, 'Missing inner detector classes'
    return {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in files}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline', action='store_true')
    args = parser.parse_args()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    if args.baseline:
        target = EVIDENCE / 'engine-base.json'
        assert not target.exists(), 'Do not overwrite the baseline'
        target.write_text(json.dumps(engine(), indent=2))
        print(target)
        return
    baseline = json.loads((EVIDENCE / 'engine-base.json').read_text())
    actual = engine()
    assert actual == baseline, 'Compiled engine changed (including inner classes)'
    for name in ['ChopDetector.kt', 'SampleFreshness.kt']:
        path = 'app/src/main/kotlin/fr/nauss/choptorch/' + name
        original = subprocess.check_output(['git', 'show', '5a77634ec17762509ab529fd3bd0d8bd58bfc027:' + path], cwd=ROOT)
        assert (ROOT / path).read_bytes() == original, name + ' source changed'
    forbidden = ['Diagnostics', 'DiagnosticSample', 'TEST_TORCH', 'CAPTURE', 'ACTION_CREATE_DOCUMENT', 'openOutputStream']
    for f in (ROOT / 'app/src/main').rglob('*'):
        if f.is_file():
            text = f.read_text(encoding='utf-8')
            assert not any(token in text for token in forbidden), str(f)
    release = ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    with zipfile.ZipFile(release) as apk:
        dex = b''.join(apk.read(n) for n in apk.namelist() if n.endswith('.dex'))
        for token in forbidden:
            assert token.encode() not in dex, 'Forbidden DEX token ' + token
    report = {'engine_classes_identical': actual, 'source_identical_to': '5a77634ec17762509ab529fd3bd0d8bd58bfc027', 'production_and_release_dex_absence': forbidden, 'release_sha256': hashlib.sha256(release.read_bytes()).hexdigest()}
    sdk = Path(os.environ['ANDROID_HOME'])
    analyzer = sdk / 'cmdline-tools/23.0/bin/apkanalyzer.bat'
    manifest_text = subprocess.check_output([str(analyzer), 'manifest', 'print', str(release)]).decode('utf-8')
    manifest = ET.fromstring(manifest_text)
    ns = '{http://schemas.android.com/apk/res/android}'
    permissions = sorted(n.get(ns+'name') for n in manifest.findall('uses-permission'))
    assert permissions == sorted(['android.permission.FOREGROUND_SERVICE', 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE', 'android.permission.POST_NOTIFICATIONS'])
    assert manifest.find('application').get(ns+'debuggable', 'false') == 'false'
    assert manifest.get('package') == 'fr.nauss.choptorch.diagnostic'
    assert manifest.get(ns+'versionCode') == '5'
    (EVIDENCE / 'release-manifest.xml').write_text(manifest_text, encoding='utf-8')
    report['release_permissions'] = permissions
    report['release_debuggable'] = False
    results = [ET.parse(p).getroot() for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
    report['jvm_tests'] = sum(int(r.get('tests')) for r in results)
    assert results and all(int(r.get('failures')) == 0 and int(r.get('errors')) == 0 for r in results)
    for name in ['lint-results-debug.xml','lint-results-release.xml']:
        source=ROOT/'app/build/reports'/name
        assert not ET.parse(source).getroot().findall('issue'), name
        shutil.copy2(source, EVIDENCE/name)
    for source in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
        target=EVIDENCE/'jvm-tests'/source.name
        target.parent.mkdir(exist_ok=True)
        shutil.copy2(source,target)
    report['debug_sha256'] = hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
    report['revision'] = subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()
    (EVIDENCE / 'verification.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))

if __name__ == '__main__':
    main()
