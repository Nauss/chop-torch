#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze B3 (0.4/versionCode4) into a durable directory, never overwrite a delivery.
Requires a clean committed checkout. Does not install, toggle LEDs or publish APKs.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
from freeze_lot_a import PERMISSIONS, sha

ROOT = Path(__file__).resolve().parents[1]
CERT = '530f4619a4caaacb3a1c1fcce20e8e01d85bc78482b915f90e176c58134ad2ae'
APK_NAME = 'ChopTorch-0.4-diagnostic.apk'

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--previous-apk', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    # Keep the existing B3 investigation AND the new private analysis separate.
    if (out.name != 'delivery-v04' or out.parent.name != 'lot-b3' or
            out.parent.parent.name != 'artifacts' or ROOT in out.parents):
        raise ValueError('Output must be a durable artifacts/lot-b3/delivery-v04 outside the build worktree')
    if out.exists():
        raise RuntimeError('Frozen B3 delivery directory already exists; refusing overwrite')
    out.mkdir(parents=True, exist_ok=True)
    def run(command: list[str], report: str | None = None) -> str:
        result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                encoding='utf-8', errors='replace', timeout=900)
        if report:
            (out / report).write_text(result.stdout, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(f'Command failed ({result.returncode}): {command}; report={report}')
        return result.stdout
    if run(['git', 'status', '--porcelain']).strip():
        raise RuntimeError('Commit all intended source changes first')
    commit = run(['git', 'rev-parse', 'HEAD']).strip()
    sdk = Path(os.environ['ANDROID_HOME'])
    bt = sdk / 'build-tools/37.0.0'
    signer = ['java', '-jar', str(bt / 'lib/apksigner.jar')]
    old = run(signer + ['verify', '--verbose', '--print-certs', str(args.previous_apk.resolve())], 'previous-signature.txt')
    if f'certificate SHA-256 digest: {CERT}' not in old or 'Verifies' not in old:
        raise RuntimeError('Previous APK does not match the authorized development certificate')
    gradle = ['java', '-classpath', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'), 'org.gradle.wrapper.GradleWrapperMain']
    build = gradle + ['--offline', '--console=plain', 'clean', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug']
    result = run(build, 'build-frozen.txt')
    if 'BUILD SUCCESSFUL' not in result or 'An API level of 37 is not supported' in result:
        raise RuntimeError('Build not accepted')
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    aapt = str(bt / ('aapt.exe' if os.name == 'nt' else 'aapt'))
    badging = run([aapt, 'dump', 'badging', str(apk)], 'apk-badging.txt')
    for value in ["name='fr.nauss.choptorch.diagnostic'", "versionCode='4'", "versionName='0.4-diagnostic'", "sdkVersion:'37'", "targetSdkVersion:'37'", "compileSdkVersion='37'", 'application-debuggable']:
        if value not in badging:
            raise RuntimeError('APK metadata mismatch: ' + value)
    permissions = run([aapt, 'dump', 'permissions', str(apk)], 'apk-permissions.txt')
    observed = set(re.findall(r"uses-permission: name='([^']+)'", permissions))
    if observed != PERMISSIONS:
        raise RuntimeError('Unexpected APK permissions')
    tree = run([aapt, 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], 'apk-manifest-tree.txt')
    service = tree.split('E: service', 1)[1]
    if not re.search(r'android:exported[^\n]+=\(type 0x12\)0x0', service):
        raise RuntimeError('Service must be unexported')
    if '0x40000000' not in service or 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' not in service:
        raise RuntimeError('Missing specialUse subtype')
    signature = run(signer + ['verify', '--verbose', '--print-certs', str(apk)], 'signature.txt')
    if 'Verifies' not in signature or f'certificate SHA-256 digest: {CERT}' not in signature:
        raise RuntimeError('Signature differs from installed development APK; DO NOT install')
    suites = [ET.parse(p).getroot() for p in (ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
    totals = {k: sum(int(s.get(k, '0')) for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
    if totals['tests'] < 51 or any(totals[k] for k in ['failures', 'errors', 'skipped']):
        raise RuntimeError(f'Tests not accepted: {totals}')
    if ET.parse(ROOT / 'app/build/reports/lint-results-debug.xml').getroot().findall('issue'):
        raise RuntimeError('Lint findings remain')
    tool_result = run(['python', '-m', 'unittest', 'discover', '-s', 'tools', '-p', 'test_*.py', '-v'], 'tool-tests.txt')
    tool_count = re.search(r'Ran (\d+) tests', tool_result)
    if not tool_count or int(tool_count[1]) < 10 or not tool_result.rstrip().endswith('OK'):
        raise RuntimeError('Replay tool tests not accepted')
    if run(['git', 'status', '--porcelain']).strip() or run(['git', 'rev-parse', 'HEAD']).strip() != commit:
        raise RuntimeError('Source changed during build')
    for target, source, pattern in [('unit-tests', 'app/build/test-results/testDebugUnitTest', 'TEST-*.xml'), ('lint', 'app/build/reports', 'lint-results-debug.*')]:
        (out / target).mkdir(exist_ok=True)
        for path in (ROOT / source).glob(pattern):
            shutil.copy2(path, out / target / path.name)
    source_hashes = {name: sha(ROOT / name) for name in run(['git', 'ls-files']).splitlines()}
    (out / 'source-files-sha256.json').write_text(json.dumps(source_hashes, indent=2) + '\n', encoding='utf-8')
    frozen = out / APK_NAME
    shutil.copy2(apk, frozen)
    manifest = {
        'format': 'choptorch-lot-b3-v1', 'createdUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'sourceCommit': commit, 'sourceBranch': run(['git', 'branch', '--show-current']).strip(),
        'apk': APK_NAME, 'sha256': sha(frozen), 'sizeBytes': frozen.stat().st_size,
        'applicationId': 'fr.nauss.choptorch.diagnostic', 'versionCode': 4, 'versionName': '0.4-diagnostic',
        'minSdk': 37, 'targetSdk': 37, 'compileSdk': 37, 'certificateSha256': CERT,
        'previousApkSha256': sha(args.previous_apk), 'sameCertificateAsPrevious': True,
        'tests': totals, 'testData': 'SYNTHETIC', 'lintIssues': 0, 'permissions': sorted(observed),
        'buildCommand': build, 'review': 'PENDING independent Roger Kanban review', 'installedOnDevice': False,
        'limitations': ['Private isolated-miss and normal/rapid replays are offline calibration, NOT new APK physical validation',
                        'SampleFreshness cannot be replayed on v1 receipt-less export',
                        'No corrected APK physical test, daily false-positive, deep-sleep, battery or endurance validation',
                        'Manual restart after update; development certificate, diagnostic application ID'],
    }
    for source in ['VALIDATION_LOT_B3.md', 'TEST_B3.md']:
        shutil.copy2(ROOT / 'docs' / source, out / source)
    manifest['toolTests'] = {'tests': int(tool_count[1]), 'failures': 0, 'data': 'SYNTHETIC_EXPORTS_ONLY'}
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    (out / 'SHA256SUMS.txt').write_text(f'{manifest["sha256"]}  {APK_NAME}\n', encoding='utf-8')
    print(json.dumps(manifest, indent=2, ensure_ascii=False))

if __name__ == '__main__':
    main()
