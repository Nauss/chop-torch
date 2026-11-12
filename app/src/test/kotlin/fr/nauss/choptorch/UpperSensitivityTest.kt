package fr.nauss.choptorch

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic low-amplitude contract, not measured human effort or false-positive rate. */
class UpperSensitivityTest {
    private class Trace(s: Int, val step: Long = 8_000_000L) {
        val events = mutableMapOf<ChopDetector.Event, Int>()
        val detector = ChopDetector(s) { events[it] = (events[it] ?: 0) + 1 }
        var t = 1_000_000_000L
        var hits = 0
        fun run(ms: Int, signal: (Double) -> DoubleArray = { doubleArrayOf(0.0, 0.0, 0.0) }) {
            val start = t
            while (t - start < ms * 1_000_000L) {
                val v = signal((t - start) / 1e6)
                if (detector.sample(t, v[0], v[1], v[2] + 9.81)) hits++
                t += step
            }
        }
        init { run(840) }
        fun stroke(duration: Int, axis: Int = 0, curve: Double = 0.0, angle: Double = 0.0,
                   triangular: Boolean = false, onset: Double = 0.0) {
            run(duration) { ms ->
                val p = ms / duration
                val a = 1.5 * if (triangular) when {
                    p < 0.25 -> 4 * p
                    p < 0.75 -> 2 - 4 * p
                    else -> 4 * p - 4
                } else sin(2 * PI * p)
                val bend = angle + curve * sin(PI * p) + if (p < 0.25) onset * (1 - p / 0.25) else 0.0
                val v = doubleArrayOf(a * cos(bend), a * sin(bend), 0.0)
                DoubleArray(3) { v[(it + axis) % 3] }
            }
            run(80)
        }
    }

    @Test fun recalibrationSupersedesLowerHalfMappingButPreservesZeroAndIntegerSemantics() {
        for (s in 0..100) {
            assertEquals("s=$s", (18.0 / (1.0 + 0.06 * s).pow(2.5)).toBits(),
                ChopDetector(s).amplitudeThreshold.toBits())
            assertEquals(s, ChopDetector(s).sensitivity)
        }
        assertEquals(18.0.toBits(), ChopDetector(0).amplitudeThreshold.toBits())
        assertEquals(0.5625, ChopDetector(50).amplitudeThreshold, 0.0)
        assertEquals(18.0 / 7.0.pow(2.5), ChopDetector(100).amplitudeThreshold, 1e-12)
        assertEquals(50, ChopDetector().sensitivity)
        val values = (0..100).map { ChopDetector(it).amplitudeThreshold }
        for (s in 1..100) assertTrue("s=$s", values[s] < values[s - 1])
        assertTrue(abs((values[51] - values[50]) - (values[50] - values[49])) < 0.02)
    }

    @Test fun maximumActuallyAcceptsOnePointFiveAcrossRatesAxesAndModestCurves() {
        val failures = mutableListOf<String>()
        for (duration in listOf(200, 280, 400))
            for (step in listOf(7_000_000L, 8_400_000L, 10_000_000L, 20_000_000L))
                for (axis in 0..2) for (curve in listOf(0.0, 0.25)) {
                    val t = Trace(100, step)
                    t.stroke(duration, axis, curve); t.stroke(duration, axis, curve, 0.15); t.run(600)
                    if (t.hits != 1) failures += "duration=$duration step=$step axis=$axis curve=$curve hits=${t.hits} events=${t.events}"
                }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun newAmplitudeKeepsCurvedOnsetsTrianglesAndTransversePreludes() {
        for (duration in listOf(280, 400)) for (step in listOf(7_000_000L, 10_000_000L, 20_000_000L)) {
            for (shape in 0..2) {
                val t = Trace(100, step)
                if (shape == 2) t.run(140) { doubleArrayOf(0.0, 0.6, 0.0) }
                t.stroke(duration, curve = 0.15, triangular = shape == 1, onset = if (shape == 0) -0.35 else 0.0)
                t.stroke(duration, curve = 0.15, triangular = shape == 1, onset = if (shape == 0) 0.35 else 0.0)
                t.run(600)
                assertEquals("duration=$duration step=$step shape=$shape events=${t.events}", 1, t.hits)
            }
        }
    }

    private fun reject(signal: (Trace) -> Unit) {
        for (s in listOf(0, 50, 75, 90, 95, 100)) for (step in listOf(7_000_000L, 20_000_000L)) {
            val t = Trace(s, step); signal(t); t.run(800)
            assertEquals("s=$s step=$step events=${t.events}", 0, t.hits)
        }
    }

    @Test fun singleNoBrakeWrongAndReversedAxesStayRejectedAtNewAmplitude() {
        for (duration in listOf(200, 280, 400)) {
            reject { it.stroke(duration) }
            for (angle in listOf(PI, PI / 2, PI / 3)) reject {
                it.stroke(duration); it.stroke(duration, angle = angle)
            }
        }
        reject { t -> repeat(2) {
            t.run(100) { doubleArrayOf(1.5 * sin(PI * it / 100), 0.0, 0.0) }; t.run(180)
        } }
    }

    @Test fun slowVibrationAndSeededJitterStayRejectedAtNewAmplitude() {
        for (period in listOf(28, 48, 80, 700, 1000)) reject { t ->
            t.run(3000) { doubleArrayOf(1.5 * sin(2 * PI * it / period), 0.15 * sin(it), 0.0) }
        }
        for (seed in 0L..15L) reject { t ->
            val random = java.util.Random(seed)
            t.run(5000) { DoubleArray(3) { (random.nextDouble() * 2 - 1) * 1.5 } }
        }
    }

    @Test fun ellipsesAndRotationStayRejectedAtNewAmplitude() {
        for (a in listOf(1.2, 1.5, 1.8, 2.0)) for (ratio in listOf(0.6, 0.75, 1.0))
            for (period in listOf(200, 280, 400, 500, 600)) reject { t ->
                t.run(5000) { doubleArrayOf(a * sin(2 * PI * it / period), a * ratio * cos(2 * PI * it / period), 0.0) }
            }
    }

    @Test fun timestampFaultsAndInvalidSamplesCancelNewAmplitudeEvidence() {
        for (gap in listOf(-20_000_000L, 0L, 121_000_000L)) reject {
            it.stroke(280); it.t += gap - it.step; it.stroke(280)
        }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 161.0)) reject {
            it.stroke(280); assertFalse(it.detector.sample(it.t, value, 0.0, 9.81)); it.t += it.step; it.stroke(280)
        }
    }

    @Test fun isolatedNewAmplitudePairsNeverDuplicateOrLoseAcceptanceAsSettingIncreases() {
        for (duration in listOf(200, 280, 400)) for (step in listOf(7_000_000L, 20_000_000L))
            for (curve in listOf(0.0, 0.25)) {
                var accepted = false
                for (s in 0..100) {
                    val t = Trace(s, step)
                    t.stroke(duration, curve = curve); t.stroke(duration, curve = curve); t.run(600)
                    assertTrue(t.hits in 0..1)
                    if (accepted) assertEquals("s=$s duration=$duration step=$step curve=$curve", 1, t.hits)
                    accepted = t.hits == 1
                }
            }
    }
}
