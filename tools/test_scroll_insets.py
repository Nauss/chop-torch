#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Geometry checker contracts; device-independent, no Android SDK needed."""
import unittest
import xml.etree.ElementTree as ET
from verify_scroll_insets import PKG, assert_safe_viewport

WINDOWS = '''
  Window #0 Window{status}:
    mAttrs={ty=STATUS_BAR}
    Frames: parent=[0,0][1080,2400] display=[0,0][1080,2400] frame=[0,0][1080,136]
    isVisible=true
  Window #1 Window{navigation}:
    mAttrs={ty=NAVIGATION_BAR}
    Frames: parent=[0,0][1080,2400] display=[0,0][1080,2400] frame=[0,2274][1080,2400]
    isVisible=true
'''


def tree(viewport='[0,136][1080,2274]', text='[63,136][1000,400]'):
    root = ET.Element('hierarchy')
    scroll = ET.SubElement(root, 'node', {'resource-id': PKG + ':id/scroll', 'bounds': viewport})
    ET.SubElement(scroll, 'node', {'text': 'Scrolled help', 'bounds': text})
    return root


class ScrollInsetsGeometry(unittest.TestCase):
    def test_accepts_safe_viewport_and_partially_clipped_text(self):
        result = assert_safe_viewport(tree(), WINDOWS)
        self.assertEqual((0,136,1080,2274), result['viewport'])

    def test_rejects_original_fullscreen_viewport_even_when_text_is_safe(self):
        with self.assertRaisesRegex(AssertionError, 'overlaps system bar'):
            assert_safe_viewport(tree(viewport='[0,0][1080,2400]'), WINDOWS)

    def test_rejects_text_above_safe_viewport(self):
        with self.assertRaisesRegex(AssertionError, 'overlaps system bar'):
            assert_safe_viewport(tree(text='[63,100][1000,400]'), WINDOWS)

    def test_rejects_text_below_safe_viewport(self):
        with self.assertRaisesRegex(AssertionError, 'overlaps system bar'):
            assert_safe_viewport(tree(text='[63,2200][1000,2300]'), WINDOWS)

    def test_fails_closed_when_bar_frames_unavailable(self):
        with self.assertRaisesRegex(AssertionError, 'Expected visible'):
            assert_safe_viewport(tree(), '')


if __name__ == '__main__':
    unittest.main()
