#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Real API37 lifecycle tests on the explicitly disposable P1 AVD ONLY.
Reboots this AVD, never discovers/selects a physical phone. No test commands in APK.
"""
import json
import os
from pathlib import Path
import time
import verify_native_ui as ui

ROOT=ui.ROOT
OUT=Path(os.environ.get('CHOPTORCH_UI_EVIDENCE',str(ROOT.parents[1]/'artifacts/finish/readiness/native')))
ui.OUT=OUT
checks=[]

def record(name):
    checks.append(name); print('PASS '+name,flush=True)
    (OUT/'checks.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')

def running():
    dump=ui.shell('dumpsys','activity','services',ui.PKG)
    return 'isForeground=true' in dump

def await_state(value,timeout=60):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if running()==value: return
        time.sleep(.5)
    raise AssertionError('Expected foreground='+str(value)+'\n'+ui.shell('dumpsys','activity','services',ui.PKG))

def enabled():
    text=ui.shell('run-as',ui.PKG,'cat','shared_prefs/settings.xml')
    return 'name="enabled" value="true"' in text

def enable():
    ui.launch(); ui.click('activation'); ui.click('Enable'); await_state(True)
    assert enabled()

def reboot():
    ui.adb('reboot'); ui.adb('wait-for-device')
    deadline=time.monotonic()+180
    while ui.shell('getprop','sys.boot_completed').strip()!='1':
        assert time.monotonic()<deadline
        time.sleep(1)
    ui.shell('input','keyevent','KEYCODE_WAKEUP'); ui.shell('input','keyevent','82')
    time.sleep(3)

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    assert 'ChopTorch_P1_API37' in ui.adb('emu','avd','name')
    assert ui.shell('getprop','ro.boot.qemu').strip()=='1'
    assert ui.shell('getprop','ro.build.version.sdk').strip()=='37'
    apk=str(ROOT/'app/build/outputs/apk/debug/app-debug.apk')
    ui.adb('install','-r',apk); ui.shell('pm','clear',ui.PKG)
    ui.shell('cmd','locale','set-app-locales',ui.PKG,'--locales','en-US')
    ui.shell('settings','put','system','font_scale','1.0')
    ui.shell('cmd','uimode','night','no'); ui.launch(); await_state(False)
    ui.assert_text('Detection off'); record('fresh install off')
    ui.click('activation'); ui.click('Enable'); ui.click('Don’t allow')
    ui.assert_text('Allow notifications'); await_state(False); record('notification refusal leaves service off')
    ui.capture('permission-denied')
    ui.shell('pm','grant',ui.PKG,'android.permission.POST_NOTIFICATIONS')
    enable(); ui.capture('enabled'); record('explicit activation with real sensor and torch callback')
    ui.shell('input','keyevent','KEYCODE_HOME')
    old=ui.shell('pidof',ui.PKG).strip()
    ui.shell('run-as',ui.PKG,'/system/bin/kill','-9',old)
    deadline=time.monotonic()+90
    new=''
    while time.monotonic()<deadline:
        # AMS can retain isForeground on a dead ServiceRecord while restart is pending.
        # Require a new live PID, not merely that stale bookkeeping flag.
        import subprocess
        candidate=subprocess.run([ui.ADB,'-s',ui.SERIAL,'shell','pidof',ui.PKG],
            stdout=subprocess.PIPE,check=False).stdout.decode().strip()
        if candidate and candidate!=old and running():
            new=candidate; break
        time.sleep(.5)
    assert new, 'No live replacement PID after SIGKILL'
    ui.launch(); ui.assert_text('Detection active'); ui.shell('input','keyevent','KEYCODE_HOME')
    record('SIGKILL process loss: system sticky null-intent recovery, new PID')
    ui.adb('install','-r',apk); time.sleep(2); await_state(True)
    assert enabled(); record('real package replacement restores enabled service')
    reboot(); await_state(True); assert enabled()
    record('real emulator reboot restores after boot/unlock')
    ui.launch(); ui.click('activation'); await_state(False); assert not enabled()
    ui.adb('install','-r',apk); time.sleep(2); await_state(False)
    record('explicit UI stop survives real package replacement')
    reboot(); await_state(False); assert not enabled(); record('disabled app stays disabled after real reboot')
    enable(); ui.shell('cmd','activity','stop-app',ui.PKG); time.sleep(1); await_state(False)
    ui.adb('install','-r',apk); time.sleep(3); await_state(False)
    assert not enabled(); record('task-manager stop suppresses saved intent on replacement')
    enable(); ui.shell('am','force-stop',ui.PKG); time.sleep(1); await_state(False)
    ui.launch(); ui.assert_text('Detection off'); assert not enabled()
    record('force-stop plus manual reopen does not reactivate')
    enable(); ui.shell('pm','revoke',ui.PKG,'android.permission.POST_NOTIFICATIONS')
    time.sleep(2); await_state(False)
    ui.adb('install','-r',apk); time.sleep(2); await_state(False)
    ui.shell('pm','grant',ui.PKG,'android.permission.POST_NOTIFICATIONS')
    ui.adb('install','-r',apk); time.sleep(2); await_state(False)
    assert not enabled(); record('revocation then permission restoration never invents consent')
    ui.shell('cmd','appops','set',ui.PKG,'RUN_ANY_IN_BACKGROUND','ignore')
    try:
        ui.launch(); ui.click('activation'); ui.click('Enable'); await_state(False)
        ui.assert_text('Android restricts'); assert not enabled()
        ui.capture('restricted'); record('real background restriction refuses activation with truthful UI')
    finally: ui.shell('cmd','appops','set',ui.PKG,'RUN_ANY_IN_BACKGROUND','allow')
    print('All lifecycle checks passed; not a physical sleep/energy/endurance test.',flush=True)

if __name__=='__main__': main()
