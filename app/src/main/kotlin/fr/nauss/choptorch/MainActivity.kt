// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.Manifest
import android.app.Activity
import android.app.AlertDialog

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.*

/** Native poster and quiet controls. Activation behavior is independent of navigation. */
class MainActivity : Activity() {
    private lateinit var activation: Switch
    private lateinit var stateText: TextView
    private lateinit var detail: TextView
    private lateinit var recovery: Button
    private lateinit var torchState: TextView
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
        // Saved View state must never replay consent callbacks on recreation.
        activation.isSaveEnabled = false
        stateText = findViewById(R.id.state)
        detail = findViewById(R.id.detail)
        recovery = findViewById(R.id.recovery)
        torchState = findViewById(R.id.torch_state)
        notificationDenied = savedInstanceState?.getBoolean("notificationDenied") ?: false
        findViewById<ImageButton>(R.id.help_button).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        // At large font sizes the illustration takes its own row, never the text's space.
        if (resources.configuration.fontScale >= 1.5f || resources.configuration.screenWidthDp < 360) {
            findViewById<LinearLayout>(R.id.hero).orientation = LinearLayout.VERTICAL
            findViewById<TextView>(R.id.hero_title).layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        activation.setOnCheckedChangeListener { _, checked ->
            if (!rendering) {
                if (checked) {
                    render()
                    AlertDialog.Builder(this).setTitle(R.string.activation_title)
                        .setMessage(R.string.activation_message)
                        .setPositiveButton(R.string.enable) { _, _ -> requestActivation() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                } else {
                    starting = false
                    Activation.stop(this)
                    render()
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

    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {

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
        if (!Activation.start(this, userInitiated = true)) {
            starting = false
            render()
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != NOTIFICATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startDetection()
        else { notificationDenied = true; render() }
    }
    private fun notificationsAllowed() = Activation.notificationsAllowed(this)
    override fun onResume() {
        super.onResume()
        Activation.reconcileUserStop(this)
        if (notificationsAllowed()) notificationDenied = false
        else if (LiveState.state.running || Activation.enabled(this)) {
            notificationDenied = true
            Activation.stop(this)
        }
        LiveState.observe(observer)

    }
    override fun onPause() { LiveState.remove(observer); super.onPause() }
    private fun render() {
        val state = LiveState.state
        torchState.setText(state.torch)
        rendering = true
        // The switch controls saved consent; the label below remains observed truth.
        // Keep cancellation available even while Android recovery/start is pending.
        activation.isChecked = starting || Activation.enabled(this) || state.running
        activation.isEnabled = true
        rendering = false
        stateText.setText(when {
            starting -> R.string.state_starting
            state.running -> R.string.state_active
            else -> R.string.state_off
        })
        val detailMessage = when {
            notificationDenied -> R.string.error_notifications
            Activation.blockedReason(this) == R.string.error_restricted -> R.string.error_restricted
            state.error != 0 -> state.error
            state.running && state.context != 0 -> state.context
            state.running -> 0
            Activation.enabled(this) -> R.string.state_pending_hint
            else -> R.string.state_off_hint
        }
        // The separate live row owns torch state, including torch-specific errors.
        val showDetail = detailMessage != 0 && detailMessage != state.torch
        detail.visibility = if (showDetail) View.VISIBLE else View.GONE
        if (showDetail) detail.setText(detailMessage) else detail.text = ""
        recovery.visibility = if (notificationDenied) View.VISIBLE else View.GONE
    }
    private fun open(intent: Intent) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show() }
    }
    companion object { private const val NOTIFICATION = 1 }
}
