package fr.nauss.choptorch

import kotlin.math.abs
import kotlin.math.sqrt

/** Experimental acceleration-only recognizer; constants require physical calibration.
 * Three fixed segmentation scales share one filter and global cooldown. Each pair
 * requires sustained acceleration, opposite braking, then axial release on one scale.
 * Input: raw accelerometer m/s² with monotonic nanosecond sensor timestamps.
 */
class ChopDetector @JvmOverloads constructor(sensitivity: Int = 50, private val observer: Observer? = null) {
    /** Transition evidence aggregated over THREE scales, not intent labels or accuracy.
     * No samples retained. The optional observer runs synchronously on the caller.
     */
    fun interface Observer { fun onEvent(event: Event) }
    enum class Event {
        INVALID_SAMPLE, DISCONTINUITY, ARMED, BRAKING, ACCELERATION_TIMEOUT,
        REVERSAL_TOO_EARLY, ACCELERATION_SUPPORT_SHORT, BRAKING_TIMEOUT,
        STROKE_TIMEOUT, BRAKING_SUPPORT_SHORT, AMPLITUDE_LOW,
        FIRST_STROKE, FIRST_EXPIRED, PAIR_DIRECTION, PAIR_INTERVAL, RECOGNIZED
    }
    val sensitivity = sensitivity.coerceIn(0, 100)
    // Fixed scales keep existing valid candidates when sensitivity increases.
    // Only completed-stroke amplitude evidence depends on the slider.
    val amplitudeThreshold = if (this.sensitivity <= 50) 18.0 - this.sensitivity * 0.10
        else 13.0 - (this.sensitivity - 50) * 0.17
    private val scales = listOf(StrokeScale(2.5), StrokeScale(8.0), StrokeScale(13.0))
    private var last = 0L
    private var warmUntil = 0L
    private var cooldownUntil = 0L
    private var gravity = doubleArrayOf(0.0, 0.0, 0.0)

    fun cancel() { scales.forEach { it.cancel() } }
    private fun report(event: Event) { observer?.onEvent(event) }

    fun sample(timestampNs: Long, x: Double, y: Double, z: Double): Boolean {
        val v = doubleArrayOf(x, y, z)
        if (timestampNs <= 0 || v.any { !it.isFinite() || abs(it) > 160.0 }) {
            cancel(); report(Event.INVALID_SAMPLE); last = 0L; return false
        }
        if (last == 0L || timestampNs <= last || timestampNs - last > 120_000_000L) {
            cancel(); report(Event.DISCONTINUITY); gravity = v; last = timestampNs
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
        val magnitude = sqrt(linear.sumOf { it * it })
        for (scale in scales) {
            if (scale.sample(timestampNs, stepNs, linear, magnitude)) {
                cooldownUntil = timestampNs + 900_000_000L
                cancel(); report(Event.RECOGNIZED); return true
            }
        }
        return false
    }

    /** Each pair completes on ONE scale; never combine halves from different axes.
     * Events aggregate three bounded scales, not three distinct physical gestures.
     */
    private inner class StrokeScale(private val arm: Double) {
        private val accelerationSupport = maxOf(2.0, arm * 0.35)
        private val brakeEntry = maxOf(2.5, arm * 0.65)
        private val brakingSupport = maxOf(2.0, arm * 0.30)
        private var phase = 0
        private var began = 0L
        private var phaseAt = 0L
        private var supportNs = 0L
        private var supportedLast = false
        private var direction = doubleArrayOf(0.0, 0.0, 0.0)
        private var accelerationPeak = 0.0
        private var brakingPeak = 0.0
        private var firstDirection: DoubleArray? = null
        private var firstAt = 0L
        fun cancel() { phase = 0; firstDirection = null; supportNs = 0L; supportedLast = false }
        private fun reject(event: Event) { cancel(); report(event) }
        private fun start(t: Long, linear: DoubleArray, magnitude: Double) {
            direction = DoubleArray(3) { linear[it] / magnitude }
            began = t; phaseAt = t; phase = 1
            supportNs = 0L; supportedLast = true
            accelerationPeak = magnitude; brakingPeak = 0.0
            report(Event.ARMED)
        }

        fun sample(timestampNs: Long, stepNs: Long, linear: DoubleArray, magnitude: Double): Boolean {
            // Expire an idle first stroke, not a second stroke already in flight.
            // Recognition still checks the start-to-start interval (150–600 ms).
            if (phase == 0 && firstDirection != null && timestampNs - firstAt > 650_000_000L) reject(Event.FIRST_EXPIRED)
            val dot = linear.indices.sumOf { linear[it] * direction[it] }
            val elapsed = timestampNs - phaseAt
            when (phase) {
                0 -> if (magnitude >= arm) start(timestampNs, linear, magnitude)
                1 -> {
                    accelerationPeak = maxOf(accelerationPeak, dot)
                    val supported = dot >= accelerationSupport
                    if (supported && supportedLast) supportNs += stepNs
                    supportedLast = supported
                    // Both half-stroke guards include the threshold ramp.
                    // Old160/180 ms guards lost400 ms strokes at high sensitivity.
                    if (elapsed > 240_000_000L) reject(Event.ACCELERATION_TIMEOUT)
                    else if (dot <= -brakeEntry) {
                        if (elapsed < 40_000_000L) reject(Event.REVERSAL_TOO_EARLY)
                        else if (supportNs < 40_000_000L) reject(Event.ACCELERATION_SUPPORT_SHORT)
                        else {
                            phase = 2; phaseAt = timestampNs; supportNs = 0L; supportedLast = true; brakingPeak = -dot
                            report(Event.BRAKING)
                        }
                    }
                    // Continuous reversal traverses zero; keep until braking/deadline.
                }
                2 -> {
                    brakingPeak = maxOf(brakingPeak, -dot)
                    val supported = dot <= -brakingSupport
                    if (supported && supportedLast) supportNs += stepNs
                    supportedLast = supported
                    if (elapsed > 240_000_000L) reject(Event.BRAKING_TIMEOUT)
                    else if (timestampNs - began > 440_000_000L) reject(Event.STROKE_TIMEOUT)
                    else if (!supported) {
                        if (supportNs < 40_000_000L) { reject(Event.BRAKING_SUPPORT_SHORT); return false }
                        if (accelerationPeak < amplitudeThreshold || brakingPeak < amplitudeThreshold * 0.5) {
                            reject(Event.AMPLITUDE_LOW); return false
                        }
                        // Axial release, not artificial 3-D rest between strokes.
                        val previous = firstDirection
                        val interval = began - firstAt
                        val aligned = previous != null && previous.indices.sumOf { previous[it] * direction[it] } >= 0.80
                        phase = 0
                        if (aligned && interval in 150_000_000L..600_000_000L) return true
                        if (previous == null) report(Event.FIRST_STROKE)
                        else {
                            if (!aligned) report(Event.PAIR_DIRECTION)
                            if (interval !in 150_000_000L..600_000_000L) report(Event.PAIR_INTERVAL)
                        }
                        firstDirection = direction.copyOf(); firstAt = began
                        // Release can already start the next thrust on this scale.
                        if (dot >= arm) start(timestampNs, linear, magnitude)
                    }
                }
            }
            return false
        }
    }
}
