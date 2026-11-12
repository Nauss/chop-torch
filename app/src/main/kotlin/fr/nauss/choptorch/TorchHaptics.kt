// SPDX-License-Identifier: GPL-3.0-or-later
package fr.nauss.choptorch

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings

/** One device-tuned tick, never a repeating vibration or an audible notification. */
internal class TorchHaptics(private val context: Context) {
    private val vibrator by lazy {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    }
    private val effect=VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    private val attributes=VibrationAttributes.Builder()
        .setUsage(VibrationAttributes.USAGE_GESTURE_INPUT).build()

    /** True means submitted, not proof that hardware played it (system policy may suppress it). */
    fun confirm(): Boolean {
        return try {
            val motor=vibrator ?: return false
            if(!motor.hasVibrator() || Settings.System.getInt(context.contentResolver,
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,1)==0) return false
            motor.vibrate(effect,attributes)
            true
        } catch(_: RuntimeException) {
            // Haptics are optional: a missing/denied system service must not disable the torch.
            false
        }
    }
}
