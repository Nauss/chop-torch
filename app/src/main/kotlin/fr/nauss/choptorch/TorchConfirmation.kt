// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

/** Consume a requested state change once; unsolicited camera callbacks stay silent. */
internal class TorchConfirmation {
    private var target: Boolean? = null
    fun requested(enabled: Boolean) { target=enabled }
    fun confirmed(previous: Boolean?, enabled: Boolean): Boolean {
        val confirmed=target==enabled && previous!=null && previous!=enabled
        if(confirmed || previous==null) target=null
        return confirmed
    }
    fun cancel() { target=null }
}
