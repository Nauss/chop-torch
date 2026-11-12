// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Literal linear samples only. JVM admission controls, not Android/hardware validation. */
class PlanePipelineTest {
    private class Signal(val detector: PlaneLinearChopDetector=PlaneLinearChopDetector(100,true)) {
        val feedback=FeedbackWindow()
        var t=1_000_000_000L
        var hits=0
        fun feed(x: Double=0.0,y: Double=0.0,z: Double=0.0,age: Long=0) {
            if(!SampleFreshness.accepts(t,t+age)) detector.cancel()
            else if(feedback.sample(detector,t,x,y,z)) hits++
            t+=20_000_000L
        }
        fun quiet(n: Int) { repeat(n) { feed() } }
        fun stroke(x: Double=3.0,z: Double=0.0) { repeat(14) { val w=sin(2*PI*it/14); feed(w*x,0.0,w*z) } }
        fun pair(x: Double=3.0,z: Double=0.0) { stroke(x,z); quiet(4); stroke(x,z); quiet(4) }
        fun field(name: String): Long {
            val f=detector.javaClass.getDeclaredField(name); f.isAccessible=true
            return (f.get(detector) as Number).toLong()
        }
    }
    @Test fun suppressedSamplesClearBothSidesWithoutResettingCadence() {
        val s=Signal();s.quiet(45);s.stroke()
        s.feedback.start(s.t)
        repeat(13) {
            s.feed(if(it%2==0)20.0 else -20.0)
            assertEquals("suppressed sample cannot retain candidate history",0L,s.field("size"))
            assertEquals(s.t-20_000_000L,s.field("lastNs"))
            assertTrue(s.field("evaluationNs")>=s.t-40_000_000L)
        }
        assertEquals(0,s.hits)
        s.quiet(60);s.pair();s.quiet(80)
        assertEquals(1,s.hits)
    }
    @Test fun feedbackCannotCompleteAnAlreadyBufferedCandidate() {
        val s=Signal();s.quiet(45);s.stroke();s.quiet(4);s.stroke()
        assertEquals(0,s.hits)
        s.feedback.start(s.t)
        s.quiet(13)
        assertEquals("no acceptance inside haptic window",0,s.hits)
        s.quiet(80)
        assertEquals("no delayed haptic candidate",0,s.hits)
    }
    @Test fun staleAndFutureDeliveryCancelPartialHistoryThenRecover() {
        for(age in longArrayOf(250_000_001L,-1L)) {
            val s=Signal();s.quiet(45);s.stroke();s.quiet(4)
            s.feed(age=age);s.stroke();s.quiet(80)
            assertEquals(0,s.hits)
            s.pair();s.quiet(80);assertEquals(1,s.hits)
        }
        assertTrue(SampleFreshness.accepts(1_000_000_000L,1_250_000_000L))
    }
    @Test fun eachPairMustPassEvenWhenPooledPlaneEnergyWouldPass() {
        for(swap in listOf(false,true)) {
            fun run(enabled: Boolean): Int {
                val s=Signal(PlaneLinearChopDetector(100,enabled));s.quiet(45)
                // Both axes remain nearly collinear: baseline PCA must qualify.
                val first=if(swap)doubleArrayOf(3.0,3.3) else doubleArrayOf(3.0,2.4)
                val second=if(swap)doubleArrayOf(3.0,2.4) else doubleArrayOf(3.0,3.3)
                s.stroke(first[0],first[1]);s.quiet(4);s.stroke(second[0],second[1]);s.quiet(80)
                return s.hits
            }
            assertEquals("disabled baseline must qualify swap=$swap",1,run(false))
            assertEquals("individual pair veto swap=$swap",0,run(true))
        }
    }
    @Test fun planeVetoRetainsHistoryAndDoesNotStartCooldown() {
        val s=Signal();val baseline=Signal(PlaneLinearChopDetector(100,false))
        for(signal in listOf(s,baseline)) { signal.quiet(45);signal.pair(0.0,3.0);signal.quiet(4) }
        assertEquals("normal candidate qualifies without plane gate",1,baseline.hits)
        assertEquals(0,s.hits);assertEquals(0L,s.field("acceptedNs"))
        assertTrue("veto must not consume candidate history",s.field("size")>45)
        val vetoTime=s.t
        s.pair();s.quiet(4)
        assertEquals("eligible motion immediately after veto",1,s.hits)
        assertTrue("acceptance before a hypothetical veto cooldown ends",s.field("acceptedNs")-vetoTime<900_000_000L)
    }

    @Test fun proximityTransitionCancelsPartialLinearMotionAndRecoveryNeedsAWholePair() {
        val s=Signal();val proximity=ProximityGate(5.0)
        fun context(distance: Double) { if(proximity.sample(s.t,distance)) s.detector.cancel() }
        assertFalse(proximity.allowsGesture)
        context(5.0);assertTrue(proximity.allowsGesture)
        s.quiet(45);s.stroke();s.quiet(4)
        context(0.0);assertFalse(proximity.allowsGesture)
        assertEquals(0L,s.field("size"))
        s.t+=20_000_000L
        context(5.0);s.stroke();s.quiet(80)
        assertEquals(0,s.hits)
        s.pair();s.quiet(80);assertEquals(1,s.hits)
        proximity.reset();s.detector.cancel()
        assertFalse(proximity.allowsGesture);assertEquals(0L,s.field("size"))
    }

    @Test fun cachedPolicyNeverAuthorizesARecognizedLinearCommandAfterRevocation() {
        val detector=PlaneLinearChopDetector(100,true)
        val feedback=FeedbackWindow()
        var reason=0;var consent=true
        val policy=PolicyGate({consent},{reason})
        var t=1_000_000_000L;var recognized=0;var commands=0
        fun feed(x: Double=0.0) {
            if(!policy.allows(t)) detector.cancel()
            else if(feedback.sample(detector,t,x,0.0,0.0)) {
                recognized++
                // Permission changes after cached admission but before the command.
                reason=42
                if(policy.allows(t,force=true)) commands++
            }
            t+=20_000_000L
        }
        repeat(45){feed()}
        repeat(2){repeat(14){feed(3*sin(2*PI*it/14))};repeat(4){feed()}}
        repeat(80){feed()}
        assertEquals(1,recognized);assertEquals(0,commands)
        assertEquals(42,policy.blockedReason)
        reason=0;consent=false
        assertFalse(policy.allows(t,force=true))
    }
}
