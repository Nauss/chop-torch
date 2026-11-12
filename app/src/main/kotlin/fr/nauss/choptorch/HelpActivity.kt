// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button

import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar

/** Separate native destination; the framework owns system/predictive Back. */
class HelpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        val root = findViewById<LinearLayout>(R.id.safe_area)
        val content = findViewById<LinearLayout>(R.id.help_content)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            content.layoutParams.width = minOf(resources.displayMetrics.widthPixels - bars.left - bars.right,
                (600 * resources.displayMetrics.density).toInt())
            insets
        }
        findViewById<Toolbar>(R.id.help_toolbar).setNavigationOnClickListener { finish() }
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        findViewById<TextView>(R.id.version).text = getString(R.string.version_license, version)
        findViewById<TextView>(R.id.font_license).text = assets.open("licenses/Barlow-OFL.txt").bufferedReader().use { it.readText() }
        findViewById<Button>(R.id.source).setOnClickListener {
            open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nauss/chop-torch")))
        }
        findViewById<Button>(R.id.battery_settings).setOnClickListener {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        // Explicit restoration also covers the long license text after a configuration change.
        if (savedInstanceState != null) {
            findViewById<ScrollView>(R.id.help_scroll).post {
                findViewById<ScrollView>(R.id.help_scroll).scrollTo(0, savedInstanceState.getInt("helpScroll"))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("helpScroll", findViewById<ScrollView>(R.id.help_scroll).scrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        findViewById<TextView>(R.id.battery_info).setText(if (exempt) R.string.battery_exempt else R.string.battery_default)
    }

    private fun open(intent: Intent) {
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show() }
    }
}
