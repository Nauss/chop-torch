package fr.nauss.choptorch

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/** Independent SYNTHETIC counterexamples at all slider endpoints. */
class SensitivitySafetyTest {
    private class Trace(s: Int) {
        val d = ChopDetector(s)
        var t = 1_000_000_000L
        var hits = 0
        fun run(ms: Int, signal: (Int) -> DoubleArray = { doubleArrayOf(0.0, 0.0) }) {
            for (i in 0 until ms step 8) {
                val v = signal(i)
                if (d.sample(t, v[0], v[1], 9.81)) hits++
                t += 8_000_000L
            }
        }
        init { run(840) }
    }
    private fun check(signal: (Trace) -> Unit) {
        for (s in listOf(0, 50, 75, 100)) {
            val a = Trace(s); signal(a); a.run(800)
            assertEquals("s=$s", 0, a.hits)
        }
    }
    @Test fun oneWeakStrokeCannotToggle() = check { a ->
        a.run(280) { doubleArrayOf(6 * sin(2 * Math.PI * it / 280), 0.0) }
    }
    @Test fun fastVibrationsCannotToggle() = check { a ->
        a.run(1800) { doubleArrayOf(28 * sin(2 * Math.PI * it / 48), 0.0) }
    }
    @Test fun ellipticalWalkingCannotToggle() = check { a ->
        a.run(6000) { doubleArrayOf(4 * sin(it * 0.015), 3 * kotlin.math.cos(it * 0.015)) }
    }
    @Test fun positiveLobesWithoutActiveBrakingCannotToggle() = check { a ->
        repeat(2) {
            a.run(100) { doubleArrayOf(24 * sin(Math.PI * it / 100), 0.0) }
            a.run(180)
        }
    }
    @Test fun delayedBrakingCannotTurnAnImpulseIntoAStroke() = check { a ->
        repeat(2) {
            a.run(8) { doubleArrayOf(28.0, 0.0) }; a.run(64)
            a.run(64) { doubleArrayOf(-28.0, 0.0) }; a.run(80)
        }
    }
    @Test fun briefBrakingCannotCompleteAStroke() = check { a ->
        repeat(2) {
            a.run(64) { doubleArrayOf(28.0, 0.0) }
            a.run(8) { doubleArrayOf(-28.0, 0.0) }; a.run(144)
        }
    }
    @Test fun longSlowMotionCannotToggle() = check { a ->
        repeat(2) {
            a.run(700) { doubleArrayOf(24 * sin(2 * Math.PI * it / 700), 0.0) }; a.run(80)
        }
    }
}
