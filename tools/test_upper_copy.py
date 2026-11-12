# SPDX-License-Identifier: GPL-3.0-or-later
"""Static poster copy is independent of callback-driven flashlight state."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main'
A = '{http://schemas.android.com/apk/res/android}'


class UpperCopyTest(unittest.TestCase):
    def test_static_localized_tagline_and_unchanged_live_strings(self):
        for folder, headline, tagline, off in (
                ('values', 'One\\nmove.', 'Flashlight on', 'Flashlight off'),
                ('values-fr', 'Un\\ngeste.', 'Torche allumée', 'Torche éteinte')):
            strings = {n.get('name'): n.text for n in ET.parse(ROOT / 'res' / folder / 'strings.xml').getroot()}
            self.assertEqual(headline, strings['hero_title'])
            self.assertEqual(tagline, strings.get('hero_tagline'))
            self.assertEqual(off, strings['torch_off'])

    def test_live_status_is_below_detail_outside_poster_with_live_region(self):
        root = ET.parse(ROOT / 'res/layout/activity_main.xml').getroot()
        nodes = {n.get(A + 'id'): n for n in root.iter() if n.get(A + 'id')}
        poster = nodes['@+id/poster']
        self.assertNotIn(nodes['@+id/torch_state'], list(poster.iter()))
        tagline = nodes.get('@+id/hero_tagline')
        self.assertIsNotNone(tagline)
        self.assertIn(tagline, list(poster.iter()))
        self.assertEqual('@string/hero_tagline', tagline.get(A + 'text'))
        self.assertNotEqual('polite', tagline.get(A + 'accessibilityLiveRegion'))
        live = nodes['@+id/torch_state']
        self.assertEqual('polite', live.get(A + 'accessibilityLiveRegion'))
        parent = next(n for n in root.iter() if live in list(n))
        self.assertEqual(list(parent).index(nodes['@+id/detail']) + 1, list(parent).index(live))
        self.assertNotEqual('@color/ink', live.get(A + 'textColor'))


if __name__ == '__main__':
    unittest.main()
