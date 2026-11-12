# SPDX-License-Identifier: GPL-3.0-or-later
"""Source wiring contracts, not framework execution or classifier accuracy tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/kotlin/fr/nauss/choptorch'


class ReliabilityContract(unittest.TestCase):
    def test_service_uses_enabled_plane_linear_including_reload(self):
        text = (MAIN / 'ChopService.kt').read_text(encoding='utf-8')
        self.assertEqual(3, text.count('=PlaneLinearChopDetector('))
        self.assertEqual(3, text.count('getInt("sensitivity",50),true)'))
        self.assertNotIn('=DirectionalChopDetector(', text)
        self.assertNotIn('=ChopDetector(', text)
        self.assertIn('SampleFreshness.accepts(t,received)', text)
        self.assertIn('feedbackWindow.sample(detector,', text)

    def test_one_value_proximity_precedes_linear_vector_guard(self):
        text = (MAIN / 'ChopService.kt').read_text(encoding='utf-8')
        self.assertLess(text.index('if(event.sensor.type==Sensor.TYPE_PROXIMITY)'), text.index('event.values.size<3'))
        self.assertIn('if(event.sensor.type!=Sensor.TYPE_LINEAR_ACCELERATION) return', text)
        self.assertIn('!proximity.allowsGesture) { detector.cancel(); return }', text)
        self.assertIn('proximity.sample(event.timestamp,', text)
        self.assertIn('LiveState.update { it.copy(context=message) }', text)

    def test_required_context_has_no_registration_fallback(self):
        text = (MAIN / 'ChopService.kt').read_text(encoding='utf-8')
        self.assertIn('if(contextSensor==null) { fail(R.string.error_proximity); return }', text)
        self.assertIn('if(!contextRegistered) { fail(R.string.error_proximity); return }', text)
        self.assertIn('ProximityGate(contextSensor.maximumRange.toDouble())', text)
        self.assertIn('getDefaultSensor(Sensor.TYPE_PROXIMITY,true)', text)
        self.assertIn('sensors.registerListener(this,contextSensor,SensorManager.SENSOR_DELAY_NORMAL,0,handler)', text)
        fail = text.split('private fun fail(message: Int)', 1)[1].split('private fun checkPolicy', 1)[0]
        self.assertIn('sensors.unregisterListener(this)', fail)
        self.assertIn('proximity.reset()', fail)

    def test_policy_force_check_at_only_command_and_no_per_sample_binder(self):
        text = (MAIN / 'ChopService.kt').read_text(encoding='utf-8')
        callback = text.split('override fun onSensorChanged', 1)[1].split('override fun onAccuracyChanged', 1)[0]
        self.assertNotIn('Activation.blockedReason', callback)
        self.assertEqual(1, text.count('torch.toggle()'))
        self.assertIn('if(!stopping && proximity.allowsGesture && checkPolicy(force=true)) torch.toggle()', callback)
        self.assertIn('if(!checkPolicy()) return', callback)
        self.assertIn('PolicyGate({ Activation.enabled(this) }, { Activation.blockedReason(this) })', text)
        for token in ['postDelayed', 'Timer(', 'WakeLock', 'TYPE_GYROSCOPE', 'TYPE_ACCELEROMETER']:
            self.assertNotIn(token, text)

    def test_context_does_not_replace_separate_actual_torch_or_poster(self):
        text = (MAIN / 'MainActivity.kt').read_text(encoding='utf-8')
        self.assertIn('torchState.setText(state.torch)', text)
        self.assertIn('state.running && state.context != 0 -> state.context', text)
        self.assertNotIn('hero_title', text.split('private fun render()', 1)[1])

    def test_no_haptic_command_provenance_or_sensor_recording_added(self):
        text = (MAIN / 'ChopService.kt').read_text(encoding='utf-8')
        self.assertIn('onConfirmed={', text)
        for name in ['PlaneLinearChopDetector.kt', 'PlaneEnergy.kt', 'DirectionalChopDetector.kt', 'ProximityGate.kt', 'PolicyGate.kt']:
            source = (MAIN / name).read_text(encoding='utf-8')
            for token in ['java.io', 'android.', 'mutableListOf', 'ArrayList', 'println(', 'Thread(']:
                self.assertNotIn(token, source)


if __name__ == '__main__':
    unittest.main()
