// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import kotlin.math.*

/** Private LINEAR-NATIVE-1: actual TYPE_LINEAR_ACCELERATION in m/s^2, zero centered.
 * Peak linear magnitude effort, not raw gravity conversion or norm range.
 * Bounded directional-window hypothesis, not a field-calibrated intent classifier.
 * Production requires ProximityGate as well as consent, freshness and feedback admission.
 * Single-worker use. Storage is fixed; sample() allocates no vectors, collections or logs.
 */
class PlaneLinearChopDetector @JvmOverloads constructor(sensitivity: Int = 50, private val planeEnabled: Boolean = false) {
    private val times = LongArray(384)
    private val xs = DoubleArray(384)
    private val ys = DoubleArray(384)
    private val zs = DoubleArray(384)
    private val norms = DoubleArray(384)
    private val projected = DoubleArray(384)
    private val seconds = DoubleArray(384)
    private val covariance = DoubleArray(9)
    private val axes = DoubleArray(9)
    private val first = IntArray(4)
    private val last = IntArray(4)
    private val signs = IntArray(4)
    private val counts = IntArray(4)
    private val starts = DoubleArray(4)
    private val ends = DoubleArray(4)
    private val peaks = DoubleArray(4)
    private val energies = DoubleArray(4)
    private val windows = longArrayOf(700_000_000L, 900_000_000L, 1_150_000_000L, 1_450_000_000L)
    // Recenter the user's accepted former minimum at 50, without migrating saved settings.
    // Engineering effort proxy: 28.8 / 14.4 / 2.4 at 0 / 50 / 100, smoothly decreasing.
    private val linearFloor = run {
        val u = (sensitivity.coerceIn(0, 100) - 50) / 50.0
        val evidence = 14.4 - 13.2 * u + 1.2 * u * u
        evidence
    }
    private var head = 0
    private var size = 0
    private var lastNs = 0L
    private var evaluationNs = 0L
    private var acceptedNs = 0L

    /** Consume history, but retain cadence and debounce (including during haptic suppression). */
    fun cancel() { head = 0; size = 0 }

    fun sample(timestampNs: Long, x: Double, y: Double, z: Double): Boolean {
        if (timestampNs <= 0 || !x.isFinite() || !y.isFinite() || !z.isFinite() ||
            abs(x) > 160 || abs(y) > 160 || abs(z) > 160 ||
            (lastNs > 0 && (timestampNs <= lastNs || timestampNs - lastNs > 120_000_000L))) {
            cancel(); lastNs = timestampNs; return false
        }
        lastNs = timestampNs
        val slot: Int
        if (size == times.size) { slot = head; head = (head + 1) % times.size }
        else { slot = index(size); size++ }
        times[slot] = timestampNs; xs[slot] = x; ys[slot] = y; zs[slot] = z
        // Elapsed comparisons avoid overflowing absolute deadlines near Long.MAX_VALUE.
        if (acceptedNs != 0L && (timestampNs < acceptedNs || timestampNs - acceptedNs < 900_000_000L)) return false
        if (evaluationNs != 0L && (timestampNs < evaluationNs || timestampNs - evaluationNs < 40_000_000L)) return false
        evaluationNs = timestampNs
        if (size < 8 || timestampNs - times[head] < windows[0]) return false
        for (duration in windows) {
            if (timestampNs - times[head] < duration) continue
            var begin = 0
            while (begin < size && timestampNs - times[index(begin)] > duration) begin++
            if (size - begin >= 8 && evaluate(begin)) {
                cancel(); acceptedNs = timestampNs; return true
            }
        }
        return false
    }

    private fun index(offset: Int): Int = (head + offset) % times.size

    private fun evaluate(begin: Int): Boolean {
        val n = size - begin
        val origin = times[index(begin)]
        var minNorm = Double.POSITIVE_INFINITY
        var maxNorm = 0.0
        var bx = 0.0; var by = 0.0; var bz = 0.0; var baselineCount = 0
        var mx = 0.0; var my = 0.0; var mz = 0.0
        for (i in 0 until n) {
            val k = index(begin + i)
            val dt = times[k] - origin
            seconds[i] = dt / 1e9
            val x = xs[k]; val y = ys[k]; val z = zs[k]
            val norm = sqrt(x*x + y*y + z*z)
            norms[i] = norm; minNorm = min(minNorm, norm); maxNorm = max(maxNorm, norm)
            mx += x; my += y; mz += z
            if (dt <= 60_000_000L) { bx += x; by += y; bz += z; baselineCount++ }
        }
        if (maxNorm < linearFloor) return false
        bx /= baselineCount; by /= baselineCount; bz /= baselineCount
        mx /= n; my /= n; mz /= n
        covariance.fill(0.0)
        for (i in 0 until n) {
            val k = index(begin + i)
            val x = xs[k] - mx; val y = ys[k] - my; val z = zs[k] - mz
            covariance[0] += x*x; covariance[1] += x*y; covariance[2] += x*z
            covariance[4] += y*y; covariance[5] += y*z; covariance[8] += z*z
        }
        covariance[3] = covariance[1]; covariance[6] = covariance[2]; covariance[7] = covariance[5]
        val trace = covariance[0] + covariance[4] + covariance[8]
        if (trace <= 1e-9) return false
        val principal = principalAxis()
        if (covariance[principal*3 + principal] / trace < .60) return false
        val ax = axes[principal]; val ay = axes[3 + principal]; val az = axes[6 + principal]
        var peak = 0.0
        for (i in 0 until n) {
            val k = index(begin + i)
            val value = (xs[k] - bx)*ax + (ys[k] - by)*ay + (zs[k] - bz)*az
            projected[i] = value; peak = max(peak, abs(value))
        }
        val threshold = max(.10, .18 * peak)
        if (peak < .25) return false
        var groups = 0
        for (i in 0 until n) {
            val y = projected[i]
            if (abs(y) < threshold) continue
            val sign = if (y > 0) 1 else -1
            if (groups == 0 || sign != signs[groups-1] || seconds[i] - ends[groups-1] > .12) {
                // A fifth group cannot be deleted or merged to rescue a pattern.
                if (groups == 4) return false
                val g = groups++
                signs[g] = sign; starts[g] = seconds[i]; ends[g] = seconds[i]
                peaks[g] = abs(y); energies[g] = 0.0; counts[g] = 1; first[g] = i; last[g] = i
            } else {
                val g = groups-1
                energies[g] += (seconds[i] - ends[g])*y*y
                ends[g] = seconds[i]; peaks[g] = max(peaks[g], abs(y)); counts[g]++; last[g] = i
            }
        }
        if (groups != 4) return false
        for (g in 0..3) {
            val f = first[g]; val l = last[g]; val sign = signs[g]
            if (f > 0) {
                val before = sign * projected[f-1]; val current = sign * projected[f]
                if (before < threshold && current > before)
                    starts[g] = seconds[f-1] + (threshold-before)/(current-before)*(seconds[f]-seconds[f-1])
            }
            if (l+1 < n) {
                val current = sign * projected[l]; val after = sign * projected[l+1]
                if (after < threshold && current > after)
                    ends[g] = seconds[l] + (current-threshold)/(current-after)*(seconds[l+1]-seconds[l])
            }
            if (counts[g] == 1) energies[g] = (ends[g]-starts[g])*peaks[g]*peaks[g]*.5
            if (ends[g]-starts[g] < .015) return false
        }
        if (starts[0]-seconds[0] < .06 || seconds[n-1]-ends[3] < .06) return false
        for (g in 0..2) if (signs[g] == signs[g+1] || starts[g+1]-ends[g] > .25) return false
        var low = peaks[0]; var high = peaks[0]
        for (g in 1..3) { low = min(low, peaks[g]); high = max(high, peaks[g]) }
        if (low/high < .18) return false
        val span = ends[3]-starts[0]
        if (span < .24 || span > 1.2) return false
        val e1 = energies[0]+energies[1]; val e2 = energies[2]+energies[3]
        if (min(e1,e2)/max(max(e1,e2),1e-9) < .20) return false
        for (pair in 0..1) {
            low = Double.POSITIVE_INFINITY; high = 0.0
            for (i in first[pair*2]..last[pair*2+1]) { low = min(low,norms[i]); high = max(high,norms[i]) }
            if (high < linearFloor) return false
        }
        // This is a NEW normal-axis limitation, not an owner/OEM requirement.
        // Veto before acceptance clear/cooldown; a failed window leaves later windows live.
        if (planeEnabled) {
            val offset = index(begin)
            for (pair in 0..1) {
                val g = pair*2
                val ex = PlaneEnergy.integral(seconds,xs,offset,n,starts[g],ends[g]) +
                    PlaneEnergy.integral(seconds,xs,offset,n,starts[g+1],ends[g+1])
                val ey = PlaneEnergy.integral(seconds,ys,offset,n,starts[g],ends[g]) +
                    PlaneEnergy.integral(seconds,ys,offset,n,starts[g+1],ends[g+1])
                val ez = PlaneEnergy.integral(seconds,zs,offset,n,starts[g],ends[g]) +
                    PlaneEnergy.integral(seconds,zs,offset,n,starts[g+1],ends[g+1])
                if (!PlaneEnergy.accepts(ex,ey,ez)) return false
            }
        }
        return true
    }

    /** Symmetric 3x3 Jacobi diagonalization, bounded to 32 rotations, all axes seeded.
     * Unlike a one-coordinate power seed this cannot miss an orthogonal principal axis.
     */
    private fun principalAxis(): Int {
        axes.fill(0.0); axes[0] = 1.0; axes[4] = 1.0; axes[8] = 1.0
        for (iteration in 0 until 32) {
            var p = 0; var q = 1; var largest = abs(covariance[1])
            if (abs(covariance[2]) > largest) { p = 0; q = 2; largest = abs(covariance[2]) }
            if (abs(covariance[5]) > largest) { p = 1; q = 2; largest = abs(covariance[5]) }
            if (largest <= 1e-14 * max(1.0, covariance[0]+covariance[4]+covariance[8])) break
            val pp = covariance[p*3+p]; val qq = covariance[q*3+q]; val pq = covariance[p*3+q]
            val angle = .5 * atan2(2*pq, qq-pp)
            val c = cos(angle); val s = sin(angle)
            for (k in 0..2) {
                if (k != p && k != q) {
                    val kp = covariance[k*3+p]; val kq = covariance[k*3+q]
                    covariance[k*3+p] = c*kp-s*kq; covariance[p*3+k] = covariance[k*3+p]
                    covariance[k*3+q] = s*kp+c*kq; covariance[q*3+k] = covariance[k*3+q]
                }
                val vp = axes[k*3+p]; val vq = axes[k*3+q]
                axes[k*3+p] = c*vp-s*vq; axes[k*3+q] = s*vp+c*vq
            }
            covariance[p*3+p] = c*c*pp-2*s*c*pq+s*s*qq
            covariance[q*3+q] = s*s*pp+2*s*c*pq+c*c*qq
            covariance[p*3+q] = 0.0; covariance[q*3+p] = 0.0
        }
        var principal = 0
        if (covariance[4] > covariance[0]) principal = 1
        if (covariance[8] > covariance[principal*3+principal]) principal = 2
        return principal
    }
}
