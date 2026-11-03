package fr.nauss.choptorch

import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorEvidenceTest {
    @Test fun unbrakedCandidateReportsTimeoutRatherThanARecognizedGesture() {
        val evidence = mutableListOf<ChopDetector.Event>()
        val d = ChopDetector(100, ChopDetector.Observer { evidence.add(it) })
        var t = 1_000_000_000L
        repeat(40) { d.sample(t, 0.0, 0.0, 9.81); t += 20_000_000L }
        repeat(20) { d.sample(t, 12.0, 0.0, 9.81); t += 20_000_000L }
        assertTrue(evidence.contains(ChopDetector.Event.ACCELERATION_TIMEOUT))
        assertTrue(!evidence.contains(ChopDetector.Event.RECOGNIZED))
    }
}
