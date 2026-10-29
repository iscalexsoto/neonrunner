package com.iscalexsoto.neonrunner

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibración corta al chocar. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun crash() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(
            VibrationEffect.createOneShot(
                GameConfig.Audio.CRASH_VIBRATION_MS,
                VibrationEffect.DEFAULT_AMPLITUDE,
            ),
        )
    }
}
