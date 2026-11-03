package fr.nauss.choptorch

/** Prevent stale non-wake-up FIFO delivery from toggling a lamp after pickup.
 * The device test must establish whether this provisional 250 ms budget is suitable.
 */
object SampleFreshness {
    fun accepts(sensorNs: Long, nowNs: Long): Boolean =
        sensorNs > 0 && nowNs >= sensorNs && nowNs - sensorNs <= 250_000_000L
}
