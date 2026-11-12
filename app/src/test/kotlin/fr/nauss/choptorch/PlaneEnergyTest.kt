package fr.nauss.choptorch

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PlaneEnergyTest {
    @Test fun axesZeroUnknownEqualityAndSignCrossing() {
        val t=doubleArrayOf(0.0,1.0,2.0)
        val v=doubleArrayOf(-1.0,0.0,1.0)
        assertEquals(2.0/3,PlaneEnergy.integral(t,v,0,3,0.0,2.0),1e-15)
        assertEquals(1.0/12,PlaneEnergy.integral(t,v,0,3,.5,1.5),1e-15)
        assertEquals(2.0/3,PlaneEnergy.integral(t,doubleArrayOf(0.0,1.0,0.0),0,3,0.0,2.0),1e-15)
        val e=PlaneEnergy.integral(t,doubleArrayOf(3.0,3.0,3.0),0,3,.25,1.75)
        assertEquals(13.5,e,0.0)
        assertTrue(PlaneEnergy.accepts(e,0.0,0.0));assertTrue(PlaneEnergy.accepts(0.0,e,0.0))
        assertFalse(PlaneEnergy.accepts(0.0,0.0,e));assertFalse(PlaneEnergy.accepts(0.0,0.0,0.0))
        assertFalse(PlaneEnergy.accepts(Double.NaN,1.0,0.0))
        assertFalse(PlaneEnergy.accepts(Double.POSITIVE_INFINITY,1.0,0.0))
        assertTrue(PlaneEnergy.accepts(3.0,1.0,4.0));assertFalse(PlaneEnergy.accepts(3.0,1.0,Math.nextUp(4.0)))
        assertTrue(PlaneEnergy.integral(t,v,0,3,-.1,1.0).isNaN())
    }
    @Test fun clippingRingGapAndIndependentSimpson() {
        val t=doubleArrayOf(0.0,1.0,2.0,3.0,4.0)
        val z=doubleArrayOf(100.0,0.0,0.0,0.0,0.0)
        assertEquals(0.0,PlaneEnergy.integral(t,z,3,5,0.0,1.0)+PlaneEnergy.integral(t,z,3,5,3.0,4.0),0.0)
        for(seed in 0..29) {
            val times=doubleArrayOf(0.0,.07,.19,.21,.5)
            val values=DoubleArray(5){sin(seed+it.toDouble())*3}
            var expected=0.0
            for(i in 0..3) {
                val l=max(.013,times[i]);val r=min(.49,times[i+1])
                if(r<=l)continue
                fun v(q:Double)=values[i]+(values[i+1]-values[i])*(q-times[i])/(times[i+1]-times[i])
                expected+=(r-l)/6*(v(l).pow(2)+4*v((l+r)/2).pow(2)+v(r).pow(2))
            }
            assertEquals(expected,PlaneEnergy.integral(times,values,0,5,.013,.49),1e-13)
        }
    }
}
