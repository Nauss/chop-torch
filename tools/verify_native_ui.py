#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Native UI evidence on the disposable ChopTorch emulator only, never a personal phone.
Requires an already running API37 AVD. Uses adb input + UI Automator, no web renderer.
"""
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT.parents[1] / 'artifacts/finish/design' if ROOT.parent.name == '.worktrees' else ROOT / 'artifacts/finish/design'
ADB = str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb.exe')
SERIAL = 'emulator-5554'
PKG = 'fr.nauss.choptorch.diagnostic'

def adb(*args, binary=False):
    result = subprocess.check_output([ADB, '-s', SERIAL, *args])
    return result if binary else result.decode('utf-8', errors='replace')

def shell(*args): return adb('shell', *args)
def launch():
    shell('am', 'start', '-W', '-n', PKG + '/fr.nauss.choptorch.MainActivity')
    time.sleep(0.8)

def tree():
    shell('uiautomator', 'dump', '/data/local/tmp/choptorch-ui.xml')
    return ET.fromstring(shell('cat', '/data/local/tmp/choptorch-ui.xml'))

def click(key):
    for node in tree().iter('node'):
        if node.get('resource-id') == PKG + ':id/' + key or node.get('text','').casefold().replace('’', "'") == key.casefold().replace('’', "'"):
            x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds')))
            shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
            time.sleep(0.5)
            return
    raise AssertionError('Control not found: ' + key)

def capture(name):
    windows = shell('dumpsys','window')
    policy = shell('dumpsys','window','policy')
    assert 'mCurrentFocus=Window' in windows and any(PKG in line for line in windows.splitlines() if 'mCurrentFocus=' in line)
    assert 'showing=true' not in '\n'.join(line for line in policy.splitlines() if 'keyguard' in line.lower())
    assert 'mShowingLockscreen=true' not in windows
    image = adb('exec-out','screencap','-p',binary=True)
    assert image.startswith(b'\x89PNG\r\n\x1a\n'), 'Native screenshot failed: ' + repr(image[:160])
    (OUT / (name+'.png')).write_bytes(image)
    (OUT / (name+'.xml')).write_bytes(ET.tostring(tree(), encoding='utf-8'))
    (OUT / (name+'-config.txt')).write_text('\n'.join([
        adb('emu','avd','name'), shell('getprop','ro.build.fingerprint'),
        shell('cmd','uimode','night'), shell('settings','get','system','font_scale'),
        shell('cmd','locale','get-app-locales',PKG), shell('wm','size'), shell('wm','density'),
        '\n'.join(line for line in windows.splitlines() if 'mCurrentFocus=' in line or 'mShowingLockscreen=' in line)
    ]), encoding='utf-8')
    print('Captured native emulator:',name,flush=True)

def assert_text(value):
    assert any(value in node.get('text','') for node in tree().iter('node')), value

def sensitivity_value():
    return next(node.get('text') for node in tree().iter('node') if node.get('resource-id') == PKG + ':id/sensitivity_value')

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    assert 'ChopTorch_P1_API37' in adb('emu','avd','name'), 'Only disposable AVD allowed'
    assert int(shell('getprop','ro.build.version.sdk').strip()) == 37
    deadline=time.time()+180
    while shell('getprop','sys.boot_completed').strip() != '1':
        assert time.time() < deadline, 'Boot timeout'
        time.sleep(2)
    adb('install','-r',str(ROOT/'app/build/outputs/apk/debug/app-debug.apk'))
    shell('pm','clear',PKG)
    shell('cmd','locale','set-app-locales',PKG,'--locales','en-US')
    shell('cmd','uimode','night','no')
    shell('settings','put','system','font_scale','1.0')
    # Cold start after changing the prior run's per-app locale; do not inspect
    # an activity created with the previous configuration during replacement.
    shell('am','force-stop',PKG)
    launch()
    assert_text('Detection off')
    capture('01-light-en-off')
    click('activation'); click('CANCEL')
    assert_text('Detection off')
    click('activation'); click('Enable')
    click('Don’t allow')
    assert_text('Allow notifications')
    capture('02-permission-denied')
    # Grant only on this disposable AVD, not the owner's device.
    shell('pm','grant',PKG,'android.permission.POST_NOTIFICATIONS')
    launch()
    click('activation'); click('Enable')
    assert_text('Detection active')
    capture('03-light-en-active')
    for _ in range(8):
        if any(n.get('resource-id') == PKG + ':id/sensitivity' and n.get('focused') == 'true' for n in tree().iter('node')):
            break
        shell('input','keyevent','KEYCODE_TAB')
    else:
        raise AssertionError('SeekBar not reachable by keyboard')
    shell('input','keyevent','KEYCODE_DPAD_RIGHT')
    shell('input','keyevent','KEYCODE_DPAD_RIGHT')
    shell('input','keyevent','KEYCODE_DPAD_RIGHT')
    changed_value = sensitivity_value()
    assert changed_value != '50 / 100', 'Keyboard failed to change sensitivity'
    print('Keyboard sensitivity:', changed_value, flush=True)
    click('activation')
    assert_text('Detection off')
    shell('am','force-stop',PKG)
    launch()
    assert_text(changed_value)
    shell('cmd','uimode','night','yes')
    launch()
    capture('04-dark-en-off')
    click('help_button')
    capture('05-dark-en-help')
    shell('input','swipe','500','1700','500','500','400')
    capture('06-dark-en-help-lower')
    # Emulated global settings are disposable and restored below.
    shell('settings','put','system','font_scale','1.3')
    shell('am','force-stop',PKG)
    launch()
    capture('07-dark-en-font130')
    shell('settings','put','system','accelerometer_rotation','0')
    shell('settings','put','system','user_rotation','1')
    time.sleep(1)
    capture('08-landscape-font130')
    shell('settings','put','system','user_rotation','0')
    shell('settings','put','system','font_scale','1.0')
    shell('cmd','uimode','night','no')
    launch()
    shell('cmd','locale','set-app-locales',PKG,'--locales','fr-FR')
    launch()
    assert_text('Détection désactivée')
    capture('09-light-fr-off')
    shell('cmd','uimode','night','yes')
    launch()
    capture('10-dark-fr-off')
    shell('settings','put','system','font_scale','2.0')
    launch()
    capture('11-dark-fr-font200')
    shell('input','swipe','500','1800','500','500','400')
    capture('12-dark-fr-font200-lower')
    shell('settings','put','system','font_scale','1.0')
    shell('wm','size','1600x2560')
    shell('wm','density','240')
    launch()
    capture('13-tablet-fr-dark')
    shell('wm','size','reset')
    shell('wm','density','reset')
    shell('cmd','uimode','night','no')
    launch()
    print('PASS: off, cancel, permission denied, active, keyboard sensitivity persisted, stop, FR/EN, light/dark/help/font130/font200/rotation/tablet. No real gesture or battery claim.')

if __name__=='__main__': main()
