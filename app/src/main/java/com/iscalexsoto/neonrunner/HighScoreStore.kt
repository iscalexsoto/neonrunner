package com.iscalexsoto.neonrunner

import android.content.Context

/** Lo único que se guarda: el récord de cada modo, en SharedPreferences locales. */
class HighScoreStore(context: Context) {
    private val prefs = context.getSharedPreferences("neon_runner", Context.MODE_PRIVATE)

    fun load(mode: GameMode): Int = prefs.getInt(key(mode), 0)

    fun loadAll(): Map<GameMode, Int> = GameMode.entries.associateWith { load(it) }

    fun save(mode: GameMode, score: Int) {
        prefs.edit().putInt(key(mode), score).apply()
    }

    /** El modo Runner conserva la clave original para no perder el récord ya guardado. */
    private fun key(mode: GameMode) = when (mode) {
        GameMode.RUNNER -> "high_score"
        GameMode.ROGUE -> "high_score_rogue"
    }
}
