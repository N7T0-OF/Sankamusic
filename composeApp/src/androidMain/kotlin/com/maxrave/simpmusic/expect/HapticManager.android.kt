package com.maxrave.simpmusic.expect

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.koin.mp.KoinPlatform.getKoin

actual object HapticManager {
    actual var enabled: Boolean = true
    actual var intensity: Float = 1f

    actual fun isSupported(): Boolean {
        val context: Context = getKoin().get()
        val vibrator = vibrator(context) ?: return false
        return vibrator.hasVibrator()
    }

    actual fun vibrate(type: HapticType) {
        if (!enabled || intensity <= 0f) return
        val context: Context = getKoin().get()
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        // Duration (ms) + base amplitude (0..255) per type. Amplitude is
        // scaled by the user intensity and clamped so it never hits 0.
        val (duration, amplitude) =
            when (type) {
                HapticType.SELECT -> 15L to 40
                HapticType.SLIDER_STEP -> 12L to 30
                HapticType.SUCCESS -> 30L to 60
            }
        val scaledAmplitude = (amplitude * intensity).toInt().coerceIn(1, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, scaledAmplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
