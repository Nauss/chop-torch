// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import org.junit.Assert.*
import org.junit.Test

class FeedbackWindowTest {
    @Test fun feedbackDeadlineSaturatesRatherThanWrapping() {
        val window=FeedbackWindow()
        window.start(Long.MAX_VALUE-100)
        assertTrue(window.suppresses(Long.MAX_VALUE))
    }
    @Test fun directionalSuppressionClearsHistoryButKeepsClockAndEvaluationCadence() {
        val detector=DirectionalChopDetector(100) // Preserve the old 50 evidence floor.
        val window=FeedbackWindow()
        var t=1_000_000_000L
        repeat(50) { window.sample(detector,t,0.0,0.0,9.81); t+=20_000_000L }
        window.start(t)
        repeat(13) {
            assertFalse(window.sample(detector,t,if(it%2==0) 20.0 else -20.0,0.0,9.81))
            t+=20_000_000L
        }
        fun field(name: String): Long {
            val f=detector.javaClass.getDeclaredField(name); f.isAccessible=true
            return f.getLong(detector)
        }
        assertEquals(t-20_000_000L,field("lastNs"))
        assertTrue(field("evaluationNs")>=t-40_000_000L)
        repeat(60) { assertFalse(window.sample(detector,t,0.0,0.0,9.81)); t+=20_000_000L }
        var hits=0
        repeat(2) {
            for(ms in 0 until 280 step 20) {
                if(window.sample(detector,t,3*kotlin.math.sin(2*Math.PI*ms/280),0.0,9.81)) hits++
                t+=20_000_000L
            }
            repeat(4) {
                if(window.sample(detector,t,0.0,0.0,9.81)) hits++
                t+=20_000_000L
            }
        }
        repeat(80) {
            if(window.sample(detector,t,0.0,0.0,9.81)) hits++
            t+=20_000_000L
        }
        assertEquals(1,hits) // Synthetic transient only, not motor validation.
    }

    @Test fun inactiveWithoutAFeedbackRequest() {
        assertFalse(FeedbackWindow().suppresses(1_000_000_000L))
    }
    @Test fun suppressesTransientAndBufferedSamplesThenExpires() {
        val window=FeedbackWindow()
        window.start(1_000_000_000L)
        assertTrue(window.suppresses(999_000_000L))
        assertTrue(window.suppresses(1_000_000_000L))
        assertTrue(window.suppresses(1_250_000_000L))
        assertFalse(window.suppresses(1_250_000_001L))
        assertFalse(window.suppresses(10_000_000_000L))
    }
    @Test fun lateConfirmationStartsANewBoundedWindow() {
        val window=FeedbackWindow()
        window.start(1_000_000_000L)
        window.start(2_800_000_000L)
        assertTrue(window.suppresses(3_000_000_000L))
        assertFalse(window.suppresses(3_050_000_001L))
    }
    @Test fun suppressionKeepsTheFilterClockContinuousWithoutRecognitions() {
        val events=mutableListOf<ChopDetector.Event>()
        val detector=ChopDetector(100) { events += it }
        val window=FeedbackWindow()
        var t=1_000_000_000L
        repeat(90) {
            assertFalse(window.sample(detector,t,0.0,0.0,9.81))
            t+=10_000_000L
        }
        window.start(t)
        detector.cancel()
        repeat(26) {
            assertFalse(window.sample(detector,t,if(it%2==0) 20.0 else -20.0,0.0,9.81))
            t+=10_000_000L
        }
        assertFalse(window.sample(detector,t,0.0,0.0,9.81))
        assertEquals(1,events.count { it==ChopDetector.Event.DISCONTINUITY })
        assertEquals(0,events.count { it==ChopDetector.Event.RECOGNIZED })
    }
}
