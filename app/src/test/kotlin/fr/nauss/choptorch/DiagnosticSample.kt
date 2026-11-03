// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

/** Only retained during the bounded manual capture; both clocks use elapsed realtime. */
internal data class DiagnosticSample(
    val timestampNs: Long, val receivedNs: Long,
    val x: Float, val y: Float, val z: Float,
    val freshnessAccepted: Boolean, val sensitivity: Int
) {
    fun columns(startNs: Long): List<Any> = listOf(
        (timestampNs - startNs) / 1e6, x.toDouble(), y.toDouble(), z.toDouble(),
        (receivedNs - startNs) / 1e6, freshnessAccepted, sensitivity
    )
}
