package fr.nauss.choptorch

import kotlin.math.*

/** Fixed 1:1 mechanical prior. Literal LINEAR input; no centering, filtering or pose. */
object PlaneEnergy {
    fun accepts(ex: Double, ey: Double, ez: Double): Boolean {
        val total=ex+ey+ez
        return ex.isFinite() && ey.isFinite() && ez.isFinite() && total.isFinite() && total>0.0 && ex+ey>=ez
    }

    /** seconds is logical; values is the original fixed ring, starting at offset. */
    fun integral(seconds: DoubleArray, values: DoubleArray, offset: Int, n: Int, start: Double, end: Double): Double {
        if(n<2 || n>seconds.size || n>values.size || offset<0 || offset>=values.size ||
            !start.isFinite() || !end.isFinite() || start>=end || start<seconds[0] || end>seconds[n-1]) return Double.NaN
        var energy=0.0
        for(i in 0 until n-1) {
            val t0=seconds[i];val t1=seconds[i+1]
            if(!t0.isFinite() || !t1.isFinite() || t1<=t0) return Double.NaN
            val left=max(start,t0);val right=min(end,t1)
            if(right<=left) continue
            val x=values[(offset+i)%values.size];val y=values[(offset+i+1)%values.size]
            if(!x.isFinite() || !y.isFinite()) return Double.NaN
            val a=x+(y-x)*(left-t0)/(t1-t0)
            val b=x+(y-x)*(right-t0)/(t1-t0)
            energy+=(right-left)*(a*a+a*b+b*b)/3.0
        }
        return if(energy.isFinite()) energy else Double.NaN
    }
}
