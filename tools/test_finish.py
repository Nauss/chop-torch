#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Regression gates for the P1 shipping surface, independent of a device."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'
ANDROID = '{http://schemas.android.com/apk/res/android}'

def luminance(color):
    channels = [int(color[i:i+2], 16)/255 for i in (1, 3, 5)]
    linear = [c/12.92 if c <= .04045 else ((c+.055)/1.055)**2.4 for c in channels]
    return sum(c*w for c,w in zip(linear, (.2126,.7152,.0722)))

def contrast(a,b):
    high, low = sorted([luminance(a),luminance(b)], reverse=True)
    return (high+.05)/(low+.05)

class FinishContract(unittest.TestCase):
    def test_no_diagnostic_production_paths(self):
        for f in MAIN.rglob('*.kt'):
            text=f.read_text(encoding='utf-8')
            for token in ['Diagnostics','DiagnosticSample','TEST_TORCH','CAPTURE','openOutputStream','ACTION_CREATE_DOCUMENT']:
                self.assertNotIn(token,text,str(f))
        self.assertTrue((ROOT/'app/src/test/kotlin/fr/nauss/choptorch/DiagnosticSample.kt').exists())

    def test_minimal_permissions_and_components(self):
        manifest=ET.parse(MAIN/'AndroidManifest.xml').getroot()
        permissions={n.get(ANDROID+'name') for n in manifest.findall('uses-permission')}
        self.assertEqual({'android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_SPECIAL_USE','android.permission.POST_NOTIFICATIONS','android.permission.RECEIVE_BOOT_COMPLETED'},permissions)
        service=manifest.find('application/service')
        self.assertEqual('false',service.get(ANDROID+'exported'))
        self.assertEqual('specialUse',service.get(ANDROID+'foregroundServiceType'))
        self.assertEqual(1,len(manifest.findall('application/activity')))
        receiver=manifest.find('application/receiver')
        self.assertEqual('false', receiver.get(ANDROID+'exported'))
        self.assertEqual({'android.intent.action.BOOT_COMPLETED','android.intent.action.MY_PACKAGE_REPLACED'},
            {n.get(ANDROID+'name') for n in receiver.findall('intent-filter/action')})

    def test_translations_complete_and_no_rendered_literals(self):
        strings=lambda folder: {n.get('name'): ''.join(n.itertext()) for n in ET.parse(MAIN/'res'/folder/'strings.xml').getroot() if n.get('translatable') != 'false'}
        en,fr=strings('values'),strings('values-fr')
        self.assertEqual(set(en),set(fr))
        for key in en:
            self.assertEqual(en[key].count('%1$'),fr[key].count('%1$'),key)
        for node in ET.parse(MAIN/'res/layout/activity_main.xml').iter():
            for attr in ['text','contentDescription']:
                value=node.get(ANDROID+attr)
                if value: self.assertTrue(value.startswith('@string/'),value)

    def test_theme_text_and_control_contrast(self):
        for mode in ['values','values-night']:
            colors={n.get('name'): n.text for n in ET.parse(MAIN/'res'/mode/'colors.xml').getroot()}
            for background in ['surface','surface_container']:
                for foreground in ['on_surface','on_surface_variant','primary']:
                    self.assertGreaterEqual(contrast(colors[foreground],colors[background]),4.5,(mode,foreground,background))
                self.assertGreaterEqual(contrast(colors['control_normal'],colors[background]),3,(mode,background))

    def test_no_timer_or_sample_console(self):
        activity=(MAIN/'kotlin/fr/nauss/choptorch/MainActivity.kt').read_text(encoding='utf-8')
        state=(MAIN/'kotlin/fr/nauss/choptorch/LiveState.kt').read_text(encoding='utf-8')
        self.assertNotIn('postDelayed',activity)
        for token in ['lastEventNs','samples:','gestures:','periodMs:']:
            self.assertNotIn(token,state)
        self.assertIn('LiveState.remove(observer)',activity)

if __name__=='__main__': unittest.main()
