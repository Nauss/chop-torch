package fr.nauss.choptorch

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Hermetic SYNTHETIC engineering envelope, not measured comfort or accuracy. */
class LightSensitivityTest {
    private class Trace(s: Int, val step: Long = 8_000_000L) {
        val detector = ChopDetector(s)
        var t = 1_000_000_000L
        var hits = 0
        fun run(ms: Int, signal: (Double) -> DoubleArray = { doubleArrayOf(0.0, 0.0, 0.0) }) {
            val begin = t
            while (t - begin < ms * 1_000_000L) {
                val v = signal((t - begin) / 1e6)
                if (detector.sample(t, v[0], v[1], v[2] + 9.81)) hits++
                t += step
            }
        }
        init { run(840) }
        fun stroke(a: Double, duration: Int, axis: Int = 0, curve: Double = 0.0, angle: Double = 0.0) {
            run(duration) { ms ->
                val p = ms / duration
                val thrust = a * sin(2 * PI * p)
                val bend = angle + curve * sin(PI * p)
                val v = doubleArrayOf(thrust * cos(bend), thrust * sin(bend), 0.0)
                DoubleArray(3) { v[(it + axis) % 3] }
            }
            run(80)
        }
    }
    private fun pair(s: Int, a: Double, duration: Int, step: Long, axis: Int = 0,
                     curve: Double = 0.0, secondAngle: Double = 0.0): Int {
        val t = Trace(s, step)
        t.stroke(a, duration, axis, curve)
        t.stroke(a, duration, axis, curve, secondAngle)
        t.run(600)
        return t.hits
    }

    @Test fun maximumAcceptsHalfThePreviousTestedAmplitudeAcrossAxesCadencesAndModestCurves() {
        for (duration in listOf(200, 280, 400)) for (step in listOf(7_000_000L, 8_400_000L, 10_000_000L, 20_000_000L))
            for (axis in 0..2) for (curve in listOf(0.0, 0.25)) {
                assertEquals("a=3 duration=$duration step=$step axis=$axis curve=$curve", 1,
                    pair(100, 3.0, duration, step, axis, curve, 0.15))
            }
    }

    @Test fun thresholdIsSmoothBoundedAndStrictlyDecreasingWithoutChangingDefault() {
        assertEquals(50, ChopDetector().sensitivity)
        assertEquals(18.0, ChopDetector(0).amplitudeThreshold, 1e-12)
        // The 0..50 preservation contract is explicitly superseded by recalibration.
        assertEquals(0.5625, ChopDetector(50).amplitudeThreshold, 1e-12)
        assertEquals(18.0 / 7.0.pow(2.5), ChopDetector(100).amplitudeThreshold, 1e-12)
        val thresholds = (0..100).map { ChopDetector(it).amplitudeThreshold }
        for (s in 1..100) {
            assertTrue(thresholds[s] < thresholds[s - 1])
            val old = if (s <= 50) 18.0 - s * 0.1 else 13.0 - (s - 50) * 0.17
            assertTrue("no stricter completed evidence s=$s", thresholds[s] <= old + 1e-12)
        }
        // No old piecewise-linear slope jump at the midpoint.
        assertTrue(abs((thresholds[51] - thresholds[50]) - (thresholds[50] - thresholds[49])) < 0.02)
    }

    @Test fun isolatedAcceptanceIsMonotoneAtEveryIntegerSetting() {
        for (a in listOf(2.0, 2.5, 3.0, 4.5, 6.0, 12.0, 16.0, 24.0))
            for (duration in listOf(200, 280, 400)) for (step in listOf(7_000_000L, 20_000_000L))
                for (curve in listOf(0.0, 0.25)) {
                    var accepted = false
                    for (s in 0..100) {
                        val hits = pair(s, a, duration, step, curve = curve)
                        assertTrue("duplicate s=$s a=$a duration=$duration", hits in 0..1)
                        if (accepted) assertEquals("s=$s a=$a duration=$duration step=$step curve=$curve", 1, hits)
                        accepted = hits == 1
                    }
                }
    }

    private fun rejectAtSettings(signal: (Trace) -> Unit) {
        for (s in listOf(0, 50, 75, 90, 100)) for (step in listOf(7_000_000L, 20_000_000L)) {
            val t = Trace(s, step); signal(t); t.run(800)
            assertEquals("s=$s step=$step", 0, t.hits)
        }
    }

    @Test fun lightSingleStrokesWrongAxesAndNoActiveBrakingRemainRejected() {
        rejectAtSettings { it.stroke(3.0, 280) }
        for (angle in listOf(PI, PI / 2, PI / 3)) for (duration in listOf(200, 280, 400)) {
            rejectAtSettings { it.stroke(3.0, duration); it.stroke(3.0, duration, angle = angle) }
        }
        rejectAtSettings { t -> repeat(2) {
            t.run(100) { doubleArrayOf(3.0 * sin(PI * it / 100), 0.0, 0.0) }; t.run(180)
        } }
    }

    @Test fun lightJitterVibrationSlowTiltsAndTimestampGapsRemainRejected() {
        for (period in listOf(28, 48, 80, 700, 1000)) rejectAtSettings { t ->
            t.run(3000) { doubleArrayOf(3.0 * sin(2 * PI * it / period), 0.3 * sin(it), 0.0) }
        }
        for (gap in listOf(-20_000_000L, 0L, 121_000_000L)) rejectAtSettings { t ->
            t.stroke(3.0, 280); t.t += gap - t.step
            t.stroke(3.0, 280)
        }
    }

    @Test fun walkingLikeEllipsesDoNotBecomeLightPairs() {
        for (a in listOf(2.5, 3.0, 4.0, 6.0)) for (ratio in listOf(0.6, 0.75, 1.0))
            for (period in listOf(280, 400, 500, 600)) rejectAtSettings { t ->
                t.run(5000) { doubleArrayOf(a * sin(2 * PI * it / period), a * ratio * cos(2 * PI * it / period), 0.0) }
            }
    }

    @Test fun transversePreludesCannotHideNormalAndFastPairs() {
        for (s in 0..100) for (a in listOf(24.0, 32.0)) for (duration in listOf(200, 280)) {
            val t = Trace(s)
            t.run(140) { doubleArrayOf(0.0, 1.8, 0.0) }
            t.stroke(a, duration, curve = 0.25); t.stroke(a, duration, curve = 0.25)
            t.run(600)
            assertEquals("s=$s a=$a duration=$duration", 1, t.hits)
        }
    }

    @Test fun newlyAdmittedLightPairsAllowDifferentModestOnsetsAndTriangularLobes() {
        for (step in listOf(7_000_000L, 10_000_000L, 20_000_000L)) for (duration in listOf(280, 400)) {
            val t = Trace(100, step)
            repeat(2) { stroke ->
                t.run(duration) { ms ->
                    val p = ms / duration
                    val a = 3.0 * sin(2 * PI * p)
                    val angle = if (p < 0.25) (if (stroke == 0) -0.35 else 0.35) * (1 - p / 0.25) else 0.0
                    doubleArrayOf(a * cos(angle), a * sin(angle), 0.0)
                }
                t.run(80)
            }
            t.run(600)
            assertEquals("different onset step=$step duration=$duration", 1, t.hits)
            val triangular = Trace(100, step)
            repeat(2) {
                triangular.run(duration) { ms ->
                    val p = ms / duration
                    val a = 3.0 * when {
                        p < 0.25 -> 4 * p
                        p < 0.75 -> 2 - 4 * p
                        else -> 4 * p - 4
                    }
                    doubleArrayOf(a, 0.0, 0.0)
                }
                triangular.run(80)
            }
            triangular.run(600)
            assertEquals("triangular step=$step duration=$duration", 1, triangular.hits)
        }
    }

    @Test fun invalidValuesAndDiscontinuitiesCancelLightEvidence() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 161.0))
            rejectAtSettings { t ->
                t.stroke(3.0, 280)
                assertFalse(t.detector.sample(t.t, value, 0.0, 9.81)); t.t += t.step
                t.stroke(3.0, 280)
            }
    }
}
