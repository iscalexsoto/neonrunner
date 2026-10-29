package com.iscalexsoto.neonrunner

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pruebas del motor puro (corren en la JVM, sin emulador).
 * Un bot sencillo juega muchas partidas: si muere pronto es que la generación de obstáculos
 * produjo algo imposible de saltar, lo que no debería pasar nunca.
 */
class GameEngineTest {

    private class Counter : GameEvents {
        var jumps = 0
        var crashes = 0
        var dings = 0
        var savedHighScore = -1
        override fun onJump() { jumps++ }
        override fun onCrash() { crashes++ }
        override fun onDing() { dings++ }
        override fun onHighScore(score: Int) { savedHighScore = score }
    }

    /** Deja que el bot juegue hasta morir o hasta [maxSeconds]. */
    private fun runBot(e: GameEngine, maxSeconds: Int) {
        if (e.phase == GameEngine.Phase.READY) { e.onPress(); e.onRelease() }
        val dt = 1f / 60f
        var holdUntil = -1f
        var steps = 0
        while (e.phase == GameEngine.Phase.PLAYING && steps < 60 * maxSeconds) {
            val ahead = e.obstacles.filter { it.right - e.distance > GameConfig.Layout.BALL_X - 0.02f }
            val next = ahead.minByOrNull { it.left }
            if (next != null && !e.airborne) {
                var groupRight = next.right
                var maxH = next.height
                for (o in ahead.sortedBy { it.left }) {
                    if (o.left <= groupRight + 0.02f) {
                        groupRight = maxOf(groupRight, o.right)
                        maxH = maxOf(maxH, o.height)
                    }
                }
                val groupW = groupRight - next.left
                val needHold = maxH > 0.11f || groupW > 0.16f
                val gapW = next.left - e.distance - GameConfig.Layout.BALL_X
                if (gapW < e.speed * 0.22f) {
                    e.onPress()
                    holdUntil = if (needHold) e.time + 0.3f else e.time + 0.02f
                }
            }
            if (holdUntil >= 0f && e.time > holdUntil) {
                e.onRelease()
                holdUntil = -1f
            }
            e.update(dt)
            steps++
        }
        if (e.phase == GameEngine.Phase.DEAD) {
            val near = e.obstacles.filter { abs(it.x - e.distance - GameConfig.Layout.BALL_X) < 0.5f }
            println(
                "bot murió: score=${e.score} speed=${e.speed} obstáculos=" +
                    near.joinToString { "(dx=${it.x - e.distance - GameConfig.Layout.BALL_X}, b=${it.base}, h=${it.height})" },
            )
        }
    }

    @Test
    fun elBotSobreviveMuchoEnTodasLasSemillas() {
        var minScore = Int.MAX_VALUE
        for (seed in 1..40) {
            val events = Counter()
            val e = GameEngine(0, events, Random(seed))
            runBot(e, maxSeconds = 600)
            assertTrue(events.jumps > 0)
            minScore = minOf(minScore, e.score)
        }
        assertTrue(minScore > 2000, "alguna partida terminó demasiado pronto: $minScore")
    }

    @Test
    fun morirReiniciarYRecord() {
        val events = Counter()
        val e = GameEngine(0, events, Random(3))
        e.onPress(); e.onRelease()
        var n = 0
        while (e.phase != GameEngine.Phase.DEAD && n++ < 10_000) e.update(1f / 60f)
        assertEquals(GameEngine.Phase.DEAD, e.phase)
        assertEquals(1, events.crashes)
        assertTrue(e.isNewHighScore)
        assertEquals(e.score, events.savedHighScore)

        // Un toque inmediato se ignora (retraso anti-reinicio accidental).
        e.onPress(); e.onRelease()
        assertEquals(GameEngine.Phase.DEAD, e.phase)

        repeat(60) { e.update(1f / 60f) }
        e.onPress(); e.onRelease()
        assertEquals(GameEngine.Phase.PLAYING, e.phase)
        assertEquals(0, e.score)
        assertTrue(e.obstacles.all { it.left - e.distance > 1f })
    }

    @Test
    fun saltoCortoYLargo() {
        fun apex(holdSeconds: Float): Float {
            val e = GameEngine(0, Counter(), Random(1))
            e.onPress()
            var top = 0f
            var t = 0f
            while (true) {
                if (t >= holdSeconds) e.onRelease()
                e.update(1f / 120f)
                t += 1f / 120f
                top = maxOf(top, e.ballY)
                if (!e.airborne && t > 0.1f) break
            }
            return top
        }
        val short = apex(0f)
        val long = apex(0.3f)
        assertTrue(short > 0.09f && short < 0.12f, "salto corto: $short")
        assertTrue(long > 0.21f && long < 0.26f, "salto largo: $long")
    }

    @Test
    fun dingCadaCienPuntosYCicloDiaNoche() {
        val events = Counter()
        val e = GameEngine(0, events, Random(5))
        val startBlend = e.nightBlend
        runBot(e, maxSeconds = 70)
        assertTrue(e.score > GameConfig.Cycle.PERIOD_POINTS + 100, "puntuación: ${e.score}")
        assertEquals(e.score / GameConfig.Scoring.DING_EVERY, events.dings)
        // Tras cruzar PERIOD_POINTS la paleta debe haber cambiado (o estar cambiando) de lado.
        assertTrue(abs(e.nightBlend - startBlend) > 0.5f, "nightBlend=${e.nightBlend}")
    }
}
