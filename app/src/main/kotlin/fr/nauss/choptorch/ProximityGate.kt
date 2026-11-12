// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

/** Persistent on-change state: silence is not evidence of either near or far.
 * Invalid/late readings fail closed until a strictly newer valid callback arrives.
 */
class ProximityGate(maximumRange: Double) {
    enum class State { UNKNOWN, NEAR, FAR }
    val available = maximumRange.isFinite() && maximumRange > 0.0
    private val range = maximumRange
    private val nearThreshold = minOf(5.0, maximumRange)
    private var lastNs = 0L
    var state = State.UNKNOWN
        private set
    val allowsGesture: Boolean get() = available && state == State.FAR

    fun reset() { lastNs = 0; state = State.UNKNOWN }

    /** Returns whether context changed, so callers can consume gesture history. */
    fun sample(timestampNs: Long, distance: Double): Boolean {
        val ordered = timestampNs > 0 && timestampNs > lastNs
        if (ordered) lastNs = timestampNs
        val next = if (!available || !ordered || !distance.isFinite() || distance < 0 || distance > range)
            State.UNKNOWN
        else if (distance < nearThreshold) State.NEAR else State.FAR
        val changed = next != state
        state = next
        return changed
    }
}
