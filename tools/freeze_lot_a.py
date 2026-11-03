#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build and freeze the diagnostic APK with exact source provenance, never a release key.
Run from a clean committed checkout, with JDK 21 and ANDROID_HOME configured.
Only writes ignored artifacts/lot-a and Gradle build outputs in this checkout.
"""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'artifacts' / 'lot-a'
APK_NAME = 'ChopTorch-0.1-diagnostic.apk'
PERMISSIONS = {
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
    'android.permission.POST_NOTIFICATIONS',
}

def run(args: list[str], report: str | None = None, timeout: int = 600) -> str:
    result = subprocess.run(args, cwd=ROOT, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                            errors='replace', timeout=timeout, check=False)
    if report:
        (OUT / report).write_text(result.stdout, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(f'Command failed ({result.returncode}): {args}; see {report}')
    return result.stdout

def sha(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def main() -> None:
    os.chdir(ROOT)
    if Path(run(['git', 'rev-parse', '--show-toplevel']).strip()).resolve() != ROOT:
        raise RuntimeError('Not an independent repository root')
    if run(['git', 'status', '--porcelain']).strip():
        raise RuntimeError('Commit intended source files first; working tree must be clean')
    commit = run(['git', 'rev-parse', 'HEAD']).strip()
    OUT.mkdir(parents=True, exist_ok=True)
    if (OUT / APK_NAME).exists() or (OUT / 'manifest.json').exists():
        raise RuntimeError('Frozen delivery already exists; preserve it instead of overwriting')
    sdk = Path(os.environ['ANDROID_HOME'])
    bt = sdk / 'build-tools' / '37.0.0'
    aapt = str(bt / ('aapt.exe' if os.name == 'nt' else 'aapt'))
    apksigner = ['java', '-jar', str(bt / 'lib' / 'apksigner.jar')]
    gradle = ['java', '-classpath', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
              'org.gradle.wrapper.GradleWrapperMain']
    wrapper_sha = sha(ROOT / 'gradle/wrapper/gradle-wrapper.jar')
    if wrapper_sha != '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7':
        raise RuntimeError('Gradle 9.5.0 wrapper JAR hash mismatch')
    build = gradle + ['--console=plain', 'clean', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug']
    output = run(build, 'build-frozen.txt')
    if 'BUILD SUCCESSFUL' not in output or 'An API level of 37 is not supported' in output:
        raise RuntimeError('Build not accepted')
    original = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    badging = run([aapt, 'dump', 'badging', str(original)], 'apk-badging.txt')
    permissions = run([aapt, 'dump', 'permissions', str(original)], 'apk-permissions.txt')
    observed = set(re.findall(r"uses-permission: name='([^']+)'", permissions))
    if observed != PERMISSIONS:
        raise RuntimeError(f'Unexpected permissions: {observed}')
    for required in ["name='fr.nauss.choptorch.diagnostic'", "sdkVersion:'37'", "targetSdkVersion:'37'", "compileSdkVersion='37'", 'application-debuggable']:
        if required not in badging:
            raise RuntimeError(f'APK metadata mismatch: {required}')
    tree = run([aapt, 'dump', 'xmltree', str(original), 'AndroidManifest.xml'], 'apk-manifest-tree.txt')
    service = tree.split('E: service', 1)[1]
    if not re.search(r'android:exported[^\n]+=\(type 0x12\)0x0', service):
        raise RuntimeError('Service must be unexported')
    if '0x40000000' not in service or 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' not in service:
        raise RuntimeError('Missing specialUse type/subtype')
    signature = run(apksigner + ['verify', '--verbose', '--print-certs', str(original)], 'signature.txt')
    if 'Verifies' not in signature or 'CN=Android Debug' not in signature:
        raise RuntimeError('Expected development signature, not a production release')
    suites = [ET.parse(p).getroot() for p in (ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
    totals = {key: sum(int(s.get(key, '0')) for s in suites) for key in ['tests', 'failures', 'errors', 'skipped']}
    if totals['tests'] < 15 or any(totals[k] for k in ['failures', 'errors', 'skipped']):
        raise RuntimeError(f'Tests not accepted: {totals}')
    lint_file = ROOT / 'app/build/reports/lint-results-debug.xml'
    if ET.parse(lint_file).getroot().findall('issue'):
        raise RuntimeError('Lint contains findings')
    for dirname in ['unit-tests', 'lint']:
        (OUT / dirname).mkdir(exist_ok=True)
    for p in (ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
        shutil.copy2(p, OUT / 'unit-tests' / p.name)
    for p in (ROOT / 'app/build/reports').glob('lint-results-debug.*'):
        shutil.copy2(p, OUT / 'lint' / p.name)
    run(gradle + ['--version'], 'gradle-version.txt')
    run(gradle + ['--console=plain', 'buildEnvironment'], 'toolchain-dependencies.txt')
    run(gradle + ['--console=plain', ':app:dependencies', '--configuration', 'debugRuntimeClasspath'], 'runtime-dependencies.txt')
    java_version = run(['java', '-version'], 'java-version.txt').strip()
    adb = sdk / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')
    devices = run([str(adb), 'devices'], 'adb-devices.txt') if adb.exists() else 'ADB absent'
    if run(['git', 'status', '--porcelain']).strip() or run(['git', 'rev-parse', 'HEAD']).strip() != commit:
        raise RuntimeError('Source changed during verification')
    tracked = run(['git', 'ls-files']).splitlines()
    source = {p: sha(ROOT / p) for p in tracked}
    (OUT / 'source-files-sha256.json').write_text(json.dumps(source, indent=2), encoding='utf-8')
    frozen = OUT / APK_NAME
    shutil.copy2(original, frozen)
    digest = sha(frozen)
    certificate = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)', signature)
    manifest = {
        'format': 'choptorch-lot-a-v1', 'createdUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'sourceCommit': commit, 'sourceBranch': run(['git', 'branch', '--show-current']).strip(),
        'apk': APK_NAME, 'sha256': digest, 'sizeBytes': frozen.stat().st_size,
        'applicationId': 'fr.nauss.choptorch.diagnostic', 'versionCode': 1, 'versionName': '0.1-diagnostic',
        'minSdk': 37, 'targetSdk': 37, 'compileSdk': 37, 'sdkPlatformRevision': 2,
        'buildTools': '37.0.0', 'agp': '9.3.3', 'gradle': '9.5.0', 'kotlinBuiltIn': '2.2.10',
        'gradleDistributionSha256': '553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746',
        'wrapperJarSha256': wrapper_sha, 'java': java_version,
        'buildCommand': build, 'tests': totals, 'testData': 'SYNTHETIC', 'lintIssues': 0,
        'permissions': sorted(observed), 'signature': 'Android Debug; APK v2 verification passed; NOT production signing',
        'certificateSha256': certificate.group(1) if certificate else None,
        'adbDevicesAtBuild': devices.strip(), 'installedOnDevice': False, 'review': 'PENDING independent Kanban review',
        'limitations': ['No physical Pixel test', 'Screen-off/deep-sleep support unverified',
            'No battery or 72h endurance measurement', 'Experimental uncalibrated detector',
            'No automatic restart after process death, boot or update', 'Not distributed through F-Droid',
            'Development signature, distinct diagnostic ID', 'Raw captures limited to voluntary 15s/1000 samples in RAM'],
    }
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    (OUT / 'SHA256SUMS.txt').write_text(f'{digest}  {APK_NAME}\n', encoding='utf-8')
    print(json.dumps({k: manifest[k] for k in ['sourceCommit','apk','sha256','sizeBytes','tests','lintIssues']}, indent=2))
    print(f'Frozen delivery: {OUT}')

if __name__ == '__main__':
    main()
