package fr.nauss.choptorch

import kotlin.math.abs
import kotlin.math.sqrt

/** Experimental acceleration-only recognizer; constants require physical calibration.
 * Each stroke requires sustained acceleration, opposite braking, then axial release.
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
    private var supportNs = 0L
    private var supportedLast = false
    private var direction = doubleArrayOf(0.0, 0.0, 0.0)
    private var firstDirection: DoubleArray? = null
    private var firstAt = 0L

    fun cancel() { phase = 0; firstDirection = null; supportNs = 0L; supportedLast = false }

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
        val stepNs = timestampNs - last
        val dt = stepNs / 1e9
        last = timestampNs
        val alpha = dt / (0.6 + dt)
        val linear = DoubleArray(3) { i ->
            gravity[i] += alpha * (v[i] - gravity[i])
            v[i] - gravity[i]
        }
        if (timestampNs < warmUntil || timestampNs < cooldownUntil) { cancel(); return false }
        // Expire an idle first stroke, not a second stroke already in flight.
        // Recognition still checks the start-to-start interval (150–600 ms).
        if (phase == 0 && firstDirection != null && timestampNs - firstAt > 650_000_000L) cancel()
        val magnitude = sqrt(linear.sumOf { it * it })
        val dot = linear.indices.sumOf { linear[it] * direction[it] }
        val elapsed = timestampNs - phaseAt
        when (phase) {
            0 -> if (magnitude >= threshold) {
                direction = DoubleArray(3) { linear[it] / magnitude }
                began = timestampNs; phaseAt = timestampNs; phase = 1
                supportNs = 0L; supportedLast = true
            }
            1 -> {
                val supported = dot >= threshold * 0.35
                if (supported && supportedLast) supportNs += stepNs
                supportedLast = supported
                if (elapsed > 160_000_000L) cancel()
                else if (dot <= -threshold * 0.65) {
                    if (elapsed < 40_000_000L || supportNs < 40_000_000L) cancel()
                    else { phase = 2; phaseAt = timestampNs; supportNs = 0L; supportedLast = true }
                }
                // A continuous reversal traverses zero: keep the candidate until
                // opposite braking arrives or the existing phase deadline expires.
            }
            2 -> {
                val supported = dot <= -threshold * 0.30
                if (supported && supportedLast) supportNs += stepNs
                supportedLast = supported
                if (elapsed > 180_000_000L) cancel()
                else if (!supported) {
                    if (supportNs < 40_000_000L) { cancel(); return false }
                    // Return along the stroke axis, not 40 ms of absolute 3-D
                    // rest. A continuous double chop need not pause mid-gesture.
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
                    // The release sample may already start the next thrust.
                    if (dot >= threshold) {
                        direction = DoubleArray(3) { linear[it] / magnitude }
                        began = timestampNs; phaseAt = timestampNs; phase = 1
                        supportNs = 0L; supportedLast = true
                    }
                }
            }
        }
        return false
    }
}
