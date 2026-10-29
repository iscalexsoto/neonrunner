package com.iscalexsoto.neonrunner

import android.content.Context

/** Lo único que se guarda: el récord, en SharedPreferences locales. */
class HighScoreStore(context: Context) {
    private val prefs = context.getSharedPreferences("neon_runner", Context.MODE_PRIVATE)

    fun load(): Int = prefs.getInt(KEY, 0)

    fun save(score: Int) {
        prefs.edit().putInt(KEY, score).apply()
    }

    private companion object {
        const val KEY = "high_score"
    }
}
