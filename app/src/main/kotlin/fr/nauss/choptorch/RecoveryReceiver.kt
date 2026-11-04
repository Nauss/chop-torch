// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Only system-protected boot/update broadcasts. Credential storage: after first unlock. */
class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Activation.start(context)
        }
    }
}
