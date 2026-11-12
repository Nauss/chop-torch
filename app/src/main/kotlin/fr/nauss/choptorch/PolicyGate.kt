// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

/** Sample-driven policy refresh: no wakeups. Call force=true immediately before any command.
 * Callbacks are captured once, not allocated per sample. Consent is never cached.
 */
internal class PolicyGate(private val consent: () -> Boolean, private val reason: () -> Int) {
    private var checked = false
    private var checkedNs = 0L
    var blockedReason = 0
        private set

    fun allows(nowNs: Long, force: Boolean = false): Boolean {
        if (nowNs < 0) return false
        if (force || !checked || nowNs < checkedNs || nowNs - checkedNs >= 1_000_000_000L) {
            blockedReason = reason()
            checkedNs = nowNs; checked = true
        }
        // Read after the potentially blocking policy call, including at the command boundary.
        val enabled = consent()
        return enabled && blockedReason == 0
    }
}
