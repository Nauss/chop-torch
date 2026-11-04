// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.NotificationManager
import android.content.Context
import android.content.Intent

/** Saved consent is not observed service state. No timers, jobs or retry loops. */
object Activation {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", 0)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean): Boolean = prefs(context).edit()
        .putBoolean("enabled", enabled).putLong("intentChangedAt", System.currentTimeMillis()).commit()

    /** Task-manager Stop has no callback. Consume its exit record, never resurrect saved consent. */
    fun reconcileUserStop(context: Context) {
        if (!enabled(context)) return
        val changed = prefs(context).getLong("intentChangedAt", 0)
        val exits = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(null, 0, 8)
        if (exits.any { it.reason == ApplicationExitInfo.REASON_USER_REQUESTED && it.timestamp >= changed }) {
            setEnabled(context, false)
        }
    }
    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel("gesture")?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun blockedReason(context: Context): Int = when {
        !notificationsAllowed(context) -> R.string.error_notifications
        context.getSystemService(ActivityManager::class.java).isBackgroundRestricted -> R.string.error_restricted
        else -> 0
    }
    fun eligible(context: Context): Boolean {
        reconcileUserStop(context)
        return RecoveryPolicy.mayRecover(enabled(context),
            notificationsAllowed(context),
            context.getSystemService(ActivityManager::class.java).isBackgroundRestricted, false)
    }
    fun start(context: Context, userInitiated: Boolean = false): Boolean {
        reconcileUserStop(context)
        val reason = blockedReason(context)
        if (reason != 0) {
            setEnabled(context, false)
            LiveState.update { it.copy(running = false, error = reason) }
            return false
        }
        if (userInitiated && !setEnabled(context, true)) {
            LiveState.update { it.copy(running = false, error = R.string.error_start) }
            return false
        }
        if (!eligible(context)) return false
        return try {
            context.startForegroundService(Intent(context, ChopService::class.java))
            true
        } catch (_: RuntimeException) {
            // A denied start is terminal until fresh explicit consent, not a retry trigger.
            setEnabled(context, false)
            LiveState.update { it.copy(running = false, error = R.string.error_start) }
            false
        }
    }
    fun stop(context: Context) {
        setEnabled(context, false)
        context.stopService(Intent(context, ChopService::class.java))
    }
}
