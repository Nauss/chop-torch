#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise framework gesture Back on the owned AVD, including active-state round trip."""
import argparse
import json
from pathlib import Path
import time
import verify_native_ui as ui
from owned_emulator_guard import OwnedEmulator, bind_native_ui


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('evidence', type=Path)
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    out = args.evidence
    guard = OwnedEmulator.from_file(out / 'emulator-owner.json', args.serial,
        ui.ADB, 'ChopTorch_P1_API37')
    with bind_native_ui(ui, guard):
        exercise(out)


def exercise(out):
    ui.OUT = out / 'gesture-back'
    ui.OUT.mkdir(exist_ok=True)
    ui.shell('pm', 'clear', ui.PKG)
    ui.shell('pm', 'grant', ui.PKG, 'android.permission.POST_NOTIFICATIONS')
    ui.shell('cmd', 'locale', 'set-app-locales', ui.PKG, '--locales', 'en-US')
    ui.shell('settings', 'put', 'system', 'font_scale', '1.0')
    ui.shell('cmd', 'uimode', 'night', 'no')
    overlay = 'com.android.internal.systemui.navbar.gestural'
    assert overlay in ui.shell('cmd', 'overlay', 'list')
    ui.shell('cmd', 'overlay', 'enable-exclusive', '--category', overlay)
    try:
        time.sleep(1)
        assert ui.shell('settings', 'get', 'secure', 'navigation_mode').strip() == '2'
        ui.launch()
        ui.click('activation'); ui.click('CANCEL')
        ui.assert_text('Detection off')
        ui.click('activation'); ui.click('Enable')
        ui.assert_text('Detection active')
        before = ui.shell('run-as', ui.PKG, 'cat', 'shared_prefs/settings.xml')
        assert 'isForeground=true' in ui.shell('dumpsys', 'activity', 'services', ui.PKG)
        ui.click('help_button')
        ui.capture('help-before-gesture')
        # Drive the real edge gesture; Android dispatches predictive Back, no app callback stub.
        ui.shell('input', 'swipe', '1', '1200', '650', '1200', '650')
        time.sleep(1)
        windows = ui.shell('dumpsys', 'window')
        focus = '\n'.join(x for x in windows.splitlines() if 'mCurrentFocus=' in x)
        assert 'MainActivity' in focus, focus
        ui.assert_text('Detection active')
        assert before == ui.shell('run-as', ui.PKG, 'cat', 'shared_prefs/settings.xml')
        assert 'isForeground=true' in ui.shell('dumpsys', 'activity', 'services', ui.PKG)
        ui.capture('active-after-gesture')
        (out / 'gesture-back-result.json').write_text(json.dumps({'passed': True, 'navigation_mode': 2,
            'focus_after_edge_gesture': focus, 'activation_and_sensitivity_unchanged': True,
            'real_foreground_service_continues': True, 'consent_cancel_verified': True}, indent=2))
        ui.click('activation')
        ui.assert_text('Detection off')
        print('PASS actual edge gesture Back, live active service and preferences preserved, consent Cancel', flush=True)
    finally:
        ui.shell('cmd', 'overlay', 'disable', overlay)

if __name__ == '__main__':
    main()
