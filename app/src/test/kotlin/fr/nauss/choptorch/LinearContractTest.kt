package fr.nauss.choptorch

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** True linear = R(theta)^T a_world, with no gravity component. */
class LinearContractTest {
    private class Core(level: Int = 100) {
        val type = Class.forName(System.getProperty("linear.test.class", "fr.nauss.choptorch.PlaneLinearChopDetector"))
        val instance = if (type.simpleName == "PlaneLinearChopDetector") type.getConstructor(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).newInstance(level, true) else type.getConstructor(Int::class.javaPrimitiveType).newInstance(level)
        val sample = type.getMethod("sample", Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
        fun feed(t: Long,x: Double,y: Double,z: Double) = sample.invoke(instance,t,x,y,z) as Boolean
        fun cancel() { type.getMethod("cancel").invoke(instance) }
        fun field(name: String): Any { val f=type.getDeclaredField(name);f.isAccessible=true;return f.get(instance) }
    }
    private class Signal(val core: Core = Core(), val step: Long=20_000_000L, val jitter: Boolean=false) {
        var now=1_000_000_000L; var hits=0; var samples=0
        fun feed(x: Double=0.0,y: Double=0.0,z: Double=0.0) {
            if(core.feed(now,x,y,z))hits++
            now+=if(jitter)step+((samples++%3)-1)*step/5 else step
        }
        fun quiet(s: Double) { val end=now+(s*1e9).toLong();while(now<end)feed() }
        fun stroke(a: Double=3.0,tilt: Double=0.0,axis: Int=0) {
            val start=now
            while(now-start<280_000_000L) {
                val wave=sin(2*PI*(now-start)/280_000_000.0)
                val angle=Math.toRadians(tilt)*wave
                // R is a rotation about the transverse axis; this is R^T * (a,0,0).
                val h=a*wave*cos(angle);val z=a*wave*sin(angle)
                if(axis==0)feed(h,0.0,z) else feed(0.0,h,z)
            }
        }
        fun pair(a: Double=3.0,tilt: Double=0.0,axis: Int=0) {stroke(a,tilt,axis);quiet(.08);stroke(a,tilt,axis)}
    }
    @Test fun directLinearEffortRejectsBelowFloorInsteadOfRawGravityConversion() {
        val s=Signal(Core(50));s.quiet(.9);s.pair(10.0);s.quiet(1.5)
        assertEquals("A10 linear is below unchanged effort14.4 at50",0,s.hits)
        val low=Signal(Core(0));low.quiet(.9);low.pair(24.0);low.quiet(1.5)
        assertEquals("A24 linear is below unchanged effort28.8 at0",0,low.hits)
    }
    @Test fun fifteenWeakPairsEveryTiltAxisRateAndJitter() {
        for(tilt in doubleArrayOf(0.0,5.0,20.0,40.0)) for(axis in 0..1)
            for(step in longArrayOf(10_000_000L,20_000_000L,40_000_000L)) for(jitter in listOf(false,true)) {
                val s=Signal(step=step,jitter=jitter);s.quiet(.9)
                repeat(15){s.pair(3.0,tilt,axis);s.quiet(1.5)}
                assertEquals("A3 tilt=$tilt axis=$axis step=$step jitter=$jitter",15,s.hits)
            }
    }
    @Test fun strongTwentyFourAtFifty() {
        for(tilt in doubleArrayOf(0.0,5.0,20.0,40.0))for(axis in 0..1)
            for(step in longArrayOf(10_000_000L,20_000_000L,40_000_000L))for(jitter in listOf(false,true)) {
                val s=Signal(Core(50),step,jitter);s.quiet(.9);s.pair(24.0,tilt,axis);s.quiet(1.5)
                assertEquals("strong tilt=$tilt axis=$axis step=$step jitter=$jitter",1,s.hits)
            }
    }
    @Test fun singlePrefixPlateauImpactQuietAndPureRotationStaySilent() {
        for(level in intArrayOf(0,50,100))for(kind in 0..5) {
            val s=Signal(Core(level));s.quiet(.9)
            when(kind) {
                0->s.stroke(24.0)
                1->{s.stroke(24.0);s.quiet(.08);repeat(6){s.feed(24*sin(PI*it/6))}}
                2->repeat(100){s.feed(19.0)}
                3->s.feed(30.0)
                4->s.quiet(3.0)
                5->{s.stroke(0.0,80.0);s.quiet(.08);s.stroke(0.0,80.0)}
            }
            s.quiet(2.0);assertEquals("level=$level negative=$kind",0,s.hits)
        }
    }
    @Test fun invalidGapBackwardsCancelAndRecovery() {
        for(kind in 0..8) {
            val s=Signal();s.quiet(.9);s.stroke();s.quiet(.08)
            when(kind) {
                0->s.feed(Double.NaN)
                1->s.feed(y=Double.POSITIVE_INFINITY)
                2->s.feed(z=160.001)
                3->{s.now+=120_000_001L;s.feed()}
                4->{s.now-=40_000_000L;s.feed()}
                5->assertFalse(s.core.feed(Long.MIN_VALUE,0.0,0.0,0.0))
                6->assertFalse(s.core.feed(0,0.0,0.0,0.0))
                7->s.core.cancel()
                8->{assertFalse(SampleFreshness.accepts(s.now,s.now+250_000_001L));s.core.cancel()}
            }
            s.stroke();s.quiet(1.5);assertEquals("invalid kind=$kind",0,s.hits)
            s.pair();s.quiet(1.5);assertEquals("recovery kind=$kind",1,s.hits)
        }
    }
    @Test fun fixedStorageCadenceCancelAndClockBoundary() {
        val c=Core();val arrays=c.type.declaredFields.filter{it.type.isArray}.map{it.isAccessible=true;it.get(c.instance)}
        var last=0L
        repeat(100_000) {
            c.feed(1_000_000_000L+it*1_000_000L,0.0,0.0,0.0)
            val evaluation=c.field("evaluationNs") as Long
            if(last!=0L&&evaluation!=last)assertTrue(evaluation-last>=40_000_000L)
            last=evaluation
        }
        val after=c.type.declaredFields.filter{it.type.isArray}.map{it.isAccessible=true;it.get(c.instance)}
        assertTrue(arrays.isNotEmpty());arrays.zip(after).forEach{(a,b)->assertSame(a,b);assertTrue(java.lang.reflect.Array.getLength(a)<=384)}
        c.cancel();assertEquals(last,c.field("evaluationNs"));assertEquals(0,c.field("size"))
        val s=Signal();s.now=Long.MAX_VALUE-4_000_000_000L;s.quiet(.9);s.pair();s.quiet(1.5);assertEquals(1,s.hits)
        assertFalse(s.core.feed(Long.MIN_VALUE,3.0,0.0,0.0))
        val reset=Signal(s.core);reset.quiet(.9);reset.pair();reset.quiet(1.5);assertEquals(0,reset.hits)
    }
    @Test fun exactPolynomialAndClampingInNewUnits() {
        val name=if(System.getProperty("linear.test.class","").endsWith(".DirectionalChopDetector"))"radialFloor" else "linearFloor"
        for(level in listOf(Int.MIN_VALUE,-1,0,25,50,75,100,101,Int.MAX_VALUE)) {
            val u=(level.coerceIn(0,100)-50)/50.0
            val expected=14.4-13.2*u+1.2*u*u
            assertEquals("linear effort at $level",expected,Core(level).field(name) as Double,0.0)
        }
    }
}
