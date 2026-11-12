package fr.nauss.choptorch

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic amplitude contracts only: neither physical effort nor pocket safety. */
class RecalibrationTest {
    private class Trace(s: Int, val step: Long = 8_400_000L) {
        val events = mutableMapOf<ChopDetector.Event, Int>()
        val detector = ChopDetector(s) { events[it] = (events[it] ?: 0) + 1 }
        var t = 1_000_000_000L
        val hits = mutableListOf<Long>()
        fun run(ms: Int, signal: (Double) -> DoubleArray = { doubleArrayOf(0.0, 0.0, 0.0) }) {
            val start = t
            while (t - start < ms * 1_000_000L) {
                val v = signal((t - start) / 1e6)
                if (detector.sample(t, v[0], v[1], v[2] + 9.81)) hits += t
                t += step
            }
        }
        init { run(840) }
        fun stroke(a: Double, duration: Int, axis: Int = 0, curve: Double = 0.0,
                   angle: Double = 0.0, triangular: Boolean = false, onset: Double = 0.0) {
            run(duration) { ms ->
                val p = ms / duration
                val thrust = a * if (triangular) when {
                    p < 0.25 -> 4 * p
                    p < 0.75 -> 2 - 4 * p
                    else -> 4 * p - 4
                } else sin(2 * PI * p)
                val bend = angle + curve * sin(PI * p) + if (p < 0.25) onset * (1 - p / 0.25) else 0.0
                val v = doubleArrayOf(thrust * cos(bend), thrust * sin(bend), 0.0)
                DoubleArray(3) { v[(it + axis) % 3] }
            }
            run(80)
        }
    }

    private fun acceptance(s: Int, a: Double) {
        val failures = mutableListOf<String>()
        for (duration in listOf(200, 280, 400))
            for (step in listOf(7_000_000L, 8_400_000L, 10_000_000L, 20_000_000L))
                for (axis in 0..2) for (curve in listOf(0.0, 0.25)) {
                    val t = Trace(s, step)
                    t.stroke(a, duration, axis, curve)
                    t.stroke(a, duration, axis, curve, angle = 0.15)
                    t.run(600)
                    if (t.hits.size != 1) failures += "s=$s a=$a duration=$duration step=$step axis=$axis curve=$curve hits=${t.hits} events=${t.events}"
                }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun middleAcceptsPointSevenFiveAcrossRealCadencesAxesAndCurves() {
        acceptance(50, 0.75)
    }

    @Test fun extremeAcceptsPointTwoAcrossRealCadencesAxesAndCurves() {
        acceptance(100, 0.2)
    }

    private fun reject(a: Double, action: (Trace) -> Unit) {
        for (s in listOf(0, 25, 50, 75, 100))
            for (step in listOf(7_000_000L, 8_400_000L, 10_000_000L, 20_000_000L)) {
                val t = Trace(s, step); action(t); t.run(800)
                assertEquals("s=$s a=$a step=$step events=${t.events}", 0, t.hits.size)
            }
    }

    @Test fun newAmplitudesStillRequireTwoBrakedAlignedStrokes() {
        for (a in listOf(0.2, 0.75)) for (duration in listOf(200, 280, 400)) {
            reject(a) { it.stroke(a, duration) }
            for (angle in listOf(PI, PI / 2, PI / 3)) reject(a) {
                it.stroke(a, duration); it.stroke(a, duration, angle = angle)
            }
            reject(a) { t -> repeat(2) {
                t.run(duration / 2) { doubleArrayOf(a * sin(2 * PI * it / duration), 0.0, 0.0) }
                t.run(duration / 2 + 80)
            } }
            reject(a) { it.stroke(a, duration); it.detector.cancel(); it.stroke(a, duration) }
        }
    }

    @Test fun newAmplitudesKeepSpeedSupportAndTimestampGuards() {
        for (a in listOf(0.2, 0.75)) {
            for (period in listOf(28, 48, 80, 700, 1000)) reject(a) { t ->
                t.run(3000) { doubleArrayOf(a * sin(2 * PI * it / period), 0.0, 0.0) }
            }
            for (gap in listOf(-20_000_000L, 0L, 121_000_000L)) reject(a) {
                it.stroke(a, 280); it.t += gap - it.step; it.stroke(a, 280)
            }
            for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 161.0, -161.0)) reject(a) {
                it.stroke(a, 280)
                assertFalse(it.detector.sample(it.t, value, 0.0, 9.81)); it.t += it.step
                it.stroke(a, 280)
            }
            reject(a) { it.stroke(a, 280); it.run(800); it.stroke(a, 280) }
        }
    }

    @Test fun smallEllipsesAndSeededBroadbandJitter() {
        for (a in listOf(0.2, 0.4, 0.75)) {
            for (ratio in listOf(0.6, 0.75, 1.0)) for (period in listOf(200, 280, 400, 500, 600)) reject(a) { t ->
                t.run(3000) { doubleArrayOf(a * sin(2 * PI * it / period), a * ratio * cos(2 * PI * it / period), 0.0) }
            }
            for (seed in 0L..15L) reject(a) { t ->
                val random = java.util.Random(seed)
                t.run(3000) { DoubleArray(3) { (random.nextDouble() * 2 - 1) * a } }
            }
        }
    }

    @Test fun coherentNearAxialPeriodicMotionIsAmbiguousAndCanToggleAtTheExtreme() {
        // This is intentionally NOT labeled human intent or a negative fixture.
        // Repeated sine lobes supply two strokes, braking and aligned directions.
        for ((ratio, firstAdmission) in listOf(0.0 to 85, 0.3 to 86)) {
            for (s in listOf(firstAdmission - 1, firstAdmission, 100)) {
                val t = Trace(s)
                t.run(3000) { doubleArrayOf(0.2 * sin(2 * PI * it / 280),
                    ratio * 0.2 * cos(2 * PI * it / 280), 0.0) }
                if (s < firstAdmission) assertEquals(0, t.hits.size)
                else assertTrue("Ambiguous periodic motion is admitted at s=$s ratio=$ratio", t.hits.isNotEmpty())
                for (i in 1 until t.hits.size) assertTrue(t.hits[i] - t.hits[i - 1] >= 900_000_000L)
            }
        }
    }

    @Test fun lightFaultRecoveryRequiresFreshWarmupAndTwoNewStrokes() {
        for ((s, a) in listOf(50 to 0.75, 100 to 0.2)) {
            val t = Trace(s)
            t.stroke(a, 280)
            assertFalse(t.detector.sample(0L, 0.0, 0.0, 9.81))
            assertFalse(t.detector.sample(-1L, 0.0, 0.0, 9.81))
            t.stroke(a, 280)
            assertTrue(t.hits.isEmpty())
            t.run(840)
            t.stroke(a, 280)
            assertTrue(t.hits.isEmpty())
            t.stroke(a, 280)
            assertEquals(1, t.hits.size)
            t.stroke(a, 280); t.stroke(a, 280)
            assertEquals(1, t.hits.size)
            t.run(1000)
            t.stroke(a, 280); t.stroke(a, 280)
            assertEquals(2, t.hits.size)
        }
    }

    @Test fun adjacentTrianglesCurvedOnsetsAndTransversePreludesRemainAccepted() {
        for ((s, a) in listOf(50 to 0.75, 100 to 0.2))
            for (duration in listOf(280, 400))
                for (step in listOf(7_000_000L, 8_400_000L, 10_000_000L, 20_000_000L))
                    for (shape in 0..2) {
                        val t = Trace(s, step)
                        if (shape == 2) t.run(140) { doubleArrayOf(0.0, a * 0.4, 0.0) }
                        t.stroke(a, duration, curve = 0.15, triangular = shape == 1, onset = if (shape == 0) -0.35 else 0.0)
                        t.stroke(a, duration, curve = 0.15, triangular = shape == 1, onset = if (shape == 0) 0.35 else 0.0)
                        t.run(600)
                        assertEquals("s=$s duration=$duration step=$step shape=$shape events=${t.events}", 1, t.hits.size)
                    }
    }

    @Test fun newIsolatedPairsAreMonotoneAndNeverDuplicateAcrossAll101Settings() {
        for (a in listOf(0.15, 0.2, 0.3, 0.4, 0.75, 1.0)) for (duration in listOf(200, 280, 400))
            for (step in listOf(7_000_000L, 8_400_000L, 10_000_000L, 20_000_000L)) {
                var accepted = false
                for (s in 0..100) {
                    val t = Trace(s, step)
                    t.stroke(a, duration, curve = 0.25); t.stroke(a, duration, curve = 0.25); t.run(600)
                    assertTrue(t.hits.size in 0..1)
                    if (accepted) assertEquals("s=$s a=$a duration=$duration step=$step", 1, t.hits.size)
                    accepted = t.hits.size == 1
                }
            }
    }

    private fun field(obj: Any, name: String): Any? = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)

    @Test fun boundedProfilesRetainConstantsAndJoinOnlyBelowTheirCeilings() {
        for (s in 0..100) {
            val d = ChopDetector(s)
            val scales = field(d, "scales") as List<*>
            assertEquals(d.scaleCount, scales.size)
            assertEquals(listOf(2.5, 8.0, 13.0, 1.2), scales.take(4).map { field(it!!, "arm") })
            val expected = listOf(0.6, 0.3, 0.15, 0.075).filter { d.amplitudeThreshold < 2.3 * (it / 0.6) }
            assertEquals(expected, scales.drop(4).map { field(it!!, "arm") })
            for (scale in scales.drop(4)) {
                val arm = field(scale!!, "arm") as Double
                val r = arm / 0.6
                assertEquals(0.4 * r, field(scale, "accelerationSupport"))
                assertEquals(0.6 * r, field(scale, "brakeEntry"))
                assertEquals(0.4 * r, field(scale, "brakingSupport"))
                assertEquals(2.3 * r, field(scale, "ceiling"))
            }
        }
        assertEquals(4, ChopDetector(0).scaleCount)
    }

    @Test fun fullBandwidthCeilingsRejectEqualityAndAboveThenRecoverOnTheSameScale() {
        // Directly exercise the real private scale with filtered vectors. This places
        // the full-vector peak exactly at the boundary without filter-rounding ambiguity.
        for (index in 4..7) for (offset in listOf(-1e-9, 0.0, 1e-9)) {
            val events = mutableListOf<ChopDetector.Event>()
            val d = ChopDetector(100) { events += it }
            val scale = (field(d, "scales") as List<*>)[index]!!
            val arm = field(scale, "arm") as Double
            val ceiling = field(scale, "ceiling") as Double
            val sample = scale.javaClass.getDeclaredMethod("sample", Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType, DoubleArray::class.java, Double::class.javaPrimitiveType).apply { isAccessible = true }
            var t = 1_000_000_000L
            var hits = 0
            fun feed(x: Double, y: Double = 0.0, dt: Long = 10_000_000L) {
                t += dt
                if (sample.invoke(scale, t, dt, doubleArrayOf(x, y, 0.0), hypot(x, y)) as Boolean) hits++
            }
            fun stroke(spike: Boolean) {
                repeat(8) { feed(arm * 2) }
                // Transverse peak, not axial evidence: must still trip the ceiling.
                if (spike) feed(0.0, ceiling + offset, 1_000_000L)
                repeat(8) { feed(-arm * 2) }
                repeat(8) { feed(0.0) }
            }
            stroke(true)
            assertEquals("index=$index offset=$offset", offset >= 0,
                ChopDetector.Event.LIGHT_SCALE_CEILING in events)
            assertEquals(0, hits)
            stroke(false)
            assertEquals(if (offset < 0) 1 else 0, hits)
            if (offset >= 0) { stroke(false); assertEquals("recovery index=$index offset=$offset", 1, hits) }
        }
    }
}
