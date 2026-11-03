package fr.nauss.choptorch

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/** Generic SYNTHETIC smooth lobes, never extracted from a private phone capture. */
class ContinuousChopTest {
    private class Trace(val sensitivity: Int = 50, val stepsMs: List<Long> = listOf(8, 9, 8, 8, 9)) {
        val detector = ChopDetector(sensitivity)
        var t = 1_000_000_000L
        var index = 0
        var hits = 0
        fun advance(durationMs: Long, signal: (Double) -> DoubleArray = { doubleArrayOf(0.0, 0.0, 0.0) }) {
            val start = t
            while (t - start < durationMs * 1_000_000L) {
                val v = signal((t - start) / 1e6)
                if (detector.sample(t, v[0], v[1], v[2] + 9.81)) hits++
                t += stepsMs[index++ % stepsMs.size] * 1_000_000L
            }
        }
        fun warm() = advance(800)
        fun stroke(axis: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0), amplitude: Double = 24.0) {
            advance(200) { ms ->
                val a = amplitude * sin(Math.PI * ms / 100.0)
                DoubleArray(3) { axis[it] * a }
            }
            advance(80)
        }
    }

    @Test fun modestTransverseMotionDuringBrakingReleaseIsAllowed() {
        val a = Trace(stepsMs = listOf(7)); a.warm()
        repeat(2) {
            a.advance(200) { ms -> doubleArrayOf(24.0 * sin(Math.PI * ms / 100.0), if (ms > 150.0) (ms - 150.0) / 5.0 else 0.0, 0.0) }
            a.advance(80)
        }
        assertEquals(1, a.hits)
    }

    @Test fun continuousReturnBetweenStrokesDoesNotRequireArtificialRest() {
        val a = Trace(); a.warm()
        a.advance(400) { ms -> doubleArrayOf(24.0 * sin(Math.PI * ms / 100.0), 0.0, 0.0) }
        a.advance(80)
        assertEquals(1, a.hits)
    }

    @Test fun isolatedImpulsesWithDelayedBrakesAreNotSustainedStrokes() {
        val a = Trace(stepsMs = listOf(8)); a.warm()
        repeat(2) {
            a.advance(8) { doubleArrayOf(28.0, 0.0, 0.0) }
            a.advance(64)
            a.advance(64) { doubleArrayOf(-28.0, 0.0, 0.0) }
            a.advance(80)
        }
        assertEquals(0, a.hits)
    }

    @Test fun briefBrakingImpulseIsNotSustainedBraking() {
        val a = Trace(stepsMs = listOf(8)); a.warm()
        repeat(2) {
            a.advance(64) { doubleArrayOf(28.0, 0.0, 0.0) }
            a.advance(8) { doubleArrayOf(-28.0, 0.0, 0.0) }
            a.advance(144)
        }
        assertEquals(0, a.hits)
    }

    @Test fun axesSignsAndCadencesDoNotChangeThePattern() {
        val axes = listOf(
            doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, -1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(-0.6, 0.0, -0.8)
        )
        val cadences = listOf(listOf(7L), listOf(8L, 9L, 8L, 8L, 9L), listOf(8L, 17L, 9L), listOf(20L))
        for (axis in axes) for (cadence in cadences) {
            val a = Trace(stepsMs = cadence); a.warm(); a.stroke(axis); a.stroke(axis)
            assertEquals("axis=${axis.toList()} cadence=$cadence", 1, a.hits)
        }
    }

    @Test fun returnInOppositeDirectionDoesNotCountAsSecondChop() {
        val a = Trace(); a.warm(); a.stroke(); a.stroke(doubleArrayOf(-1.0, 0.0, 0.0))
        assertEquals(0, a.hits)
    }

    @Test fun perpendicularBrakingDoesNotCompleteAStroke() {
        val a = Trace(); a.warm()
        repeat(2) {
            a.advance(80) { doubleArrayOf(24.0, 0.0, 0.0) }
            a.advance(80) { doubleArrayOf(0.0, -24.0, 0.0) }
            a.advance(120)
        }
        assertEquals(0, a.hits)
    }

    @Test fun smoothSingleStrokeAndUnbrakedPulsesDoNotToggle() {
        val single = Trace(); single.warm(); single.stroke(); single.advance(800)
        assertEquals(0, single.hits)
        val a = Trace(); a.warm()
        repeat(2) {
            a.advance(100) { ms -> doubleArrayOf(24.0 * sin(Math.PI * ms / 100.0), 0.0, 0.0) }
            a.advance(180)
        }
        assertEquals(0, a.hits)
    }

    @Test fun discontinuityCancelsSmoothCandidateAndRequiresWarmup() {
        for (jump in listOf(-16_000_000L, -8_000_000L, 121_000_000L)) {
            val a = Trace(stepsMs = listOf(8)); a.warm(); a.stroke()
            a.t += jump
            a.stroke(); assertEquals("jump=$jump", 0, a.hits)
            a.advance(800); a.stroke(); a.stroke(); assertEquals(1, a.hits)
        }
    }

    @Test fun explicitCancelAndTooLongIntervalDiscardFirstStroke() {
        val a = Trace(); a.warm(); a.stroke(); a.detector.cancel(); a.stroke()
        assertEquals(0, a.hits)
        a.advance(800); a.stroke(); a.advance(650); a.stroke()
        assertEquals(0, a.hits)
    }

    @Test fun smoothCooldownExpiresWithoutDuplicateToggle() {
        val a = Trace(); a.warm(); repeat(4) { a.stroke() }
        assertEquals(1, a.hits)
        a.advance(1000); a.stroke(); a.stroke(); assertEquals(2, a.hits)
    }

    @Test fun sensitivityKeepsSustainedAndTimingGuards() {
        for (s in listOf(0, 50, 100)) {
            val weak = Trace(s); weak.warm(); weak.stroke(amplitude = 12.0); weak.stroke(amplitude = 12.0)
            assertEquals("s=$s", if (s == 100) 1 else 0, weak.hits)
            val fast = Trace(s); fast.warm()
            fast.advance(500) { ms -> doubleArrayOf(28.0 * sin(Math.PI * ms / 24.0), 0.0, 0.0) }
            assertEquals(0, fast.hits)
        }
    }

    @Test fun secondStrokeStartedInsidePairWindowMayFinishAfterExpiry() {
        val a = Trace(); a.warm(); a.stroke(); a.advance(300); a.stroke()
        assertEquals(1, a.hits)
    }

    @Test fun smoothDecelerationAndReversalRecognizeDoubleChop() {
        val a = Trace(); a.warm(); a.stroke(); a.stroke()
        assertEquals(1, a.hits)
    }

    @Test fun weakContinuousPairsRemainValidWithJitterAndSpatialAxes() {
        val axes = listOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, -1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(-0.6, 0.0, -0.8))
        for (axis in axes) for (steps in listOf(listOf(8L, 9L, 8L, 8L, 9L), listOf(8L, 17L, 9L), listOf(20L))) {
            val a = Trace(100, steps); a.warm()
            a.advance(560) { ms -> DoubleArray(3) { axis[it] * 6.0 * sin(2 * Math.PI * ms / 280.0) } }
            a.advance(120)
            assertEquals("axis=${axis.toList()} steps=$steps", 1, a.hits)
        }
    }
}
