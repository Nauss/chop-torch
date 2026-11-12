#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Native matrix on the already-owned disposable emulator (never auto-select a device)."""
import argparse
import itertools
import json
import os
from pathlib import Path
import time
from owned_emulator_guard import OwnedEmulator

ROOT = Path(__file__).resolve().parents[1]
PKG = 'fr.nauss.choptorch.diagnostic'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    out = args.evidence
    guard = OwnedEmulator.from_file(out / 'emulator-owner.json', args.serial,
        Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb.exe', 'ChopTorch_P1_API37')
    adb = guard.text
    results = []
    for lang, scale, night in itertools.product(('fr-FR', 'en-US'), ('1.0', '2.0'), ('no', 'yes')):
        label = f'{lang}-font{scale}-{night}'
        adb('shell', 'pm', 'clear', PKG)
        adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')
        adb('shell', 'cmd', 'locale', 'set-app-locales', PKG, '--locales', lang)
        adb('shell', 'settings', 'put', 'system', 'font_scale', scale)
        adb('shell', 'cmd', 'uimode', 'night', night)
        time.sleep(1)
        text = adb('shell', 'am', 'instrument', '-w', '-e', 'suite', 'ui',
                   PKG + '.test/fr.nauss.choptorch.LifecycleInstrumentation')
        (out / (label + '.log')).write_text(text)
        (out / ('screenshots/' + label)).mkdir(parents=True, exist_ok=True)
        adb('pull', '/sdcard/Android/data/' + PKG + '/files/.', str(out / ('screenshots/' + label)))
        expected = f'{lang[:2]}-{scale}-{16 if night == "no" else 32}'
        result = {'configuration': label, 'runtime_tag': expected, 'passed': 'PASS native UI regression' in text and expected in text and 'FAIL' not in text}
        results.append(result)
        (out / 'ui-matrix.json').write_text(json.dumps(results, indent=2))
        print(label + '\n' + text, flush=True)
        assert result['passed'], label
    assert len(results) == 8

if __name__ == '__main__':
    main()
