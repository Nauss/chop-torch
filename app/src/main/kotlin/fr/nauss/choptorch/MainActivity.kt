// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.*

/** One native screen. System controls retain keyboard and TalkBack behavior. */
class MainActivity : Activity() {
    private lateinit var activation: Switch
    private lateinit var stateText: TextView
    private lateinit var detail: TextView
    private lateinit var recovery: Button
    private lateinit var help: View
    private lateinit var helpButton: Button
    private var rendering = false
    private var notificationDenied = false
    private var starting = false
    private val observer: () -> Unit = {
        if (LiveState.state.running || LiveState.state.error != 0) starting = false
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val safeArea = findViewById<FrameLayout>(R.id.safe_area)
        val content = findViewById<LinearLayout>(R.id.content)
        // Keep the viewport itself outside the bars: padding a scrolling view
        // does not provide a stable, independently measurable safe-area boundary.
        safeArea.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            content.layoutParams.width = minOf(resources.displayMetrics.widthPixels - bars.left - bars.right, dp(600))
            insets
        }
        content.layoutParams.width = minOf(resources.displayMetrics.widthPixels, dp(600))
        activation = findViewById(R.id.activation)
        stateText = findViewById(R.id.state)
        detail = findViewById(R.id.detail)
        recovery = findViewById(R.id.recovery)
        help = findViewById(R.id.help)
        helpButton = findViewById(R.id.help_button)
        notificationDenied = savedInstanceState?.getBoolean("notificationDenied") ?: false
        setHelpExpanded(savedInstanceState?.getBoolean("helpExpanded") ?: false)
        helpButton.setOnClickListener { setHelpExpanded(help.visibility != View.VISIBLE) }
        activation.setOnCheckedChangeListener { _, checked ->
            if (!rendering) {
                if (checked) {
                    render()
                    AlertDialog.Builder(this).setTitle(R.string.activation_title)
                        .setMessage(R.string.activation_message)
                        .setPositiveButton(R.string.enable) { _, _ -> requestActivation() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                } else {
                    getSharedPreferences("settings", 0).edit().putBoolean("enabled", false).apply()
                    stopService(Intent(this, ChopService::class.java))
                }
            }
        }
        recovery.setOnClickListener {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        val prefs = getSharedPreferences("settings", 0)
        val slider = findViewById<SeekBar>(R.id.sensitivity)
        val value = findViewById<TextView>(R.id.sensitivity_value)
        slider.progress = prefs.getInt("sensitivity", 50).coerceIn(0, 100)
        fun updateValue(progress: Int) {
            value.text = getString(R.string.sensitivity_value, progress)
            slider.stateDescription = getString(R.string.sensitivity_value, progress)
        }
        updateValue(slider.progress)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                updateValue(progress)
                if (fromUser) {
                    prefs.edit().putInt("sensitivity", progress).apply()
                    if (LiveState.state.running) {
                        try { startService(Intent(this@MainActivity, ChopService::class.java).setAction(ChopService.SENSITIVITY)) }
                        catch (_: RuntimeException) { LiveState.update { it.copy(error = R.string.error_command) } }
                    }
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        findViewById<TextView>(R.id.version).text = getString(R.string.version_license, version)
        findViewById<Button>(R.id.source).setOnClickListener {
            open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nauss/chop-torch")))
        }
        findViewById<Button>(R.id.battery_settings).setOnClickListener {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun setHelpExpanded(expanded: Boolean) {
        help.visibility = if (expanded) View.VISIBLE else View.GONE
        helpButton.setText(if (expanded) R.string.help_close else R.string.help_open)
        helpButton.stateDescription = getString(if (expanded) R.string.expanded else R.string.collapsed)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("helpExpanded", help.visibility == View.VISIBLE)
        outState.putBoolean("notificationDenied", notificationDenied)
        super.onSaveInstanceState(outState)
    }
    private fun requestActivation() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION)
        } else startDetection()
    }
    private fun startDetection() {
        if (!notificationsAllowed()) { notificationDenied = true; render(); return }
        notificationDenied = false
        // A repeated failure must still produce a fresh observed transition.
        LiveState.update { it.copy(error = 0) }
        starting = true
        render()
        try { startForegroundService(Intent(this, ChopService::class.java)) }
        catch (_: RuntimeException) {
            starting = false
            LiveState.update { it.copy(running = false, error = R.string.error_start) }
            render()
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != NOTIFICATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startDetection()
        else { notificationDenied = true; render() }
    }
    private fun notificationsAllowed() = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    override fun onResume() {
        super.onResume()
        if (notificationsAllowed()) notificationDenied = false
        else if (LiveState.state.running) {
            notificationDenied = true
            stopService(Intent(this, ChopService::class.java))
        }
        LiveState.observe(observer)
        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        findViewById<TextView>(R.id.battery_info).setText(if (exempt) R.string.battery_exempt else R.string.battery_default)
    }
    override fun onPause() { LiveState.remove(observer); super.onPause() }
    private fun render() {
        val state = LiveState.state
        rendering = true
        activation.isChecked = state.running
        activation.isEnabled = !starting
        rendering = false
        stateText.setText(when {
            starting -> R.string.state_starting
            state.running -> R.string.state_active
            else -> R.string.state_off
        })
        detail.setText(when {
            notificationDenied -> R.string.error_notifications
            state.error != 0 -> state.error
            state.running -> state.torch
            else -> R.string.state_off_hint
        })
        recovery.visibility = if (notificationDenied) View.VISIBLE else View.GONE
    }
    private fun open(intent: Intent) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show() }
    }
    companion object { private const val NOTIFICATION = 1 }
}
