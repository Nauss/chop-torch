// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

/** Ignore the phone's own short haptic transient, using the sensor's monotonic clock. */
internal class FeedbackWindow {
    private var untilNs=0L
    fun start(nowNs: Long) { untilNs=if(nowNs>Long.MAX_VALUE-250_000_000L) Long.MAX_VALUE else nowNs+250_000_000L }
    fun suppresses(sampleNs: Long): Boolean = untilNs!=0L && sampleNs<=untilNs

    fun sample(detector: PlaneLinearChopDetector, timestampNs: Long, x: Double, y: Double, z: Double): Boolean {
        if(!suppresses(timestampNs)) return detector.sample(timestampNs,x,y,z)
        // Retain cadence, but never retain or accept a haptic candidate.
        detector.cancel()
        detector.sample(timestampNs,x,y,z)
        detector.cancel()
        return false
    }

    fun sample(detector: DirectionalChopDetector, timestampNs: Long, x: Double, y: Double, z: Double): Boolean {
        if(!suppresses(timestampNs)) return detector.sample(timestampNs,x,y,z)
        // Preserve sampling/evaluation cadence, but never retain a haptic candidate.
        detector.cancel()
        detector.sample(timestampNs,x,y,z)
        detector.cancel()
        return false
    }

    fun sample(detector: ChopDetector, timestampNs: Long, x: Double, y: Double, z: Double): Boolean {
        if(!suppresses(timestampNs)) return detector.sample(timestampNs,x,y,z)
        // Keep cadence/gravity tracking alive; dropping these samples resets the filter.
        // Clear both sides so the vibration cannot seed a later half-gesture.
        detector.cancel()
        detector.sample(timestampNs,x,y,z)
        detector.cancel()
        return false
    }
}
