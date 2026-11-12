// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/** Analytic sensor signals, not recordings or a claim about human accuracy. */
class GestureReliabilityTest {
    @Test fun pureGravityTiltCannotBecomeADoubleChop() {
        val detector=DirectionalChopDetector(50)
        var t=1_000_000_000L
        var hits=0
        fun feed(angle: Double) {
            if(detector.sample(t,-9.81*sin(angle),0.0,9.81*cos(angle))) hits++
            t+=20_000_000L
        }
        repeat(50) { feed(0.0) }
        repeat(2) {
            for(ms in 0 until 280 step 20) {
                feed(Math.toRadians(5.0)*sin(2.0*Math.PI*ms/280.0))
            }
            repeat(4) { feed(0.0) }
        }
        repeat(50) { feed(0.0) }
        assertEquals("Rotating constant-magnitude gravity is not a translational double chop",0,hits)
    }
}
