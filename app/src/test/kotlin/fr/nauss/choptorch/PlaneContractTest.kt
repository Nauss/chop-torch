package fr.nauss.choptorch

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** New plane contract, explicitly NOT a replacement for the unchanged raw tests. */
class PlaneContractTest {
    private fun hits(x: Double,y: Double,z: Double): Int {
        val type=Class.forName(System.getProperty("linear.test.class","fr.nauss.choptorch.PlaneLinearChopDetector"))
        val core=if(type.simpleName=="PlaneLinearChopDetector") type.getConstructor(Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).newInstance(100,true) else type.getConstructor(Int::class.javaPrimitiveType).newInstance(100)
        val sample=type.getMethod("sample",Long::class.javaPrimitiveType,Double::class.javaPrimitiveType,Double::class.javaPrimitiveType,Double::class.javaPrimitiveType)
        var now=1_000_000_000L;var count=0
        fun feed(w: Double) { if(sample.invoke(core,now,w*x,w*y,w*z) as Boolean)count++;now+=20_000_000L }
        repeat(45){feed(0.0)}
        repeat(2){ repeat(14){feed(sin(2*PI*it/14))};repeat(4){feed(0.0)} }
        repeat(100){feed(0.0)}
        return count
    }
    @Test fun strongNormalZIsNewVisibleExclusion() {assertEquals("normal-to-screen deliberately excluded by NEW contract",0,hits(0.0,0.0,24.0))}
    @Test fun inPlaneHeadingAndSignSymmetry() {
        for(degrees in listOf(0,17,45,90,137,270))for(sign in listOf(-1,1)) {
            val a=Math.toRadians(degrees.toDouble())
            assertEquals("heading=$degrees sign=$sign",1,hits(sign*3*cos(a),sign*3*sin(a),0.0))
        }
    }
}
