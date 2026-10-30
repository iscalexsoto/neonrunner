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
        var shots = 0
        var pops = 0
        var levelUps = 0
        var savedHighScore = -1
        var savedMode: GameMode? = null
        override fun onJump() { jumps++ }
        override fun onCrash() { crashes++ }
        override fun onDing() { dings++ }
        override fun onShoot() { shots++ }
        override fun onSquareDestroyed() { pops++ }
        override fun onLevelUp() { levelUps++ }
        override fun onHighScore(mode: GameMode, score: Int) { savedHighScore = score; savedMode = mode }
    }

    private fun newEngine(seed: Int, events: GameEvents = Counter()) = GameEngine(emptyMap(), events, Random(seed))

    /** Toque en la pantalla de selección: arriba = Runner, abajo = Rogue. */
    private fun select(e: GameEngine, mode: GameMode) {
        e.onPress(yFrac = if (mode == GameMode.RUNNER) 0.1f else 0.9f)
        e.onRelease()
        assertEquals(GameEngine.Phase.PLAYING, e.phase)
        assertEquals(mode, e.mode)
    }

    /** Peligro genérico delante de la pelota (triángulo o cuadrado). */
    private class Hazard(val left: Float, val right: Float, val height: Float, val isSquare: Boolean)

    private fun hazards(e: GameEngine): List<Hazard> =
        e.obstacles.map { Hazard(it.left, it.right, it.height, false) } + e.squares.map { Hazard(it.left, it.right, it.size, true) }

    /** Deja que el bot juegue hasta morir o hasta [maxSeconds]. Salta todo lo que se acerca. */
    private fun runBot(e: GameEngine, maxSeconds: Int) {
        val dt = 1f / 60f
        var holdUntil = -1f
        var steps = 0
        while (e.phase == GameEngine.Phase.PLAYING && steps < 60 * maxSeconds) {
            val ahead = hazards(e).filter { it.right - e.distance > GameConfig.Layout.BALL_X - 0.02f }
            val next = ahead.minByOrNull { it.left }
            if (next != null && !e.airborne) {
                var groupRight = next.right
                var maxH = next.height
                var anySquare = next.isSquare
                for (o in ahead.sortedBy { it.left }) {
                    if (o.left <= groupRight + 0.02f) {
                        groupRight = maxOf(groupRight, o.right)
                        maxH = maxOf(maxH, o.height)
                        anySquare = anySquare || o.isSquare
                    }
                }
                val groupW = groupRight - next.left
                // Un cuadrado es una caja: siempre pide salto alto.
                val needHold = anySquare || maxH > 0.11f || groupW > 0.16f
                val gapW = next.left - e.distance - GameConfig.Layout.BALL_X
                // Ante un cuadrado pequeño conviene quedarse disparando hasta el último momento;
                // el blindado es una caja alta y hay que saltarlo con antelación.
                val lead = when {
                    anySquare && maxH > 0.1f -> 0.26f
                    anySquare -> 0.16f
                    else -> 0.22f
                }
                if (gapW < e.speed * lead) {
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
            val near = hazards(e).filter { abs((it.left + it.right) / 2f - e.distance - GameConfig.Layout.BALL_X) < 0.5f }
            println(
                "bot murió: modo=${e.mode} score=${e.score} speed=${e.speed} peligros=" +
                    near.joinToString { "(dx=${it.left - e.distance - GameConfig.Layout.BALL_X}, w=${it.right - it.left}, h=${it.height})" },
            )
        }
    }

    @Test
    fun elBotSobreviveMuchoEnTodasLasSemillasRunner() {
        var minScore = Int.MAX_VALUE
        for (seed in 1..40) {
            val events = Counter()
            val e = newEngine(seed, events)
            select(e, GameMode.RUNNER)
            runBot(e, maxSeconds = 600)
            assertTrue(events.jumps > 0)
            assertTrue(e.squares.isEmpty() && events.shots == 0, "en Runner no hay cuadrados ni disparos")
            minScore = minOf(minScore, e.score)
        }
        assertTrue(minScore > 2000, "alguna partida terminó demasiado pronto: $minScore")
    }

    @Test
    fun elBotSobreviveYSubeDeNivelEnRogue() {
        var minScore = Int.MAX_VALUE
        var minLevel = Int.MAX_VALUE
        for (seed in 1..30) {
            val events = Counter()
            val e = newEngine(seed, events)
            select(e, GameMode.ROGUE)
            runBot(e, maxSeconds = 300)
            minScore = minOf(minScore, e.score)
            minLevel = minOf(minLevel, e.level)
            assertTrue(events.shots > 0, "debería haber disparado")
            assertEquals(e.squaresDestroyed, events.pops)
            assertEquals(e.level - 1, events.levelUps)
            println("rogue seed=$seed score=${e.score} nivel=${e.level} destruidos=${e.squaresDestroyed} cadencia=${e.fireRate} daño=${e.damage} alcance=${e.range}")
        }
        assertTrue(minScore > 1500, "alguna partida terminó demasiado pronto: $minScore")
        assertTrue(minLevel >= 2, "el bot debería subir de nivel aunque salte casi todo: $minLevel")
    }

    @Test
    fun lasBalasMatanCuadradosYSueltanExperiencia() {
        // Pelota quieta en el suelo (sin saltar) hasta que aparezca el primer cuadrado.
        var found = false
        for (seed in 1..60) {
            val events = Counter()
            val e = newEngine(seed, events)
            select(e, GameMode.ROGUE)
            var n = 0
            while (e.phase == GameEngine.Phase.PLAYING && n++ < 60 * 8) {
                e.update(1f / 60f)
                if (e.squaresDestroyed > 0) break
            }
            if (e.squaresDestroyed > 0) {
                found = true
                assertTrue(events.shots > 0)
                // Al menos un rombo apareció, y si llegó al suelo se desliza hacia la pelota.
                assertTrue(e.gems.isNotEmpty() || e.xp > 0 || e.level > 1)
                break
            }
        }
        assertTrue(found, "con 60 semillas alguna debería destruir un cuadrado sin saltar")
    }

    /** Experiencia total ganada en la partida (niveles completados + barra actual). */
    private fun totalXp(e: GameEngine): Int =
        (1 until e.level).sumOf { GameConfig.Rogue.XP_BASE + (it - 1) * GameConfig.Rogue.XP_PER_LEVEL } + e.xp

    @Test
    fun losRombosAlcanzanALaPelotaTambienAVelocidadAlta() {
        // A partir de ~1000 puntos la pelota pasaba por debajo del rombo en el aire y este,
        // al caer detrás, nunca la alcanzaba. Ahora persigue a la pelota en ambos sentidos.
        var observed = 0
        for (seed in 1..25) {
            val events = Counter()
            val e = newEngine(seed, events)
            select(e, GameMode.ROGUE)
            runBotUntilScore(e, 1500)
            if (e.phase != GameEngine.Phase.PLAYING) continue
            // Dejar de saltar: la pelota se queda en el suelo disparando hasta destruir un cuadrado.
            val popsBefore = events.pops
            var n = 0
            while (e.phase == GameEngine.Phase.PLAYING && events.pops == popsBefore && n++ < 60 * 4) e.update(1f / 60f)
            if (events.pops == popsBefore) continue
            val xpBefore = totalXp(e)
            val speedAtKill = e.speed
            // El rombo debe llegar a la pelota en poco más de un segundo, aunque la pelota pase
            // por debajo mientras está en el aire.
            var collected = false
            var t = 0f
            while (e.phase == GameEngine.Phase.PLAYING && t < 1.6f) {
                e.update(1f / 60f)
                t += 1f / 60f
                if (totalXp(e) > xpBefore) { collected = true; break }
            }
            println("seed=$seed velocidad=$speedAtKill rombo recogido=$collected en ${"%.2f".format(t)} s")
            assertTrue(speedAtKill > 0.9f)
            assertTrue(collected, "el rombo no alcanzó a la pelota a velocidad $speedAtKill (seed $seed)")
            observed++
            if (observed >= 6) break
        }
        assertTrue(observed >= 6, "pocas observaciones: $observed")
    }

    private fun runBotUntilScore(e: GameEngine, target: Int) {
        var guard = 0
        while (e.phase == GameEngine.Phase.PLAYING && e.score < target && guard++ < 200) runBot(e, maxSeconds = 2)
    }

    @Test
    fun morirReiniciarYRecordPorModo() {
        val events = Counter()
        val e = newEngine(3, events)
        select(e, GameMode.RUNNER)
        var n = 0
        while (e.phase != GameEngine.Phase.DEAD && n++ < 10_000) e.update(1f / 60f)
        assertEquals(GameEngine.Phase.DEAD, e.phase)
        assertEquals(1, events.crashes)
        assertTrue(e.isNewHighScore)
        assertEquals(e.score, events.savedHighScore)
        assertEquals(GameMode.RUNNER, events.savedMode)
        assertEquals(0, e.highScoreFor(GameMode.ROGUE))

        // Un toque inmediato se ignora (retraso anti-reinicio accidental).
        e.onPress(); e.onRelease()
        assertEquals(GameEngine.Phase.DEAD, e.phase)

        // Toque corto tras el retraso: reinicia el mismo modo.
        repeat(60) { e.update(1f / 60f) }
        e.onPress()
        repeat(3) { e.update(1f / 60f) }
        e.onRelease()
        assertEquals(GameEngine.Phase.PLAYING, e.phase)
        assertEquals(GameMode.RUNNER, e.mode)
        assertEquals(0, e.score)
        assertTrue(e.obstacles.all { it.left - e.distance > 1f })
    }

    @Test
    fun pulsacionLargaEnGameOverVuelveALaSeleccionDeModo() {
        val e = newEngine(4)
        select(e, GameMode.ROGUE)
        var n = 0
        while (e.phase != GameEngine.Phase.DEAD && n++ < 20_000) e.update(1f / 60f)
        assertEquals(GameEngine.Phase.DEAD, e.phase)
        repeat(60) { e.update(1f / 60f) }
        e.onPress()
        var held = 0f
        while (held < GameConfig.Flow.MODE_SWITCH_HOLD + 0.1f) {
            e.update(1f / 60f)
            held += 1f / 60f
        }
        assertEquals(GameEngine.Phase.SELECT, e.phase)
        e.onRelease()
        assertEquals(GameEngine.Phase.SELECT, e.phase)
        // Desde la selección se puede entrar al otro modo.
        select(e, GameMode.RUNNER)
    }

    @Test
    fun saltoCortoYLargo() {
        fun apex(holdSeconds: Float): Float {
            val e = newEngine(1)
            select(e, GameMode.RUNNER)
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
        val e = newEngine(5, events)
        select(e, GameMode.RUNNER)
        val startBlend = e.nightBlend
        runBot(e, maxSeconds = 70)
        assertTrue(e.score > GameConfig.Cycle.PERIOD_POINTS + 100, "puntuación: ${e.score}")
        assertEquals(e.score / GameConfig.Scoring.DING_EVERY, events.dings)
        // Tras cruzar PERIOD_POINTS la paleta debe haber cambiado (o estar cambiando) de lado.
        assertTrue(abs(e.nightBlend - startBlend) > 0.5f, "nightBlend=${e.nightBlend}")
    }
}
