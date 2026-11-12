// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.app.Instrumentation
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toolbar
import java.io.File

/** Framework UI regression, deliberately runnable against the pre-redesign layout. */
object UiRegression {
    private lateinit var test: Instrumentation
    private var tag = ""

    private fun ui(block: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        test.waitForIdleSync()
    }

    private fun pass(message: String) {
        test.sendStatus(0, Bundle().apply { putString("stream", "PASS $tag $message\n") })
    }

    private fun settle() { test.waitForIdleSync(); Thread.sleep(450) }

    private fun shot(name: String) {
        settle()
        val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
        File(test.targetContext.getExternalFilesDir(null), "$tag-$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun bounds(activity: Activity, scrollId: Int) {
        ui {
            val root = activity.findViewById<View>(R.id.safe_area)
            val scroll = activity.findViewById<ScrollView>(scrollId)
            val bars = root.rootWindowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val viewport = Rect()
            check(scroll.getGlobalVisibleRect(viewport))
            val decor = activity.window.decorView
            check(viewport.top >= bars.top && viewport.bottom <= decor.height - bars.bottom) { "Scroll viewport under system bars: $viewport / $bars" }
            check(viewport.left >= bars.left && viewport.right <= decor.width - bars.right)
            check(root.paddingTop == bars.top && root.paddingBottom == bars.bottom) { "Insets are not on the fixed root" }
            for (text in descendants(scroll).filterIsInstance<TextView>()) {
                if (text.visibility != View.VISIBLE || text.text.isEmpty()) continue
                val layout = checkNotNull(text.layout)
                check(layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom) { "Clipped text height: ${text.text}" }
                for (line in 0 until layout.lineCount) {
                    check(layout.getEllipsisCount(line) == 0) { "Ellipsized text: ${text.text}" }
                    // getLineMax excludes trailing spaces; getLineWidth includes them past a wrap.
                    check(layout.getLineMax(line) <= layout.width + 2) { "Clipped text width: ${text.text}" }
                }
            }
        }
    }

    private fun activationAppearance(activity: Activity, checked: Boolean) {
        ui {
            val control = activity.findViewById<Switch>(R.id.activation)
            val density = activity.resources.displayMetrics.density
            fun close(pixels: Int, dp: Int) = kotlin.math.abs(pixels / density - dp) < 0.6f
            check(control.isChecked == checked)
            check(control.height >= 48 * density)
            val node = control.createAccessibilityNodeInfo()
            check(node.isCheckable && node.isChecked == checked)
            check(node.text.toString().contains(activity.getString(R.string.detection)))
            val bitmap = android.graphics.Bitmap.createBitmap(control.width, control.height,
                android.graphics.Bitmap.Config.ARGB_8888)
            try {
                control.draw(android.graphics.Canvas(bitmap))
                val track = control.trackDrawable.bounds
                val thumb = control.thumbDrawable.bounds
                check(close(track.width(), 56) && close(track.height(), 28)) {
                    "Activation must use the approved 56 x 28dp rectangular track, not the stock pill: $track"
                }
                check(close(thumb.width(), 20)) { "Activation thumb must remain 20dp wide" }
                val padding = Rect()
                control.thumbDrawable.getPadding(padding)
                check(close(thumb.height() - padding.top - padding.bottom, 18)) {
                    "Activation thumb must remain 18dp tall inside the track"
                }
                val trackColor = activity.getColor(if (checked) R.color.primary else R.color.control_normal)
                val thumbColor = activity.getColor(if (checked) R.color.surface_container else R.color.primary)
                check(bitmap.getPixel(thumb.centerX(), thumb.centerY()) == thumbColor) { "Wrong activation thumb color" }
                check(bitmap.getPixel(track.left + (3 * density).toInt(), track.top + (7 * density).toInt()) == trackColor) {
                    "Track corner must be rectangular, not rounded into a pill"
                }
                check(bitmap.getPixel(thumb.centerX(), track.top + (2 * density).toInt()) == trackColor) {
                    "Thumb must sit inside the track with a visible vertical inset"
                }
                File(test.targetContext.getExternalFilesDir(null), "$tag-activation-$checked.png").outputStream().use {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
        }
        pass("rectangular activation checked=$checked: rendered geometry, palette, inset and native accessibility")
    }

    /** Exercise the real observer/render binding, not a fake production callback hook. */
    private fun posterAndLiveStatus(activity: Activity) {
        val original = LiveState.state
        try {
            for ((label, state) in listOf("off" to R.string.torch_off, "on" to R.string.torch_on,
                    "unknown" to R.string.torch_unknown, "unavailable" to R.string.torch_unavailable,
                    "denied" to R.string.torch_denied, "missing" to R.string.torch_missing,
                    "unconfirmed" to R.string.torch_unconfirmed)) {
                ui { LiveState.update { it.copy(torch = state, running = true, context = 0, error = 0) } }
                settle()
                ui {
                    val poster = activity.findViewById<View>(R.id.poster)
                    val tagline = activity.findViewById<TextView>(R.id.hero_tagline)
                    val live = activity.findViewById<TextView>(R.id.torch_state)
                    val detail = activity.findViewById<View>(R.id.detail)
                    check(tagline in descendants(poster) && live !in descendants(poster))
                    check(tagline.text == activity.getString(R.string.hero_tagline))
                    val expected = if (activity.resources.configuration.locales[0].language == "fr")
                        "Torche allumée" else "Flashlight on"
                    check(tagline.text.toString() == expected)
                    check(tagline.accessibilityLiveRegion == View.ACCESSIBILITY_LIVE_REGION_NONE)
                    check(live.text == activity.getString(state)) { "Live status did not follow $label" }
                    check(live.accessibilityLiveRegion == View.ACCESSIBILITY_LIVE_REGION_POLITE)
                    check(detail.visibility == View.GONE) { "Torch status must not be repeated in the detail row" }
                    val parent = live.parent as ViewGroup
                    check(parent == detail.parent && parent.indexOfChild(live) == parent.indexOfChild(detail) + 1)
                    if (detail.visibility == View.VISIBLE) check(live.top >= detail.bottom)
                    check(live.currentTextColor == activity.getColor(R.color.on_surface_variant))
                    activity.findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0)
                }
                bounds(activity, R.id.scroll)
                if (label in listOf("off", "on", "unknown", "unavailable")) {
                    shot("poster-$label")
                    ui { activity.findViewById<ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN) }
                    shot("live-$label")
                }
                pass("static positive tagline and callback-driven live status: $label")
            }
            ui { LiveState.update { it.copy(running = true, context = R.string.context_covered, error = 0, torch = R.string.torch_off) } }
            settle()
            ui {
                val detail = activity.findViewById<TextView>(R.id.detail)
                check(detail.visibility == View.VISIBLE && detail.text == activity.getString(R.string.context_covered))
            }
            ui { LiveState.update { it.copy(context = 0, error = R.string.torch_unavailable, torch = R.string.torch_unavailable) } }
            settle()
            ui {
                check(activity.findViewById<View>(R.id.detail).visibility == View.GONE)
                check(activity.findViewById<TextView>(R.id.torch_state).text == activity.getString(R.string.torch_unavailable))
            }
        } finally {
            ui {
                LiveState.update { original }
                activity.findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0)
            }
        }
    }

    private fun recreate(activity: Activity): Activity {
        val monitor = test.addMonitor(activity.javaClass.name, null, false)
        ui { activity.recreate() }
        val next = checkNotNull(test.waitForMonitorWithTimeout(monitor, 5000)) { "Recreation timed out" }
        test.removeMonitor(monitor)
        settle()
        return next
    }

    private fun help(main: Activity): Activity {
        ui { main.findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0) }
        settle()
        val monitor = test.addMonitor("fr.nauss.choptorch.HelpActivity", null, false)
        ui { main.findViewById<View>(R.id.help_button).performClick() }
        val next = checkNotNull(test.waitForMonitorWithTimeout(monitor, 3000)) { "Help icon did not open a separate Activity" }
        test.removeMonitor(monitor)
        settle()
        return next
    }

    fun run(test: Instrumentation) {
        this.test = test
        var main = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        settle()
        val config = main.resources.configuration
        tag = "${config.locales[0].language}-${config.fontScale}-${config.uiMode and 48}"
        val prefs = test.targetContext.getSharedPreferences("settings", 0)
        ui {
            val help = main.findViewById<View>(R.id.help_button)
            check(help is ImageButton) { "Help must be a discreet header icon, not the inline accordion" }
            check(help.contentDescription == main.getString(R.string.help_open)) { "Localized help label missing" }
            val dp = main.resources.displayMetrics.density
            check(help.width >= 48 * dp && help.height >= 48 * dp)
            check(help.drawable.intrinsicWidth / dp in 22f..24f)
            check(main.findViewById<View?>(R.id.battery_info) == null) { "Help prose still exists on Main" }
            check(main.findViewById<SeekBar>(R.id.sensitivity).contentDescription == main.getString(R.string.sensitivity))
            if (!prefs.contains("sensitivity")) {
                check(main.findViewById<SeekBar>(R.id.sensitivity).progress == 50) { "Fresh default is not 50" }
            }
        }
        bounds(main, R.id.scroll)
        activationAppearance(main, false)
        shot("main")
        posterAndLiveStatus(main)
        pass("localized icon/slider labels; 48dp target, 24dp art; fixed root insets; main has no help prose")
        for (value in listOf(0, 100, 73)) {
            ui {
                val slider = main.findViewById<SeekBar>(R.id.sensitivity)
                slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                    Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value.toFloat()) })
                check(slider.progress == value)
                check(prefs.getInt("sensitivity", -1) == value)
            }
        }
        main = recreate(main)
        ui {
            check(main.findViewById<SeekBar>(R.id.sensitivity).progress == 73)
            check(!main.findViewById<Switch>(R.id.activation).isChecked)
            val title = main.findViewById<View>(R.id.hero_title)
            val art = main.findViewById<View>(R.id.phone_art)
            val a = Rect(title.left, title.top, title.right, title.bottom)
            check(!Rect.intersects(a, Rect(art.left, art.top, art.right, art.bottom))) { "Artwork overlaps title" }
            main.findViewById<ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN)
        }
        settle()
        bounds(main, R.id.scroll)
        shot("controls")
        pass("integer sensitivity 0/100/73 via real accessibility action, persisted after recreation; no title/art overlap")
        posterAndLiveStatus(main)
        var help = help(main)
        bounds(help, R.id.help_scroll)
        shot("help-top")
        ui { check(!help.packageManager.getActivityInfo(help.componentName, 0).exported) }
        var bottom = 0
        var step = 0
        ui { val scroll = help.findViewById<ScrollView>(R.id.help_scroll); bottom = scroll.getChildAt(0).height - scroll.height; step = scroll.height / 2 }
        check(bottom > 0 && step > 0)
        for (position in 0..bottom step step) {
            ui { help.findViewById<ScrollView>(R.id.help_scroll).scrollTo(0, position) }
            bounds(help, R.id.help_scroll)
        }
        ui { help.findViewById<ScrollView>(R.id.help_scroll).fullScroll(View.FOCUS_DOWN) }
        settle()
        var savedScroll = 0
        ui { savedScroll = help.findViewById<ScrollView>(R.id.help_scroll).scrollY }
        shot("help-bottom")
        help = recreate(help)
        ui { check(help.findViewById<ScrollView>(R.id.help_scroll).scrollY == savedScroll) { "Help scroll lost on recreation" } }
        bounds(help, R.id.help_scroll)
        pass("non-exported Help; all text unclipped at current font scale; safe bounds throughout long scroll; bottom restored after recreation")
        // Exercise the actual Toolbar navigation button, not Activity.finish directly.
        ui {
            val toolbar = help.findViewById<Toolbar>(R.id.help_toolbar)
            val back = descendants(toolbar).single { it is ImageButton && it.contentDescription == help.getString(R.string.back) }
            check(back.width >= 48 * help.resources.displayMetrics.density)
            back.performClick()
        }
        settle()
        check(help.isFinishing)
        check(!Activation.enabled(test.targetContext) && prefs.getInt("sensitivity", -1) == 73)
        help = help(main)
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
        check(help.isFinishing)
        pass("actual toolbar Back and system Back return to Main without changing activation or sensitivity")
        help = help(main)
        val source = test.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
            addDataAuthority("github.com", null)
            addDataPath("/Nauss/chop-torch", android.os.PatternMatcher.PATTERN_LITERAL)
        }, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        ui { help.findViewById<View>(R.id.source).performClick() }
        check(source.hits == 1) { "Source link did not dispatch HTTPS intent" }
        test.removeMonitor(source)
        // Real system Settings round trip; battery status must be refreshed on resume.
        ui { help.findViewById<View>(R.id.battery_settings).performClick() }
        settle()
        test.uiAutomation.executeShellCommand("dumpsys deviceidle whitelist +${test.targetContext.packageName}").close()
        Thread.sleep(400)
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
        try {
            ui { check(help.findViewById<TextView>(R.id.battery_info).text == help.getString(R.string.battery_exempt)) { "Battery status not refreshed on Help resume" } }
        } finally {
            test.uiAutomation.executeShellCommand("dumpsys deviceidle whitelist -${test.targetContext.packageName}").close()
        }
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
        pass("functional source intent and real app Settings link; exemption refreshed on resume")
        // A real saved-but-not-running state: no fake service or production test hooks.
        ui { Activation.setEnabled(test.targetContext, true) }
        main = recreate(main)
        ui {
            check(!LiveState.state.running)
            check(main.findViewById<Switch>(R.id.activation).isChecked)
            check(main.findViewById<TextView>(R.id.detail).text == main.getString(R.string.state_pending_hint))
        }
        activationAppearance(main, true)
        help = help(main)
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        settle()
        check(Activation.enabled(test.targetContext) && prefs.getInt("sensitivity", -1) == 73)
        ui { main.findViewById<ScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN) }
        shot("pending")
        ui { main.findViewById<Switch>(R.id.activation).performClick() }
        check(!Activation.enabled(test.targetContext))
        pass("pending consent remains cancellable and unchanged by help navigation")
        ui { main.finish() }
    }
}
