package fr.nauss.choptorch

import org.junit.Assert.*
import org.junit.Test

/** All samples in this suite are SYNTHETIC, not measurements from a phone. */
class ChopDetectorTest {
    private class Trace(val detector: ChopDetector = ChopDetector()) {
        var t = 1_000_000_000L
        var hits = 0
        fun sample(x: Double = 0.0, y: Double = 0.0, z: Double = 9.81) {
            if (detector.sample(t, x, y, z)) hits++
            t += 20_000_000L
        }
        fun rest(n: Int) { repeat(n) { sample() } }
        fun stroke(axis: Int = 0, amplitude: Double = 22.0) {
            repeat(3) { sample(if(axis==0) amplitude else 0.0, if(axis==1) amplitude else 0.0) }
            repeat(3) { sample(if(axis==0) -amplitude else 0.0, if(axis==1) -amplitude else 0.0) }
            rest(4)
        }
        fun warm() { rest(40) }
    }
    @Test fun singleCompleteStrokeIsNotEnough() {
        val a=Trace(); a.warm(); a.stroke(); a.rest(50); assertEquals(0,a.hits)
    }
    @Test fun twoUnbrakedPeaksAreRejected() {
        val a=Trace(); a.warm()
        repeat(2) { repeat(3) { a.sample(25.0) }; a.rest(8) }
        assertEquals(0,a.hits)
    }
    @Test fun perpendicularStrokesAreRejected() {
        val a=Trace(); a.warm(); a.stroke(0); a.stroke(1); assertEquals(0,a.hits)
    }
    @Test fun cancellationDiscardsFirstStroke() {
        val a=Trace(); a.warm(); a.stroke(); a.detector.cancel(); a.stroke(); assertEquals(0,a.hits)
    }
    @Test fun dataGapDiscardsFirstStroke() {
        val a=Trace(); a.warm(); a.stroke(); a.t+=300_000_000L; a.stroke(); assertEquals(0,a.hits)
    }
    @Test fun reversedTimestampDiscardsFirstStroke() {
        val a=Trace(); a.warm(); a.stroke(); a.t-=100_000_000L; a.stroke(); assertEquals(0,a.hits)
    }
    @Test fun duplicateTimestampDiscardsFirstStroke() {
        val a=Trace(); a.warm(); a.stroke(); a.t-=20_000_000L; a.stroke(); assertEquals(0,a.hits)
    }
    @Test fun cooldownPreventsRepeatedToggle() {
        val a=Trace(); a.warm(); repeat(4) { a.stroke() }; assertEquals(1,a.hits)
        a.rest(60); a.stroke(); a.stroke(); assertEquals(2,a.hits)
    }
    @Test fun slowStrokesAndSeparatedStrokesAreRejected() {
        val a=Trace(); a.warm(); repeat(15) { a.sample(22.0) }; repeat(15) { a.sample(-22.0) }; a.rest(10)
        a.stroke(); a.rest(40); a.stroke(); assertEquals(0,a.hits)
    }
    @Test fun syntheticVibrationsAndWalkingAreRejected() {
        val a=Trace(); a.warm()
        repeat(300) { a.sample(if(it%2==0) 25.0 else -25.0) }
        repeat(300) { a.sample(kotlin.math.sin(it*0.3)*4, kotlin.math.cos(it*0.3)*3) }
        assertEquals(0,a.hits)
    }
    @Test fun sensitivityChangesAmplitudeNotTimingProtection() {
        val high=Trace(ChopDetector(100)); high.warm(); high.stroke(amplitude=12.0); high.stroke(amplitude=12.0)
        val low=Trace(ChopDetector(0)); low.warm(); low.stroke(amplitude=12.0); low.stroke(amplitude=12.0)
        assertEquals(1,high.hits); assertEquals(0,low.hits)
        assertEquals(0,ChopDetector(-20).sensitivity); assertEquals(100,ChopDetector(150).sensitivity)
    }
    @Test fun invalidSampleDiscardsCandidate() {
        val a=Trace(); a.warm(); a.stroke(); a.sample(Double.NaN); a.stroke(); assertEquals(0,a.hits)
    }
    @Test fun restGravityDoesNotTrigger() {
        val a=Trace(); a.rest(1000); assertEquals(0,a.hits)
    }
    @Test fun syntheticDoubleChopTriggersOnce() {
        val a=Trace(); a.warm(); a.stroke(); a.stroke(); a.rest(20)
        assertEquals(1, a.hits)
    }
}
