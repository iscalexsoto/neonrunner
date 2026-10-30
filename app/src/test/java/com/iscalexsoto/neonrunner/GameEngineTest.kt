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
        override fun onEnemyDestroyed() { pops++ }
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

    /** Peligro en el suelo delante de la pelota (triángulo o cuadrado). Los voladores se pasan por debajo. */
    private class Hazard(val left: Float, val right: Float, val height: Float, val isSquare: Boolean)

    private fun hazards(e: GameEngine): List<Hazard> =
        e.obstacles.map { Hazard(it.left, it.right, it.height, false) } +
            e.enemies.filter { !it.flying }.map { Hazard(it.left, it.right, it.size, true) } +
            // Los voladores del Runner solo se saltan cuando van a ras de suelo; los demás se pasan por debajo.
            e.flyers.filter { it.heightIndex == 0 }.map { Hazard(it.left, it.right, it.size, true) }

    private fun running(e: GameEngine) = e.phase == GameEngine.Phase.PLAYING || e.phase == GameEngine.Phase.LEVEL_UP

    /**
     * Deja que el bot juegue hasta morir o hasta [maxSeconds]. Salta todo lo que se acerca en el
     * suelo. Con [attackFlyers], además salta alto cuando un volador está a la distancia justa para
     * dispararle desde el aire y aterrizar antes de que llegue.
     */
    private fun runBot(e: GameEngine, maxSeconds: Int, attackFlyers: Boolean = false) {
        val dt = 1f / 60f
        var holdUntil = -1f
        var steps = 0
        while (running(e) && steps < 60 * maxSeconds) {
            if (e.phase == GameEngine.Phase.LEVEL_UP) {
                // Elige la carta según el nivel, para variar las mejoras.
                e.chooseUpgrade(e.level % e.levelUpOptions.size)
                continue
            }
            if (attackFlyers && !e.airborne) {
                val minDist = 0.78f * e.speed + 0.14f
                val ballFront = e.distance + GameConfig.Layout.BALL_X
                val flyerAhead = e.enemies.any { it.flying && it.left - ballFront in minDist..e.effectiveRange }
                val groundNear = hazards(e).any { it.left - ballFront < 1.0f && it.right > ballFront }
                if (flyerAhead && !groundNear) {
                    e.onPress()
                    holdUntil = e.time + 0.3f
                    e.update(dt)
                    steps++
                    continue
                }
            }
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
            assertTrue(e.enemies.isEmpty() && events.shots == 0, "en Runner no hay enemigos ni disparos")
            minScore = minOf(minScore, e.score)
        }
        assertTrue(minScore > 2000, "alguna partida terminó demasiado pronto: $minScore")
    }

    @Test
    fun losVoladoresDelRunnerSalenALasCuatroAlturasYSeEsquivanComoSeAnuncia() {
        // Geometría de las cuatro alturas respecto a la pelota (hitboxes incluidas).
        val r = GameConfig.Layout.BALL_RADIUS * GameConfig.Physics.HITBOX_BALL
        val half = GameConfig.Obstacles.FLYER_SIZE * GameConfig.Rogue.HITBOX_SQUARE / 2f
        fun hits(flyerBottom: Float, ballY: Float): Boolean {
            val mid = flyerBottom + GameConfig.Obstacles.FLYER_SIZE / 2f
            return GameEngine.circleHitsRect(0f, ballY + GameConfig.Layout.BALL_RADIUS, r, -half, mid - half, half, mid + half)
        }
        val h = GameConfig.Obstacles.FLYER_HEIGHTS
        val shortApex = 0.106f
        val longApex = 0.234f
        // Suelo: choca en el suelo, se libra en el punto alto de cualquier salto.
        assertTrue(hits(h[0], 0f) && !hits(h[0], shortApex))
        // Salto corto: pasa por debajo, choca en el salto corto, se libra con el largo.
        assertTrue(!hits(h[1], 0f) && hits(h[1], shortApex) && !hits(h[1], longApex))
        // Salto largo: pasa por debajo y con el corto, choca con el largo.
        assertTrue(!hits(h[2], 0f) && !hits(h[2], shortApex) && hits(h[2], longApex))
        // Muy alto: solo choca en lo más alto del salto largo.
        assertTrue(!hits(h[3], 0f) && !hits(h[3], shortApex) && hits(h[3], longApex))

        // En partida aparecen todas las alturas y el bot (que salta solo los de suelo) sobrevive.
        val seen = IntArray(h.size)
        for (seed in 1..20) {
            val e = newEngine(seed)
            select(e, GameMode.RUNNER)
            var steps = 0
            while (running(e) && steps < 60 * 240) {
                runBot(e, maxSeconds = 1)
                for (f in e.flyers) if (f.x - e.distance < 1.2f) seen[f.heightIndex] = 1
                steps += 60
            }
            assertEquals(GameEngine.Phase.PLAYING, e.phase, "el bot murió en Runner con voladores (seed $seed, score ${e.score})")
        }
        assertTrue(seen.all { it == 1 }, "no salieron todas las alturas: ${seen.toList()}")
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
            assertEquals(e.enemiesDestroyed, events.pops)
            assertEquals(e.level - 1, events.levelUps)
            assertEquals(e.distanceScore + e.combatScore, e.score)
            assertEquals(e.xpCollected * GameConfig.Rogue.XP_POINTS, e.combatScore)
            assertEquals(e.level - 1, e.upgradeCounts.sum())
            println("rogue seed=$seed score=${e.score} nivel=${e.level} destruidos=${e.enemiesDestroyed} cadencia=${e.fireRate} daño=${e.damage} alcance=${e.range}")
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
                if (e.enemiesDestroyed > 0) break
            }
            if (e.enemiesDestroyed > 0) {
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
        for (seed in 1..60) {
            val events = Counter()
            val e = newEngine(seed, events)
            select(e, GameMode.ROGUE)
            runBotUntilScore(e, 1800)
            if (e.phase != GameEngine.Phase.PLAYING) continue
            // Dejar de saltar: la pelota se queda en el suelo disparando hasta destruir un enemigo.
            val popsBefore = events.pops
            // Se toma ANTES del pop: si el enemigo muere pegado a la pelota, el rombo se recoge en el mismo frame.
            val xpBefore = e.xpCollected
            var n = 0
            while (e.phase == GameEngine.Phase.PLAYING && events.pops == popsBefore && n++ < 60 * 4) e.update(1f / 60f)
            if (events.pops == popsBefore) continue
            val speedAtKill = e.speed
            // El rombo debe llegar a la pelota en poco más de un segundo, aunque la pelota pase
            // por debajo mientras está en el aire.
            var collected = e.xpCollected > xpBefore
            var t = 0f
            val trace = StringBuilder()
            while (!collected && running(e) && t < 1.6f) {
                e.update(1f / 60f)
                t += 1f / 60f
                if ((t * 60f).toInt() % 6 == 0) {
                    trace.append("  t=${"%.2f".format(t)} ballY=${"%.3f".format(e.ballY)} air=${e.airborne} gems=")
                    trace.append(e.gems.joinToString { "(dx=${"%.3f".format(it.x - e.distance - GameConfig.Layout.BALL_X)} y=${"%.3f".format(it.y)})" })
                    trace.append(" xp=${e.xp} lvl=${e.level}\n")
                }
                if (e.xpCollected > xpBefore) { collected = true; break }
            }
            if (!collected) println(trace)
            println("seed=$seed velocidad=$speedAtKill rombo recogido=$collected en ${"%.2f".format(t)} s fase=${e.phase}")
            // Si la pelota chocó antes de que llegara el rombo, la observación no vale.
            if (!collected && e.phase == GameEngine.Phase.DEAD) continue
            assertTrue(speedAtKill > 0.8f, "la prueba debe ocurrir a velocidad alta: $speedAtKill")
            assertTrue(collected, "el rombo no alcanzó a la pelota a velocidad $speedAtKill (seed $seed)")
            observed++
            if (observed >= 5) break
        }
        assertTrue(observed >= 5, "pocas observaciones: $observed")
    }

    private fun runBotUntilScore(e: GameEngine, target: Int) {
        var guard = 0
        while (running(e) && e.score < target && guard++ < 200) runBot(e, maxSeconds = 2)
    }

    @Test
    fun subirDeNivelDetieneElJuegoYAplicaLaMejoraElegida() {
        val events = Counter()
        val e = newEngine(9, events)
        select(e, GameMode.ROGUE)
        var guard = 0
        while (e.phase == GameEngine.Phase.PLAYING && guard++ < 60 * 120) {
            // Bot mínimo: salta todo con salto alto para llegar a la primera subida de nivel.
            val next = hazards(e).filter { it.right - e.distance > GameConfig.Layout.BALL_X }.minByOrNull { it.left }
            if (next != null && !e.airborne && next.left - e.distance - GameConfig.Layout.BALL_X < e.speed * 0.26f) e.onPress()
            if (e.airborne && e.ballVy < 0.3f) e.onRelease()
            e.update(1f / 60f)
        }
        assertEquals(GameEngine.Phase.LEVEL_UP, e.phase, "debería haber llegado a una subida de nivel")
        assertEquals(GameConfig.Rogue.UPGRADE_OPTIONS, e.levelUpOptions.size)
        assertEquals(e.levelUpOptions.size, e.levelUpOptions.distinct().size, "las opciones no se repiten")

        // Congelado: ni la distancia ni los toques fuera de las cartas cambian nada.
        val distance = e.distance
        repeat(30) { e.update(1f / 60f) }
        e.onPress(yFrac = 0.99f); e.onRelease()
        assertEquals(distance, e.distance)
        assertEquals(GameEngine.Phase.LEVEL_UP, e.phase)

        // Tocar la segunda carta aplica exactamente esa mejora.
        val chosen = e.levelUpOptions[1]
        val fireRate = e.fireRate; val damage = e.damage; val range = e.range; val bulletSpeed = e.bulletSpeed
        val y = GameConfig.Rogue.cardTopY(1) + GameConfig.Rogue.CARD_HEIGHT_Y / 2f
        e.onPress(yFrac = y); e.onRelease()
        assertEquals(2, e.level)
        assertEquals(1, events.levelUps)
        assertEquals(1, e.upgradeCounts[chosen.ordinal])
        when (chosen) {
            Upgrade.FIRE_RATE -> assertTrue(e.fireRate > fireRate && e.damage == damage)
            Upgrade.DAMAGE -> assertTrue(e.damage > damage && e.fireRate == fireRate)
            Upgrade.RANGE -> assertTrue(e.range > range && e.bulletSpeed == bulletSpeed)
            Upgrade.BULLET_SPEED -> assertTrue(e.bulletSpeed > bulletSpeed && e.range == range)
        }
        assertTrue(e.phase == GameEngine.Phase.PLAYING || e.phase == GameEngine.Phase.LEVEL_UP)
        assertTrue(e.levelMessage.startsWith(chosen.title))

        // La velocidad en Rogue sube con el nivel: un paso más y ya incluye el bono del nivel 2.
        if (e.phase == GameEngine.Phase.PLAYING) {
            e.update(1f / 60f)
            val expected = GameConfig.Speed.START + e.distanceScore * GameConfig.Rogue.SPEED_PER_POINT + GameConfig.Rogue.SPEED_PER_LEVEL
            assertTrue(abs(e.speed - expected) < 1e-4f, "velocidad ${e.speed} esperada $expected")
        }
    }

    @Test
    fun elVoladorSePasaPorDebajoYSeDerribaDesdeElAire() {
        // 1) Pasar por debajo sin saltar nunca mata.
        val ballTop = GameConfig.Layout.BALL_RADIUS * 2f
        assertTrue(GameConfig.Rogue.FLYER_BOTTOM - GameConfig.Rogue.FLYER_BOB_AMPLITUDE > ballTop + 0.03f, "el volador debe dejar pasar a la pelota por debajo")
        // 2) El bot (que no salta ante voladores) sobrevive, y si salta alto cuando uno está a media
        //    distancia, las balas disparadas desde el aire lo derriban alguna vez.
        var flyersKilled = 0
        var deaths = 0
        for (seed in 1..10) {
            val events = Counter()
            val e = newEngine(seed, events)
            select(e, GameMode.ROGUE)
            runBot(e, maxSeconds = 300, attackFlyers = true)
            if (e.phase == GameEngine.Phase.DEAD) deaths++
            val killed = e.destroyedByKind[GameEngine.EnemyKind.FLYER.ordinal]
            flyersKilled += killed
            println("volador seed=$seed derribados=$killed destruidos(total)=${e.enemiesDestroyed} score=${e.score} fase=${e.phase}")
        }
        assertTrue(flyersKilled > 0, "ningún volador derribado en 10 partidas")
        assertTrue(deaths <= 2, "el bot murió demasiado con voladores: $deaths")
    }

    @Test
    fun elCuadradoGrandeSePuedeDestruirConMejoras() {
        // Partida larga con el bot (elige cartas): en algún momento debe caer algún blindado.
        var bigKilled = 0
        for (seed in 1..10) {
            val events = Counter()
            val e = newEngine(seed, events)
            select(e, GameMode.ROGUE)
            runBot(e, maxSeconds = 400)
            val big = e.destroyedByKind[GameEngine.EnemyKind.BIG.ordinal]
            bigKilled += big
            println("grande seed=$seed nivel=${e.level} destruidos=${e.enemiesDestroyed} grandes=$big score=${e.score}")
        }
        assertTrue(bigKilled > 0, "ningún cuadrado grande cayó en 10 partidas")
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
        while (e.phase != GameEngine.Phase.DEAD && n++ < 20_000) {
            if (e.phase == GameEngine.Phase.LEVEL_UP) e.chooseUpgrade(0) else e.update(1f / 60f)
        }
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
