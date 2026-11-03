package fr.nauss.choptorch

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mathematical probes, NOT measured comfortable gestures. */
class SensitivityContractTest {
    private fun pair(sensitivity: Int, amplitude: Double, durationMs: Double, stepNs: Long = 8_400_000L,
                     secondAngle: Double = 0.0, curve: Double = 0.0, triangular: Boolean = false,
                     transversePrelude: Boolean = false): Int {
        val d = ChopDetector(sensitivity)
        var t = 1_000_000_000L
        var hits = 0
        fun advance(duration: Double, signal: (Double) -> DoubleArray = { doubleArrayOf(0.0, 0.0) }) {
            val begin = t
            while ((t - begin) / 1e6 < duration) {
                val v = signal((t - begin) / 1e6)
                if (d.sample(t, v[0], v[1], 9.81)) hits++
                t += stepNs
            }
        }
        advance(840.0)
        if (transversePrelude) advance(140.0) { doubleArrayOf(0.0, 3.5) }
        repeat(2) { stroke ->
            advance(durationMs) {
                val p = it / durationMs
                val a = amplitude * if (triangular) when {
                    p < 0.25 -> 4 * p
                    p < 0.75 -> 2 - 4 * p
                    else -> 4 * p - 4
                } else sin(2 * Math.PI * p)
                val angle = stroke * secondAngle + curve * sin(Math.PI * p)
                doubleArrayOf(a * kotlin.math.cos(angle), a * sin(angle))
            }
            advance(80.0)
        }
        advance(420.0)
        return hits
    }

    @Test fun highSensitivityAcceptsLowerAmplitudeControlledPairs() {
        // Proposed SOFTWARE envelope, not a physiological comfort calibration.
        for (amplitude in listOf(6.0, 8.0)) for (duration in listOf(200.0, 280.0, 400.0)) {
            assertEquals("a=$amplitude duration=$duration", 1, pair(100, amplitude, duration))
        }
    }

    @Test fun higherSensitivityDoesNotLoseTheIsolated400msPair() {
        assertEquals("reference s50", 1, pair(50, 16.0, 400.0))
        assertEquals("s100 must not lose the same isolated pair", 1, pair(100, 16.0, 400.0))
    }

    @Test fun isolatedPairAcceptanceIsMonotoneAcrossAmplitudeDurationAndCadenceGrid() {
        for (a in listOf(2.0, 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0))
            for (duration in listOf(140.0, 180.0, 200.0, 240.0, 280.0, 320.0, 400.0, 500.0))
                for (step in listOf(7_000_000L, 8_400_000L, 20_000_000L)) {
                    var accepted = false
                    for (s in 0..100 step 5) {
                        val hits = pair(s, a, duration, step)
                        assertEquals("no duplicate a=$a duration=$duration step=$step s=$s", true, hits in 0..1)
                        if (accepted) assertEquals("isolated monotonicity a=$a duration=$duration step=$step s=$s", 1, hits)
                        accepted = hits == 1
                    }
                }
    }

    @Test fun weakCurvedPairsAndIndependentTriangularLobesAreRecognized() {
        for (step in listOf(7_000_000L, 8_400_000L, 20_000_000L)) {
            for (duration in listOf(280.0, 400.0)) {
                assertEquals(1, pair(100, 6.0, duration, step, secondAngle = 0.25, curve = 0.25))
                assertEquals(1, pair(100, 6.0, duration, step, triangular = true))
            }
        }
    }

    @Test fun oppositeReturnAndIncoherentDirectionRemainRejectedAtEverySetting() {
        for (s in listOf(0, 50, 75, 100)) for (angle in listOf(Math.PI, Math.PI / 2, Math.PI / 3)) {
            assertEquals(0, pair(s, 24.0, 280.0, secondAngle = angle))
            assertEquals(0, pair(s, 6.0, 280.0, secondAngle = angle))
        }
    }

    @Test fun smallTransversePreludeCannotHideAStrongerValidPair() {
        // Independent adversarial construction, NOT copied from a private capture.
        assertEquals(1, pair(100, 24.0, 200.0, transversePrelude = true))
    }
}
