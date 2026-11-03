package fr.nauss.choptorch

import kotlin.math.abs
import kotlin.math.sqrt

/** Experimental acceleration-only recognizer; constants require physical calibration.
 * Each stroke requires sustained acceleration, opposite braking, then quiet.
 * Input: raw accelerometer m/s² with monotonic nanosecond sensor timestamps.
 */
class ChopDetector(sensitivity: Int = 50) {
    val sensitivity = sensitivity.coerceIn(0, 100)
    private val threshold = 18.0 - this.sensitivity * 0.10
    private var last = 0L
    private var warmUntil = 0L
    private var cooldownUntil = 0L
    private var gravity = doubleArrayOf(0.0, 0.0, 0.0)
    private var phase = 0
    private var began = 0L
    private var phaseAt = 0L
    private var quietAt = 0L
    private var direction = doubleArrayOf(0.0, 0.0, 0.0)
    private var firstDirection: DoubleArray? = null
    private var firstAt = 0L

    fun cancel() { phase = 0; firstDirection = null; quietAt = 0L }

    fun sample(timestampNs: Long, x: Double, y: Double, z: Double): Boolean {
        val v = doubleArrayOf(x, y, z)
        if (timestampNs <= 0 || v.any { !it.isFinite() || abs(it) > 160.0 }) {
            cancel(); last = 0L; return false
        }
        if (last == 0L || timestampNs <= last || timestampNs - last > 120_000_000L) {
            cancel(); gravity = v; last = timestampNs
            warmUntil = timestampNs + 500_000_000L
            return false
        }
        val dt = (timestampNs - last) / 1e9
        last = timestampNs
        val alpha = dt / (0.6 + dt)
        val linear = DoubleArray(3) { i ->
            gravity[i] += alpha * (v[i] - gravity[i])
            v[i] - gravity[i]
        }
        if (timestampNs < warmUntil || timestampNs < cooldownUntil) { cancel(); return false }
        if (firstDirection != null && timestampNs - firstAt > 650_000_000L) cancel()
        val magnitude = sqrt(linear.sumOf { it * it })
        val dot = linear.indices.sumOf { linear[it] * direction[it] }
        val elapsed = timestampNs - phaseAt
        when (phase) {
            0 -> if (magnitude >= threshold) {
                direction = DoubleArray(3) { linear[it] / magnitude }
                began = timestampNs; phaseAt = timestampNs; phase = 1
            }
            1 -> {
                if (elapsed > 160_000_000L) cancel()
                else if (dot <= -threshold * 0.65) {
                    if (elapsed < 40_000_000L) cancel()
                    else { phase = 2; phaseAt = timestampNs }
                } else if (dot < threshold * 0.35) cancel()
            }
            2 -> {
                if (elapsed > 180_000_000L) cancel()
                else if (magnitude < threshold * 0.5) {
                    if (elapsed < 40_000_000L) cancel()
                    else { phase = 3; quietAt = timestampNs }
                } else if (dot > -threshold * 0.30) cancel()
            }
            3 -> {
                if (magnitude >= threshold * 0.5) cancel()
                else if (timestampNs - quietAt >= 40_000_000L) {
                    val previous = firstDirection
                    val interval = began - firstAt
                    val aligned = previous != null && previous.indices.sumOf { previous[it] * direction[it] } >= 0.80
                    phase = 0
                    if (aligned && interval in 150_000_000L..600_000_000L) {
                        firstDirection = null
                        cooldownUntil = timestampNs + 900_000_000L
                        return true
                    }
                    firstDirection = direction.copyOf(); firstAt = began
                }
            }
        }
        return false
    }
}
