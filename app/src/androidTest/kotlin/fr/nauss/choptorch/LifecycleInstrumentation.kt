// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle

/** Runs against real framework services, only on a disposable emulator. No test code in release. */
class LifecycleInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val results = Bundle()
        try {
            check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Disposable emulator required" }
            val prefs = targetContext.getSharedPreferences("settings", 0)
            runOnMainSync { targetContext.stopService(Intent(targetContext, ChopService::class.java)) }
            Thread.sleep(400)
            prefs.edit().clear().commit()
            runOnMainSync { targetContext.startForegroundService(Intent(targetContext, ChopService::class.java)) }
            Thread.sleep(1000)
            check(!LiveState.state.running) { "Fresh/disabled app started detection" }
            check(!prefs.getBoolean("enabled", false)) { "Service invented user intent" }
            Activation.setEnabled(targetContext, true)
            val pendingActivity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            var cancelAvailable = false
            runOnMainSync { cancelAvailable = pendingActivity.findViewById<android.widget.Switch>(R.id.activation).isChecked }
            check(cancelAvailable) { "Saved pending activation cannot be cancelled from UI" }
            runOnMainSync { pendingActivity.findViewById<android.widget.Switch>(R.id.activation).performClick(); pendingActivity.finish() }
            check(!Activation.enabled(targetContext))
            Activation.setEnabled(targetContext, true)
            runOnMainSync { targetContext.startForegroundService(Intent(targetContext, ChopService::class.java).setAction(ChopService.SENSITIVITY)) }
            Thread.sleep(500)
            check(targetContext.getSystemService(android.app.NotificationManager::class.java).activeNotifications.isEmpty()) {
                "Settings-only command created an idle foreground service"
            }
            runOnMainSync { check(Activation.start(targetContext, userInitiated = true)) }
            Thread.sleep(800)
            check(LiveState.state.running && Activation.enabled(targetContext)) { "Enabled valid app did not start" }
            repeat(3) { runOnMainSync { Activation.start(targetContext) } }
            runOnMainSync { targetContext.startService(Intent(targetContext, ChopService::class.java).setAction(ChopService.SENSITIVITY)) }
            runOnMainSync { targetContext.startService(Intent(targetContext, ChopService::class.java).setAction("obsolete-command")) }
            Thread.sleep(300)
            check(LiveState.state.running)
            val notification = targetContext.getSystemService(android.app.NotificationManager::class.java)
                .activeNotifications.single().notification
            check(notification.channelId == "gesture")
            notification.actions.single().actionIntent.send()
            Thread.sleep(700)
            check(!LiveState.state.running && !Activation.enabled(targetContext)) { "Notification stop not respected" }
            runOnMainSync { RecoveryReceiver().onReceive(targetContext, Intent(Intent.ACTION_BOOT_COMPLETED)) }
            Thread.sleep(300)
            check(!LiveState.state.running) { "Boot revived disabled app" }
            // Real protected delivery exercised separately; this invokes the same production receiver.
            Activation.setEnabled(targetContext, true)
            runOnMainSync { RecoveryReceiver().onReceive(targetContext, Intent(Intent.ACTION_MY_PACKAGE_REPLACED)) }
            Thread.sleep(800)
            check(LiveState.state.running) { "Update receiver did not recover consent" }
            runOnMainSync { Activation.stop(targetContext) }
            Thread.sleep(400)
            repeat(10) {
                runOnMainSync { Activation.start(targetContext, userInitiated = true) }
                runOnMainSync { Activation.stop(targetContext) }
            }
            Thread.sleep(6000)
            check(!LiveState.state.running && !Activation.enabled(targetContext)) { "Queued work revived stopped detection" }
            val manager = targetContext.getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(android.app.NotificationChannel("gesture", "Blocked test channel", android.app.NotificationManager.IMPORTANCE_NONE))
            var accepted = true
            runOnMainSync { accepted = Activation.start(targetContext, userInitiated = true) }
            check(!accepted) { "Blocked notification channel accepted" }
            check(!Activation.enabled(targetContext))
            results.putString("stream", "PASS fresh/disabled, enabled, duplicate start, notification stop, disabled boot, enabled update, blocked notification channel actual services\n")
            finish(-1, results)
        } catch (error: Throwable) {
            results.putString("stream", "FAIL ${error.javaClass.simpleName}: ${error.message}\n")
            finish(0, results)
        }
    }
}
