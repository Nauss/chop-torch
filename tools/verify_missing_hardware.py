#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Missing hardware integration: separately configured disposable API37 AVD, port5556."""
import argparse
import time
import verify_native_ui as ui

def main():
    args=argparse.ArgumentParser(); args.add_argument('--missing',choices=['sensor','torch'],required=True); args=args.parse_args()
    ui.SERIAL='emulator-5556'
    ui.adb('wait-for-device')
    assert 'ChopTorch_P2_Hardware' in ui.adb('emu','avd','name')
    deadline=time.monotonic()+180
    while ui.shell('getprop','sys.boot_completed').strip()!='1':
        assert time.monotonic()<deadline; time.sleep(1)
    assert ui.shell('getprop','ro.boot.qemu').strip()=='1'
    ui.adb('install','-r',str(ui.ROOT/'app/build/outputs/apk/debug/app-debug.apk'))
    ui.shell('pm','clear',ui.PKG)
    ui.shell('pm','grant',ui.PKG,'android.permission.POST_NOTIFICATIONS')
    ui.shell('input','keyevent','KEYCODE_WAKEUP'); ui.shell('input','keyevent','82')
    ui.launch(); ui.click('activation'); ui.click('Enable'); time.sleep(1)
    ui.assert_text('No accelerometer available' if args.missing=='sensor' else 'No flashlight available')
    ui.assert_text('Detection off')
    assert 'isForeground=true' not in ui.shell('dumpsys','activity','services',ui.PKG)
    assert 'name="enabled" value="true"' not in ui.shell('run-as',ui.PKG,'cat','shared_prefs/settings.xml')
    print('PASS real framework missing '+args.missing+': truthful error, service off, consent cleared, no retry')

if __name__=='__main__': main()
