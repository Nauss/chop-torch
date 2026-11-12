# SPDX-License-Identifier: GPL-3.0-or-later
"""Production wiring checks; runtime admission is covered by PlanePipelineTest."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'

class PlaneIntegration(unittest.TestCase):
    def test_all_production_constructions_explicitly_enable_and_use_saved_setting(self):
        source = (MAIN / 'kotlin/fr/nauss/choptorch/ChopService.kt').read_text(encoding='utf-8')
        constructions = re.findall(r'=PlaneLinearChopDetector\(([^\n]+)', source)
        self.assertEqual(3, len(constructions))
        for expression in constructions:
            self.assertEqual('getSharedPreferences("settings",0).getInt("sensitivity",50),true)', expression)
        self.assertNotIn('=DirectionalChopDetector(', source)
        self.assertNotIn('=ChopDetector(', source)

    def test_only_true_linear_and_proximity_registered_without_batching(self):
        source = (MAIN / 'kotlin/fr/nauss/choptorch/ChopService.kt').read_text(encoding='utf-8')
        self.assertIn('getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION,true)\n            ?: sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION,false)', source)
        self.assertIn('if(sensor==null) { fail(R.string.error_sensor_missing); return }', source)
        self.assertIn('if(event.sensor.type!=Sensor.TYPE_LINEAR_ACCELERATION) return', source)
        self.assertEqual(2, source.count('sensors.registerListener('))
        self.assertIn('sensors.registerListener(this,sensor,20_000,0,handler)', source)
        self.assertIn('sensors.registerListener(this,contextSensor,SensorManager.SENSOR_DELAY_NORMAL,0,handler)', source)
        for forbidden in ['TYPE_ACCELEROMETER','TYPE_GYROSCOPE','TYPE_SIGNIFICANT_MOTION','WakeLock','postDelayed']:
            self.assertNotIn(forbidden, source)

    def test_feedback_cancel_sample_cancel_and_cleanup_remain(self):
        source = (MAIN / 'kotlin/fr/nauss/choptorch/FeedbackWindow.kt').read_text(encoding='utf-8')
        plane = source.split('fun sample(detector: PlaneLinearChopDetector,', 1)[1].split('\n    }', 1)[0]
        self.assertIn('if(!suppresses(timestampNs)) return detector.sample(timestampNs,x,y,z)', plane)
        self.assertRegex(plane, r'detector.cancel\(\)\s+detector.sample\(timestampNs,x,y,z\)\s+detector.cancel\(\)\s+return false')
        service = (MAIN / 'kotlin/fr/nauss/choptorch/ChopService.kt').read_text(encoding='utf-8')
        self.assertIn('if(!fresh) {\n            detector.cancel()', service)
        self.assertIn('if(!stopping && proximity.allowsGesture && checkPolicy(force=true)) torch.toggle()', service)
        self.assertIn('detector.cancel(); proximity.reset(); torch.close()', service)
        self.assertIn('worker.quitSafely()', service)

    def test_version_and_honest_localized_help(self):
        build = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('versionCode = 16', build)
        self.assertIn('versionName = "0.13-beta.1"', build)
        self.assertIn('applicationId = "fr.nauss.choptorch"', build)
        for folder, plane, sensor, unknown in [('values', 'screen', 'linear acceleration', 'not been measured'), ('values-fr', 'écran', 'accélération linéaire', 'restent à mesurer')]:
            strings = {n.get('name'): n.text for n in ET.parse(MAIN / 'res' / folder / 'strings.xml').getroot()}
            self.assertIn(plane, strings['help_gesture'])
            self.assertNotIn('0.11', strings['help_gesture'])
            self.assertIn(sensor, strings['activation_message'])
            self.assertIn(sensor, strings['error_sensor_missing'])
            self.assertIn(unknown, strings['help_limits'])

if __name__ == '__main__':
    unittest.main()
