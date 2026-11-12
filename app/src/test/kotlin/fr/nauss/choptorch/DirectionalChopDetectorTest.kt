// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Public analytic signals only. These are not recordings or field accuracy tests. */
class DirectionalChopDetectorTest {
    // Same evidence floor as the old 50: preserve these weak-signal shape controls.
    private class Signal(val detector: DirectionalChopDetector = DirectionalChopDetector(100),
                         var now: Long = 1_000_000_000L, val step: Long = 20_000_000L,
                         val jitter: Boolean = false) {
        var hits = 0
        var samples = 0
        fun feed(x: Double = 0.0, y: Double = 0.0, z: Double = 9.81) {
            if (detector.sample(now, x, y, z)) hits++
            now += if (jitter) step + ((samples++ % 3) - 1) * step / 5 else step
        }
        fun quiet(seconds: Double) {
            val end = now + (seconds * 1e9).toLong()
            while (now < end) feed()
        }
        fun stroke(amplitude: Double = 3.0, tilt: Double = 0.0, gravity: Double = 9.81,
                   axis: Int = 0) {
            val start = now
            while (now - start < 280_000_000L) {
                val wave = sin(2 * PI * (now - start) / 280_000_000.0)
                val a = amplitude * wave
                val angle = Math.toRadians(tilt) * wave
                val horizontal = a * cos(angle) - gravity * sin(angle)
                val vertical = a * sin(angle) + gravity * cos(angle)
                if (axis == 0) feed(horizontal, 0.0, vertical)
                else feed(0.0, horizontal, vertical)
            }
        }
        fun pair(tilt: Double = 0.0, axis: Int = 0) {
            stroke(tilt = tilt, axis = axis); quiet(.08); stroke(tilt = tilt, axis = axis)
        }
    }

    @Test fun fullPairButNotSingleImpactOrPrefix() {
        val complete = Signal(); complete.quiet(.9); complete.pair(); complete.quiet(1.5)
        assertEquals(1, complete.hits)
        val single = Signal(); single.quiet(.9); single.stroke(); single.quiet(2.0)
        assertEquals(0, single.hits)
        val impact = Signal(); impact.quiet(.9); impact.feed(30.0); impact.quiet(2.0)
        assertEquals(0, impact.hits)
        val prefix = Signal(); prefix.quiet(.9); prefix.stroke(); prefix.quiet(.08)
        repeat(6) { prefix.feed(3 * sin(PI * it / 6)) }; prefix.quiet(2.0)
        assertEquals(0, prefix.hits)
    }

    @Test fun naturalReferenceFixedAndRotatingGravityRepeatedFifteenPairs() {
        for (tilt in doubleArrayOf(0.0, 5.0, 20.0, 40.0)) {
            val s = Signal(); s.quiet(.9)
            repeat(15) { s.pair(tilt); s.quiet(1.5) }
            assertEquals("tilt=$tilt", 15, s.hits)
        }
    }

    @Test fun constantNormAtDifferentMagnitudesTiltsAndSettings() {
        for (g in doubleArrayOf(1.62, 9.4, 9.81, 10.2, 20.0)) {
            for (tilt in doubleArrayOf(5.0, 20.0, 40.0, 80.0)) {
                for (level in intArrayOf(0, 50, 100)) {
                    val s = Signal(DirectionalChopDetector(level))
                    repeat(50) { s.feed(z = g) }
                    repeat(2) {
                        s.stroke(amplitude = 0.0, tilt = tilt, gravity = g)
                        repeat(4) { s.feed(z = g) }
                    }
                    repeat(80) { s.feed(z = g) }
                    assertEquals("gravity=$g tilt=$tilt level=$level", 0, s.hits)
                }
            }
        }
    }

    @Test fun samplingRatesAndJitterAndAxisSeedIndependence() {
        for (step in longArrayOf(10_000_000L, 20_000_000L, 40_000_000L)) {
            for (jitter in listOf(false, true)) {
                for (axis in 0..1) {
                    val s = Signal(step = step, jitter = jitter); s.quiet(.9)
                    s.pair(axis = axis); s.quiet(1.5)
                    assertEquals("step=$step jitter=$jitter axis=$axis", 1, s.hits)
                }
            }
        }
    }

    @Test fun cancelAndFreshnessAdmissionCannotBridgeStrokes() {
        for (stale in listOf(false, true)) {
            val s = Signal(); s.quiet(.9); s.stroke(); s.quiet(.08)
            if (stale) {
                assertFalse(SampleFreshness.accepts(s.now, s.now + 250_000_001L))
                s.detector.cancel() // Same admission path as ChopService.
            } else s.detector.cancel()
            s.stroke(); s.quiet(1.5)
            assertEquals(0, s.hits)
            s.pair(); s.quiet(1.5); assertEquals(1, s.hits)
        }
    }

    @Test fun invalidVectorsGapsAndBackwardsTimeClearCandidates() {
        for (kind in 0..6) {
            val s = Signal(); s.quiet(.9); s.stroke(); s.quiet(.08)
            when (kind) {
                0 -> s.feed(Double.NaN)
                1 -> s.feed(y = Double.POSITIVE_INFINITY)
                2 -> s.feed(z = 160.001)
                3 -> { s.now += 120_000_001L; s.feed() }
                4 -> { s.now -= 40_000_000L; s.feed() }
                5 -> assertFalse(s.detector.sample(Long.MIN_VALUE, 0.0, 0.0, 9.81))
                6 -> assertFalse(s.detector.sample(0, 0.0, 0.0, 9.81))
            }
            s.stroke(); s.quiet(1.5); assertEquals("kind=$kind", 0, s.hits)
            s.pair(); s.quiet(1.5); assertEquals("recovery kind=$kind", 1, s.hits)
        }
    }

    @Test fun longClockBoundaryAndNoDuplicate() {
        val s = Signal(now = Long.MAX_VALUE - 4_000_000_000L)
        s.quiet(.9); s.pair(); s.quiet(1.5); assertEquals(1, s.hits)
        assertFalse(s.detector.sample(Long.MIN_VALUE, 3.0, 0.0, 9.81))
        val reset = Signal(s.detector); reset.quiet(.9); reset.pair(); reset.quiet(1.5)
        // Clock reset must fail closed, not overflow debounce into a fresh command.
        assertEquals(0, reset.hits)
    }

    @Test fun fixedStorageAndExplicitSensitivityClamp() {
        val d = DirectionalChopDetector()
        repeat(100_000) { d.sample(1_000_000_000L + it * 1_000_000L, 0.0, 0.0, 9.81) }
        val arrays = d.javaClass.declaredFields.filter { it.type.isArray }.map {
            it.isAccessible = true; java.lang.reflect.Array.getLength(checkNotNull(it.get(d)))
        }
        assertTrue(arrays.isNotEmpty()); assertTrue(arrays.all { it <= 384 })
        for ((input, clamped) in listOf(-1 to 0, Int.MIN_VALUE to 0, 101 to 100, Int.MAX_VALUE to 100)) {
            val a = Signal(DirectionalChopDetector(input)); val b = Signal(DirectionalChopDetector(clamped))
            a.quiet(.9); b.quiet(.9); a.pair(); b.pair(); a.quiet(1.5); b.quiet(1.5)
            assertEquals(b.hits, a.hits)
        }
        val firm = Signal(DirectionalChopDetector(0)); firm.quiet(.9); firm.pair(); firm.quiet(1.5)
        assertEquals(0, firm.hits)
    }

    @Test fun eigensolverCannotMissPrincipalAxisOrthogonalToLargestDiagonal() {
        val d = DirectionalChopDetector()
        val covarianceField = d.javaClass.getDeclaredField("covariance").apply { isAccessible = true }
        val axesField = d.javaClass.getDeclaredField("axes").apply { isAccessible = true }
        val matrix = covarianceField.get(d) as DoubleArray
        // Largest diagonal is X=4, but the principal eigenvalue is 6 along Y+Z.
        doubleArrayOf(4.0, 0.0, 0.0, 0.0, 3.0, 3.0, 0.0, 3.0, 3.0).copyInto(matrix)
        val solve = d.javaClass.getDeclaredMethod("principalAxis").apply { isAccessible = true }
        val principal = solve.invoke(d) as Int
        val axes = axesField.get(d) as DoubleArray
        assertEquals(6.0, matrix[principal*3+principal], 1e-10)
        assertEquals(0.0, axes[principal], 1e-10)
        assertEquals(sqrt(.5), abs(axes[3+principal]), 1e-10)
        assertEquals(sqrt(.5), abs(axes[6+principal]), 1e-10)
    }

    @Test fun boundedEvaluationCadenceAndGapBoundary() {
        val d = DirectionalChopDetector()
        val evaluated = d.javaClass.getDeclaredField("evaluationNs").apply { isAccessible = true }
        var previous = 0L; var evaluations = 0
        for (i in 0 until 1000) {
            d.sample(1_000_000_000L + i*1_000_000L, 0.0, 0.0, 9.81)
            val now = evaluated.getLong(d)
            if (now != previous) {
                if (previous != 0L) assertTrue(now-previous >= 40_000_000L)
                evaluations++; previous = now
            }
        }
        assertEquals(25, evaluations)
        val size = d.javaClass.getDeclaredField("size").apply { isAccessible = true }
        d.sample(2_119_000_000L, 0.0, 0.0, 9.81) // Exactly 120 ms remains admissible.
        assertEquals(384, size.getInt(d))
        d.sample(2_239_000_001L, 0.0, 0.0, 9.81)
        assertEquals(0, size.getInt(d))
    }

    @Test fun contextTransitionsAndEveryNearSampleBlockTheProductionDetector() {
        val d = DirectionalChopDetector(100)
        val gate = ProximityGate(5.0)
        var now = 1_000_000_000L; var hits = 0
        fun context(distance: Double) { if (gate.sample(now, distance)) d.cancel() }
        fun feed(x: Double = 0.0) {
            if (!gate.allowsGesture) d.cancel()
            else if (d.sample(now, x, 0.0, 9.81)) hits++
            now += 20_000_000L
        }
        fun stroke() {
            for (ms in 0 until 280 step 20) feed(3*sin(2*PI*ms/280))
            repeat(4) { feed() }
        }
        fun pair() { repeat(50) { feed() }; stroke(); stroke(); repeat(60) { feed() } }
        pair(); assertEquals(0, hits) // Unknown.
        context(0.0); pair(); pair(); assertEquals(0, hits) // Persistent near.
        context(5.0); repeat(50) { feed() }; stroke()
        context(0.0); feed(); context(5.0); stroke(); repeat(60) { feed() }
        assertEquals(0, hits) // Far cannot revive a pre-near half gesture.
        pair(); assertEquals(1, hits)
        context(Double.NaN); pair(); assertEquals(1, hits)
        context(5.0); pair(); assertEquals(2, hits)
    }

    @Test fun cooldownNeverAllowsTwoCommandsInsideNineHundredMilliseconds() {
        val d = DirectionalChopDetector(100)
        val hits = mutableListOf<Long>()
        var t = 1_000_000_000L
        fun feed(x: Double = 0.0) {
            if (d.sample(t, x, 0.0, 9.81)) hits += t
            t += 20_000_000L
        }
        repeat(50) { feed() }
        repeat(12) {
            repeat(2) {
                for (ms in 0 until 280 step 20) feed(3*sin(2*PI*ms/280))
                repeat(4) { feed() }
            }
            repeat(5) { feed() }
        }
        repeat(80) { feed() }
        assertTrue(hits.isNotEmpty())
        for (i in 1 until hits.size) assertTrue(hits[i]-hits[i-1] >= 900_000_000L)
    }
}
