#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze P2 local evidence; no signing or publication. Run after all Gradle checks."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
import verify_finish

ROOT=verify_finish.ROOT
PROJECT=ROOT.parents[1] if ROOT.parent.name=='.worktrees' else ROOT
OUT=PROJECT/'artifacts/finish/readiness'
NS='{http://schemas.android.com/apk/res/android}'

def digest(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    classes=verify_finish.engine()
    assert classes==json.loads((PROJECT/'artifacts/finish/design/engine-base.json').read_text())
    for name in ['ChopDetector.kt','SampleFreshness.kt']:
        path='app/src/main/kotlin/fr/nauss/choptorch/'+name
        assert (ROOT/path).read_bytes()==subprocess.check_output(['git','show','5a77634:'+path],cwd=ROOT)
    apk=ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk'
    analyzer=Path(os.environ['ANDROID_HOME'])/'cmdline-tools/23.0/bin/apkanalyzer.bat'
    text=subprocess.check_output([str(analyzer),'manifest','print',str(apk)]).decode()
    manifest=ET.fromstring(text); app=manifest.find('application')
    assert manifest.get('package')=='fr.nauss.choptorch'
    assert manifest.get(NS+'versionCode')=='6' and manifest.get(NS+'versionName')=='0.6-beta.1'
    assert app.get(NS+'debuggable','false')=='false'
    assert manifest.find('uses-sdk').get(NS+'minSdkVersion')=='37'
    permissions=sorted(node.get(NS+'name') for node in manifest.findall('uses-permission'))
    assert permissions==sorted('android.permission.'+p for p in ['FOREGROUND_SERVICE','FOREGROUND_SERVICE_SPECIAL_USE','POST_NOTIFICATIONS','RECEIVE_BOOT_COMPLETED'])
    assert app.find('service').get(NS+'exported')=='false'
    receiver=app.find('receiver'); assert receiver.get(NS+'exported')=='false'
    assert {n.get(NS+'name') for n in receiver.findall('intent-filter/action')}=={'android.intent.action.BOOT_COMPLETED','android.intent.action.MY_PACKAGE_REPLACED'}
    forbidden=['Diagnostics','DiagnosticSample','TEST_TORCH','CAPTURE','ACTION_CREATE_DOCUMENT','openOutputStream','LifecycleInstrumentation']
    with zipfile.ZipFile(apk) as archive:
        dex=b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
        for token in forbidden: assert token.encode() not in dex,token
        assert not any(n.endswith(('.jks','.keystore','.p12','.pk8')) for n in archive.namelist())
    debug=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    assert subprocess.check_output([str(analyzer),'manifest','application-id',str(debug)]).decode().strip()=='fr.nauss.choptorch.diagnostic'
    tests=[ET.parse(p).getroot() for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
    assert tests and all(int(t.get('errors'))==0 and int(t.get('failures'))==0 for t in tests)
    for name in ['lint-results-debug.xml','lint-results-release.xml']:
        source=ROOT/'app/build/reports'/name
        assert not ET.parse(source).getroot().findall('issue')
        shutil.copy2(source,OUT/name)
    (OUT/'jvm-tests').mkdir(exist_ok=True)
    for source in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'): shutil.copy2(source,OUT/'jvm-tests'/source.name)
    wrapper={}
    for filename,url in [ ('gradle/wrapper/gradle-wrapper.jar','https://services.gradle.org/distributions/gradle-9.5.0-wrapper.jar.sha256') ]:
        expected=urllib.request.urlopen(url,timeout=30).read().decode().strip()
        assert digest(ROOT/filename)==expected
        wrapper[filename]=expected
    properties=(ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()
    expected=urllib.request.urlopen('https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256',timeout=30).read().decode().strip()
    assert 'distributionSha256Sum='+expected in properties
    wrapper['distribution']=expected
    for locale in ['en-US','fr-FR']:
        metadata=ROOT/'fastlane/metadata/android'/locale
        for name,maximum in [('title.txt',50),('short_description.txt',80),('full_description.txt',4000)]:
            assert len((metadata/name).read_text(encoding='utf-8').strip())<=maximum
        assert '0.6-beta.1' in (metadata/'changelogs/6.txt').read_text(encoding='utf-8')
        assert (metadata/'images/icon.png').is_file()
        assert len(list((metadata/'images/phoneScreenshots').glob('*.png')))>=2
    destination=OUT/'ChopTorch-0.6-beta.1-unsigned.apk'
    shutil.copy2(apk,destination)
    shutil.copy2(debug,OUT/'ChopTorch-0.6-beta.1-preview.apk')
    (OUT/'release-manifest.xml').write_text(text,encoding='utf-8')
    native_checks=json.loads((OUT/'native-final/checks.json').read_text())
    assert len(native_checks)==12
    instrumentation=(OUT/'21-instrumentation-green.log').read_text()
    assert 'PASS fresh/disabled' in instrumentation and 'FAIL' not in instrumentation and 'crashed' not in instrumentation
    for name in ['32-missing-torch-final.log','34-missing-sensor-final.log']:
        assert 'PASS real framework missing' in (OUT/name).read_text()
    assert 'PASS actual keytool + zipalign + apksigner verify' in (OUT/'26-signing-final.log').read_text()
    report={'revision':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),
        'native_lifecycle_checks':native_checks,'native_lifecycle_count':len(native_checks),
        'package':'fr.nauss.choptorch','version':'0.6-beta.1','versionCode':6,'futureTag':'v0.6-beta.1',
        'unsigned_apk':str(destination),'unsigned_sha256':digest(apk),'debug_sha256':digest(debug),
        'release_debuggable':False,'permissions':permissions,'jvm_tests':sum(int(t.get('tests')) for t in tests),
        'engine_classes_identical':classes,'wrapper_verified':wrapper,'release_forbidden_dex_tokens_absent':forbidden,
        'public_signing':'NOT PERFORMED; disposable signing self-test is not a release key',
        'physical_sleep_energy_endurance':'NOT MEASURED','publication':'NOT PERFORMED; independent review and landing required'}
    (OUT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__': main()
