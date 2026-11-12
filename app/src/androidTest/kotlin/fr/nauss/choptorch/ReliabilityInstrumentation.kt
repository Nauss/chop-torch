// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.app.Instrumentation
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

/** Select with suite=reliability on an owned disposable emulator only.
 * Real framework registration and observed production context; synthetic recognition
 * is isolated from the service and can never issue a torch or haptic command.
 */
object ReliabilityInstrumentation {
    fun run(test: Instrumentation) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Disposable emulator required" }
        syntheticControls()
        val context = test.targetContext
        val manager = context.getSystemService(SensorManager::class.java)
        val linear = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, false)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_PROXIMITY, false)
        test.runOnMainSync { Activation.stop(context) }
        waitUntil { !LiveState.state.running }
        // Stop is asynchronous; allow destruction before exercising a fresh registration.
        Thread.sleep(400)
        if (linear == null || sensor == null) {
            val expectedError = if (linear == null) R.string.error_sensor_missing else R.string.error_proximity
            try {
                test.runOnMainSync { check(Activation.start(context, userInitiated = true)) }
                waitUntil { LiveState.state.error == expectedError }
                check(!LiveState.state.running && !Activation.enabled(context))
            } finally { test.runOnMainSync { Activation.stop(context) } }
            return
        }
        val observed = AtomicReference(ProximityGate.State.UNKNOWN)
        val ready = CountDownLatch(1)
        val gate = ProximityGate(sensor.maximumRange.toDouble())
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
                gate.sample(event.timestamp, event.values.firstOrNull()?.toDouble() ?: Double.NaN)
                observed.set(gate.state)
                ready.countDown()
            }
        }
        try {
            check(manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL,
                Handler(Looper.getMainLooper()))) { "Framework proximity registration failed" }
            check(ready.await(5, TimeUnit.SECONDS)) { "No initial on-change proximity reading" }
            check(observed.get() != ProximityGate.State.UNKNOWN)
            repeat(2) {
                test.runOnMainSync { check(Activation.start(context, userInitiated = true)) }
                waitUntil { LiveState.state.running && LiveState.state.context == expected(observed.get()) }
                val activity = test.startActivitySync(android.content.Intent(context, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                try {
                    test.runOnMainSync {
                        val state = LiveState.state
                        val detail = activity.findViewById<android.widget.TextView>(R.id.detail)
                        if (state.context != 0) {
                            check(detail.visibility == android.view.View.VISIBLE)
                            check(detail.text.toString() == context.getString(state.context))
                        } else {
                            check(detail.visibility == android.view.View.GONE && detail.text.isEmpty())
                        }
                        check(activity.findViewById<android.widget.TextView>(R.id.torch_state).text.toString() ==
                            context.getString(state.torch))
                    }
                    Thread.sleep(1200) // On-change context must not expire after a quiet second.
                    check(LiveState.state.context == expected(observed.get()))
                } finally { test.runOnMainSync { activity.finish() } }
                test.runOnMainSync { Activation.stop(context) }
                waitUntil { !LiveState.state.running }
                check(LiveState.state.context == 0)
                Thread.sleep(400)
            }
        } finally {
            manager.unregisterListener(listener)
            test.runOnMainSync { Activation.stop(context) }
        }
    }

    private fun expected(state: ProximityGate.State): Int = when (state) {
        ProximityGate.State.UNKNOWN -> R.string.context_checking
        ProximityGate.State.NEAR -> R.string.context_covered
        ProximityGate.State.FAR -> 0
    }

    private fun waitUntil(condition: () -> Boolean) {
        val start = android.os.SystemClock.elapsedRealtime()
        while (!condition()) {
            check(android.os.SystemClock.elapsedRealtime() - start < 5000) { "Timed out waiting for production state" }
            Thread.sleep(25)
        }
    }

    private fun syntheticControls() {
        val detector = PlaneLinearChopDetector(100,true) // Literal linear motion without gravity.
        val gate = ProximityGate(5.0)
        var t = 1_000_000_000L
        var hits = 0
        fun feed(x: Double = 0.0) {
            if (!gate.allowsGesture) detector.cancel()
            else if (detector.sample(t, x, 0.0, 0.0)) hits++
            t += 20_000_000L
        }
        fun pair() {
            repeat(50) { feed() }
            repeat(2) {
                for (ms in 0 until 280 step 20) feed(3*sin(2*PI*ms/280))
                repeat(4) { feed() }
            }
            repeat(60) { feed() }
        }
        pair(); check(hits == 0)
        if (gate.sample(t, 0.0)) detector.cancel()
        pair(); check(hits == 0)
        if (gate.sample(t, 5.0)) detector.cancel()
        pair(); check(hits == 1)
        gate.reset(); detector.cancel(); pair(); check(hits == 1)
        var reason = 0
        val policy = PolicyGate({ true }, { reason })
        check(policy.allows(t)); reason = R.string.error_notifications
        check(!policy.allows(t + 1, force = true))
    }
}
