#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Native scroll regression on the disposable API37 AVD; no phone mutations."""
import json
import re
import time
PKG = 'fr.nauss.choptorch.diagnostic'


def bounds(value):
    result = tuple(map(int, re.findall(r'-?\d+', value)))
    assert len(result) == 4, value
    return result


def intersects(a, b):
    return a[0] < b[2] and a[2] > b[0] and a[1] < b[3] and a[3] > b[1]


def assert_safe_viewport(root, windows):
    """Use actual visible system-window frames, not fixed status-bar heights.

    ScrollView's physical viewport AND its visible accessibility descendants
    must not intersect either system bar. Insets are outside the scrolling view.
    This intentionally rejects an edge-to-edge scrolling viewport with padding.
    """
    bars = []
    for block in re.split(r'\n  Window #', windows):
        if not re.search(r'ty=(?:STATUS_BAR|NAVIGATION_BAR)\b', block):
            continue
        if 'isVisible=true' not in block:
            continue
        match = re.search(r'Frames:.*? frame=(\[-?\d+,-?\d+\]\[-?\d+,-?\d+\])', block)
        assert match, 'Missing visible system bar frame'
        bars.append(bounds(match.group(1)))
    assert len(bars) >= 2, 'Expected visible status and navigation bars'
    scroll = next(n for n in root.iter('node') if n.get('resource-id') == PKG + ':id/scroll')
    viewport = bounds(scroll.get('bounds'))
    for node in scroll.iter('node'):
        rect = bounds(node.get('bounds'))
        assert not any(intersects(rect, bar) for bar in bars), (
            'Scrolled UI overlaps system bar', node.get('text'), rect, bars)
    return {'viewport': viewport, 'system_bars': bars}


def check(name):
    import verify_native_ui as ui
    ui.capture(name)
    windows = ui.shell('dumpsys', 'window', 'windows')
    (ui.OUT / (name + '-windows.txt')).write_text(windows, encoding='utf-8')
    result = assert_safe_viewport(ui.tree(), windows)
    (ui.OUT / (name + '-geometry.json')).write_text(json.dumps(result, indent=2))
    print('PASS native scroll geometry:', name, result, flush=True)


def main():
    import verify_native_ui as ui
    assert 'ChopTorch_P1_API37' in ui.adb('emu', 'avd', 'name'), 'Only disposable AVD allowed'
    ui.OUT = ui.OUT / 'insets-fix'
    ui.OUT.mkdir(parents=True, exist_ok=True)
    for locale, scale, label in [('en-US', '1.0', 'en-font100'), ('fr-FR', '2.0', 'fr-font200')]:
        ui.shell('settings', 'put', 'system', 'font_scale', scale)
        ui.shell('cmd', 'uimode', 'night', 'yes')
        ui.shell('cmd', 'locale', 'set-app-locales', ui.PKG, '--locales', locale)
        ui.shell('am', 'force-stop', ui.PKG)
        ui.launch()
        # Reach the disclosure at any text size without truncating content.
        for _ in range(10):
            if any(n.get('resource-id') == ui.PKG + ':id/help_button' for n in ui.tree().iter('node')):
                break
            ui.shell('input', 'swipe', '500', '1800', '500', '800', '400')
        ui.click('help_button')
        # Multiple intermediate offsets, not only the end where blank space can
        # accidentally hide an overlap. Verify the help is genuinely expanded.
        for step in range(6):
            ui.shell('input', 'swipe', '500', '1700', '500', '1050', '400')
            time.sleep(.4)
            check(f'{label}-scroll{step + 1}')
        ui.assert_text('Source code and license' if locale == 'en-US' else 'Code source et licence')
    ui.shell('settings', 'put', 'system', 'font_scale', '1.0')
    ui.shell('cmd', 'uimode', 'night', 'no')
    ui.launch()
    print('PASS: expanded help reachable at 100/200%, six scrolled offsets each; no system-bar overlap.')


if __name__ == '__main__':
    main()
