package com.iscalexsoto.neonrunner

import com.iscalexsoto.neonrunner.GameConfig.Cycle
import com.iscalexsoto.neonrunner.GameConfig.Flow
import com.iscalexsoto.neonrunner.GameConfig.Layout
import com.iscalexsoto.neonrunner.GameConfig.Obstacles
import com.iscalexsoto.neonrunner.GameConfig.Physics
import com.iscalexsoto.neonrunner.GameConfig.Rogue
import com.iscalexsoto.neonrunner.GameConfig.Scoring
import com.iscalexsoto.neonrunner.GameConfig.Speed
import com.iscalexsoto.neonrunner.GameConfig.Visual
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Eventos que el motor dispara y que la capa Android traduce a sonido / vibración / guardado. */
interface GameEvents {
    fun onJump()
    fun onCrash()
    fun onDing()
    fun onHighScore(mode: GameMode, score: Int)
    fun onShoot() {}
    fun onSquareDestroyed() {}
    fun onLevelUp() {}
}

/** Los dos modos de juego. RUNNER es el original; ROGUE añade cuadrados, disparos y niveles. */
enum class GameMode { RUNNER, ROGUE }

/** Mejoras que se pueden elegir al subir de nivel (modo ROGUE). */
enum class Upgrade(val title: String, val description: String) {
    FIRE_RATE("CADENCIA", "Más disparos por segundo"),
    DAMAGE("DAÑO", "Cada bala quita más vida"),
    RANGE("ALCANCE", "Empiezas a disparar desde más lejos"),
    BULLET_SPEED("BALA RÁPIDA", "Las balas llegan antes al blanco"),
}

/**
 * Lógica pura del juego (sin dependencias de Android ni Compose).
 *
 * Sistema de coordenadas del mundo: unidades en anchos de pantalla (W), el suelo está en y = 0
 * y la "y" crece hacia ARRIBA. La cámara avanza con [distance]; la pelota está siempre en
 * x = [Layout.BALL_X] en pantalla y los obstáculos tienen una posición absoluta en el mundo.
 */
class GameEngine(
    initialHighScores: Map<GameMode, Int>,
    private val events: GameEvents,
    private val rng: Random = Random(System.nanoTime()),
) {
    enum class Phase { SELECT, PLAYING, LEVEL_UP, DEAD, PAUSED }

    /** Triángulo (pincho) en el suelo. [x] es el centro de la base en coordenadas de mundo (W). */
    class Obstacle(val x: Float, val base: Float, val height: Float) {
        val left get() = x - base / 2f
        val right get() = x + base / 2f
    }

    /** Cuadrado destructible (solo modo ROGUE). [x] es el centro, apoyado en el suelo. */
    class Square(val x: Float, val size: Float, val maxHp: Float, val xp: Int) {
        var hp = maxHp
        val left get() = x - size / 2f
        val right get() = x + size / 2f
        val hpFraction get() = (hp / maxHp).coerceIn(0f, 1f)
    }

    /** Disparo. Coordenadas de PANTALLA (W): vuela hacia la derecha a altura fija. */
    class Bullet(var x: Float, val y: Float) {
        var traveled = 0f
    }

    /** Rombo de experiencia. Coordenadas de mundo. */
    class Gem(var x: Float, var y: Float, var vy: Float, val value: Int) {
        var landed = false
        /** Segundos de vida; pasado GEM_LIFETIME sin ser recogido, desaparece. */
        var age = 0f
    }

    var phase = Phase.SELECT; private set
    var mode = GameMode.RUNNER; private set
    /** Tiempo total transcurrido (s), para animaciones ambientales. */
    var time = 0f; private set
    /** Distancia recorrida por la cámara (W). */
    var distance = 0f; private set
    var speed = Speed.START; private set
    /** Puntuación total. En ROGUE es [distanceScore] + [combatScore]. */
    var score = 0; private set
    var distanceScore = 0; private set
    var combatScore = 0; private set
    private val highScores = initialHighScores.toMutableMap()
    val highScore: Int get() = highScores[mode] ?: 0
    fun highScoreFor(m: GameMode): Int = highScores[m] ?: 0
    var isNewHighScore = false; private set

    /** Altura del punto más bajo de la pelota sobre el suelo (W). */
    var ballY = 0f; private set
    /** Velocidad vertical de la pelota (W/s, positiva hacia arriba). */
    var ballVy = 0f; private set
    var airborne = false; private set
    /** Deformación de la pelota: < 0 aplastada, > 0 estirada. Vuelve a 0 como un resorte. */
    var squash = 0f; private set
    private var squashVel = 0f

    /** 1 = paleta noche, 0 = paleta día. */
    var nightBlend = if (Cycle.START_AT_NIGHT) 1f else 0f; private set
    /** Segundos desde que se murió. */
    var deadTime = 0f; private set
    /** Segundos desde el último "ding" (negativo = ninguno activo). */
    var hudFlashTime = -1f; private set

    val obstacles = ArrayList<Obstacle>()

    // ---- estado Rogue-lite
    val squares = ArrayList<Square>()
    val bullets = ArrayList<Bullet>()
    val gems = ArrayList<Gem>()
    var level = 1; private set
    var xp = 0; private set
    val xpToNext: Int get() = Rogue.XP_BASE + (level - 1) * Rogue.XP_PER_LEVEL
    var fireRate = Rogue.FIRE_RATE_START; private set
    var damage = Rogue.DAMAGE_START; private set
    var range = Rogue.RANGE_START; private set
    var bulletSpeed = Rogue.BULLET_SPEED_START; private set
    var squaresDestroyed = 0; private set
    var xpCollected = 0; private set
    /** Veces que se ha elegido cada mejora. */
    val upgradeCounts = IntArray(Upgrade.entries.size)
    /** Mejoras ofrecidas en la pantalla de subida de nivel (vacío fuera de ella). */
    var levelUpOptions: List<Upgrade> = emptyList(); private set
    /** Texto de la última subida de nivel y segundos desde que ocurrió (negativo = ninguno). */
    var levelMessage = ""; private set
    var levelMessageTime = -1f; private set
    private var fireCooldown = 0f

    private var holding = false
    private var holdTime = 0f
    private var jumpBuffered = false
    private var nextSpawnX = Obstacles.FIRST_OBSTACLE_DISTANCE
    /** En game over: momento en que empezó el toque actual (negativo = no hay toque). */
    private var deadPressStart = -1f

    val canRestart: Boolean get() = phase == Phase.DEAD && deadTime >= Flow.RESTART_DELAY
    /** Progreso [0, 1] de la pulsación larga que lleva a la selección de modo. */
    val modeSwitchProgress: Float
        get() = if (phase == Phase.DEAD && deadPressStart >= 0f) ((time - deadPressStart) / Flow.MODE_SWITCH_HOLD).coerceIn(0f, 1f) else 0f

    // ---------------------------------------------------------------- input

    /**
     * Toque. [yFrac] es la posición vertical del dedo como fracción de la altura de la pantalla
     * (0 = arriba); solo importa en la pantalla de selección de modo.
     */
    fun onPress(yFrac: Float = 0f) {
        holding = true
        when (phase) {
            Phase.SELECT -> startGame(if (yFrac < Flow.MODE_SPLIT_Y) GameMode.RUNNER else GameMode.ROGUE)
            Phase.PLAYING -> {
                if (!airborne) {
                    jump()
                } else if (ballVy < 0f && ballY <= Physics.JUMP_BUFFER_HEIGHT) {
                    jumpBuffered = true
                }
            }
            Phase.LEVEL_UP -> {
                val index = cardIndexAt(yFrac)
                if (index != null) chooseUpgrade(index)
            }
            Phase.DEAD -> if (canRestart) deadPressStart = time
            Phase.PAUSED -> phase = Phase.PLAYING
        }
    }

    fun onRelease() {
        holding = false
        if (phase == Phase.DEAD && deadPressStart >= 0f) {
            // Toque corto: reiniciar el mismo modo. (La pulsación larga se resuelve en update.)
            deadPressStart = -1f
            startGame(mode)
        }
    }

    /** Llamar cuando la app pasa a segundo plano. */
    fun onAppPaused() {
        holding = false
        deadPressStart = -1f
        if (phase == Phase.PLAYING) phase = Phase.PAUSED
    }

    // ---------------------------------------------------------------- update

    fun update(dt: Float) {
        time += dt
        if (hudFlashTime >= 0f) {
            hudFlashTime += dt
            if (hudFlashTime > Scoring.HUD_FLASH_DURATION) hudFlashTime = -1f
        }
        if (levelMessageTime >= 0f) {
            levelMessageTime += dt
            if (levelMessageTime > Rogue.LEVEL_MESSAGE_DURATION) levelMessageTime = -1f
        }
        updateSquash(dt)
        when (phase) {
            Phase.PLAYING -> step(dt)
            Phase.DEAD -> {
                deadTime += dt
                if (deadPressStart >= 0f && time - deadPressStart >= Flow.MODE_SWITCH_HOLD) {
                    deadPressStart = -1f
                    holding = false
                    goToModeSelect()
                }
            }
            Phase.SELECT, Phase.PAUSED, Phase.LEVEL_UP -> Unit
        }
    }

    private fun step(dt: Float) {
        speed = if (mode == GameMode.ROGUE) {
            min(Speed.MAX, Speed.START + distanceScore * Rogue.SPEED_PER_POINT + (level - 1) * Rogue.SPEED_PER_LEVEL)
        } else {
            min(Speed.MAX, Speed.START + score * Speed.PER_POINT)
        }
        distance += speed * dt

        // Pelota
        if (airborne) {
            val holdActive = holding && ballVy > 0f && holdTime < Physics.HOLD_MAX
            val g = if (holdActive) Physics.GRAVITY_HOLD else Physics.GRAVITY
            if (holding) holdTime += dt
            ballVy = max(ballVy - g * dt, -Physics.MAX_FALL_SPEED)
            ballY += ballVy * dt
            if (ballY <= 0f) {
                ballY = 0f
                ballVy = 0f
                airborne = false
                squash = -Visual.SQUASH_ON_LAND
                squashVel = 0f
                if (jumpBuffered) {
                    jumpBuffered = false
                    jump()
                }
            }
        }

        spawnObstacles()
        cullObstacles()

        if (mode == GameMode.ROGUE) {
            updateFire(dt)
            updateBullets(dt)
            updateGems(dt)
        }

        // Puntuación
        distanceScore = (distance * Scoring.POINTS_PER_W).toInt()
        updateScore()

        // Ciclo día / noche: alterna cada PERIOD_POINTS y transiciona suavemente.
        val cycleIndex = score / Cycle.PERIOD_POINTS
        val nightIsTarget = (cycleIndex % 2 == 0) == Cycle.START_AT_NIGHT
        val target = if (nightIsTarget) 1f else 0f
        nightBlend = moveToward(nightBlend, target, dt / Cycle.TRANSITION_SECONDS)

        if (collides()) die()
    }

    /** Recalcula la puntuación total y dispara el "ding" al cruzar cada DING_EVERY puntos. */
    private fun updateScore() {
        val newScore = distanceScore + combatScore
        if (newScore / Scoring.DING_EVERY > score / Scoring.DING_EVERY) {
            hudFlashTime = 0f
            events.onDing()
        }
        score = newScore
    }

    private fun updateSquash(dt: Float) {
        // Resorte amortiguado hacia 0.
        squashVel += (-Visual.SQUASH_SPRING * squash - Visual.SQUASH_DAMPING * squashVel) * dt
        squash += squashVel * dt
    }

    private fun jump() {
        ballVy = Physics.JUMP_VELOCITY
        airborne = true
        holdTime = 0f
        jumpBuffered = false
        squash = Visual.STRETCH_ON_JUMP
        squashVel = 0f
        events.onJump()
    }

    private fun die() {
        phase = Phase.DEAD
        deadTime = 0f
        holding = false
        deadPressStart = -1f
        squash = -0.5f
        squashVel = 0f
        if (score > highScore) {
            highScores[mode] = score
            isNewHighScore = true
            events.onHighScore(mode, score)
        }
        events.onCrash()
    }

    private fun goToModeSelect() {
        resetWorld()
        phase = Phase.SELECT
    }

    private fun startGame(newMode: GameMode) {
        mode = newMode
        resetWorld()
        phase = Phase.PLAYING
    }

    private fun resetWorld() {
        obstacles.clear()
        squares.clear()
        bullets.clear()
        gems.clear()
        distance = 0f
        speed = Speed.START
        score = 0
        distanceScore = 0
        combatScore = 0
        ballY = 0f
        ballVy = 0f
        airborne = false
        squash = 0f
        squashVel = 0f
        holdTime = 0f
        jumpBuffered = false
        isNewHighScore = false
        deadTime = 0f
        hudFlashTime = -1f
        nextSpawnX = Obstacles.FIRST_OBSTACLE_DISTANCE
        level = 1
        xp = 0
        fireRate = Rogue.FIRE_RATE_START
        damage = Rogue.DAMAGE_START
        range = Rogue.RANGE_START
        bulletSpeed = Rogue.BULLET_SPEED_START
        squaresDestroyed = 0
        xpCollected = 0
        upgradeCounts.fill(0)
        levelUpOptions = emptyList()
        fireCooldown = 0f
        levelMessage = ""
        levelMessageTime = -1f
    }

    // ---------------------------------------------------------------- obstacles

    private fun spawnObstacles() {
        while (nextSpawnX < distance + Obstacles.SPAWN_X) {
            val groupEnd = if (mode == GameMode.ROGUE && rng.nextFloat() < Rogue.SQUARE_CHANCE) {
                spawnSquare(nextSpawnX)
            } else {
                spawnTriangleGroup(nextSpawnX)
            }
            // El hueco crece con la velocidad para que siempre se pueda saltar.
            val minGap = speed * (Obstacles.MAX_AIR_TIME * Obstacles.GAP_SAFETY + Obstacles.GAP_REACTION)
            val extra = rng.nextFloat() * speed * Obstacles.GAP_RANDOM_MAX
            nextSpawnX = groupEnd + minGap + extra
        }
    }

    /** Crea un grupo de 1 a 3 triángulos a partir de [start] y devuelve dónde termina. */
    private fun spawnTriangleGroup(start: Float): Float {
        val count = pickGroupSize()
        val maxHeight = when (count) {
            1 -> Obstacles.MAX_HEIGHT
            2 -> Obstacles.GROUP2_MAX_HEIGHT
            else -> Obstacles.GROUP3_MAX_HEIGHT
        }
        var x = start
        var groupEnd = x
        repeat(count) {
            val h = Obstacles.MIN_HEIGHT + rng.nextFloat() * (maxHeight - Obstacles.MIN_HEIGHT)
            val jitter = 1f + (rng.nextFloat() * 2f - 1f) * Obstacles.BASE_JITTER
            val b = h * Obstacles.BASE_RATIO * jitter
            obstacles.add(Obstacle(x + b / 2f, b, h))
            groupEnd = x + b
            x += b * Obstacles.GROUP_SPACING
        }
        return groupEnd
    }

    /** Crea un cuadrado (normal o blindado) a partir de [start] y devuelve dónde termina. */
    private fun spawnSquare(start: Float): Float {
        val big = speed >= Rogue.BIG_MIN_SPEED && rng.nextFloat() < Rogue.BIG_CHANCE
        val size = if (big) Rogue.BIG_SIZE else Rogue.SMALL_SIZE
        val baseHp = if (big) Rogue.BIG_HP else Rogue.SMALL_HP
        val growth = 1f + Rogue.HP_GROWTH_PER_1000 * (score / 1000)   // escalonado cada 1000 puntos
        val xpValue = if (big) Rogue.BIG_XP else Rogue.SMALL_XP
        squares.add(Square(start + size / 2f, size, baseHp * growth, xpValue))
        return start + size
    }

    private fun pickGroupSize(): Int {
        val r = rng.nextFloat()
        var acc = 0f
        var count = 1
        for (i in Obstacles.GROUP_WEIGHTS.indices) {
            acc += Obstacles.GROUP_WEIGHTS[i]
            if (r < acc) {
                count = i + 1
                break
            }
        }
        // A poca velocidad un grupo ancho no se puede librar: se reduce.
        if (count == 3 && speed < Obstacles.GROUP3_MIN_SPEED) count = 2
        if (count == 2 && speed < Obstacles.GROUP2_MIN_SPEED) count = 1
        return count
    }

    private fun cullObstacles() {
        val limit = distance - 0.3f
        obstacles.removeAll { it.right < limit }
        squares.removeAll { it.right < limit }
    }

    // ---------------------------------------------------------------- rogue: disparos

    private fun updateFire(dt: Float) {
        fireCooldown -= dt
        if (fireCooldown > 0f) return
        // Solo se dispara si hay un cuadrado al alcance, delante de la pelota.
        val ballFront = distance + Layout.BALL_X
        val hasTarget = squares.any { it.right > ballFront && it.left - ballFront < range }
        if (!hasTarget) return
        fireCooldown = 1f / fireRate
        bullets.add(Bullet(Layout.BALL_X + Layout.BALL_RADIUS, ballY + Layout.BALL_RADIUS))
        events.onShoot()
    }

    private fun updateBullets(dt: Float) {
        val step = bulletSpeed * dt
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += step
            b.traveled += step
            if (b.traveled > range + 0.1f || b.x > 1.2f) {
                it.remove()
                continue
            }
            val hit = squares.firstOrNull { s ->
                val left = s.left - distance
                val right = s.right - distance
                b.x + Rogue.BULLET_RADIUS >= left && b.x - Rogue.BULLET_RADIUS <= right &&
                    b.y - Rogue.BULLET_RADIUS <= s.size && b.y + Rogue.BULLET_RADIUS >= 0f
            } ?: continue
            it.remove()
            hit.hp -= damage
            if (hit.hp <= 0.001f) destroySquare(hit)
        }
    }

    private fun destroySquare(s: Square) {
        squares.remove(s)
        squaresDestroyed++
        gems.add(Gem(s.x, s.size / 2f, Rogue.GEM_POP_VY, s.xp))
        events.onSquareDestroyed()
    }

    private fun updateGems(dt: Float) {
        val ballCx = distance + Layout.BALL_X
        val ballCy = ballY + Layout.BALL_RADIUS
        val it = gems.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.age += dt
            if (!g.landed) {
                g.vy -= Rogue.GEM_GRAVITY * dt
                g.y += g.vy * dt
                if (g.y <= Rogue.GEM_RADIUS) {
                    g.y = Rogue.GEM_RADIUS
                    g.vy = 0f
                    g.landed = true
                }
            }
            // Persigue a la pelota en horizontal (en coordenadas de pantalla, así la velocidad del
            // juego no importa): si quedó atrás porque la pelota pasó por debajo, la alcanza.
            val dx0 = g.x - ballCx
            val stepX = Rogue.GEM_HOMING_SPEED * dt
            g.x = if (abs(dx0) <= stepX) ballCx else g.x - stepX * if (dx0 > 0f) 1f else -1f
            val dx = g.x - ballCx
            val dy = g.y - ballCy
            val reach = Layout.BALL_RADIUS + Rogue.GEM_RADIUS * 2f
            if (dx * dx + dy * dy <= reach * reach) {
                it.remove()
                gainXp(g.value)
            } else if (g.age > Rogue.GEM_LIFETIME) {
                it.remove()
            }
        }
    }

    private fun gainXp(amount: Int) {
        xp += amount
        xpCollected += amount
        combatScore = xpCollected * Rogue.XP_POINTS
        updateScore()
        if (xp >= xpToNext) openLevelUp()
    }

    /** Detiene el juego y ofrece mejoras distintas al azar. */
    private fun openLevelUp() {
        holding = false
        levelUpOptions = Upgrade.entries.shuffled(rng).take(Rogue.UPGRADE_OPTIONS)
        phase = Phase.LEVEL_UP
    }

    /** Índice de la carta bajo un toque a [yFrac] de la altura, o null si no toca ninguna. */
    fun cardIndexAt(yFrac: Float): Int? {
        for (i in levelUpOptions.indices) {
            val top = Rogue.cardTopY(i)
            if (yFrac >= top && yFrac <= top + Rogue.CARD_HEIGHT_Y) return i
        }
        return null
    }

    /** Aplica la mejora elegida y reanuda (o encadena otra subida si la XP sobrante alcanza). */
    fun chooseUpgrade(index: Int) {
        if (phase != Phase.LEVEL_UP) return
        val upgrade = levelUpOptions.getOrNull(index) ?: return
        when (upgrade) {
            Upgrade.FIRE_RATE -> fireRate *= Rogue.FIRE_RATE_MULT
            Upgrade.DAMAGE -> damage += Rogue.DAMAGE_ADD
            Upgrade.RANGE -> range += Rogue.RANGE_ADD
            Upgrade.BULLET_SPEED -> bulletSpeed *= Rogue.BULLET_SPEED_MULT
        }
        upgradeCounts[upgrade.ordinal]++
        xp -= xpToNext
        level++
        levelMessage = upgrade.title + " +"
        levelMessageTime = 0f
        events.onLevelUp()
        if (xp >= xpToNext) openLevelUp() else {
            levelUpOptions = emptyList()
            phase = Phase.PLAYING
        }
    }

    /** Texto "valor actual → valor con la mejora", para las cartas. */
    fun upgradePreview(upgrade: Upgrade): String = when (upgrade) {
        Upgrade.FIRE_RATE -> "${fmt(fireRate)} → ${fmt(fireRate * Rogue.FIRE_RATE_MULT)} por segundo"
        Upgrade.DAMAGE -> "${fmt(damage)} → ${fmt(damage + Rogue.DAMAGE_ADD)} de daño"
        Upgrade.RANGE -> "${fmt(range * 100)} → ${fmt((range + Rogue.RANGE_ADD) * 100)} % de pantalla"
        Upgrade.BULLET_SPEED -> "${fmt(bulletSpeed)} → ${fmt(bulletSpeed * Rogue.BULLET_SPEED_MULT)} pantallas/s"
    }

    private fun fmt(v: Float): String {
        val rounded = (v * 10f + 0.5f).toInt()
        return if (rounded % 10 == 0) (rounded / 10).toString() else "${rounded / 10}.${rounded % 10}"
    }

    // ---------------------------------------------------------------- collision

    private fun collides(): Boolean {
        val cx = Layout.BALL_X
        val cy = ballY + Layout.BALL_RADIUS
        val r = Layout.BALL_RADIUS * Physics.HITBOX_BALL
        for (o in obstacles) {
            val tx = o.x - distance
            if (abs(tx - cx) > o.base + Layout.BALL_RADIUS) continue
            val b = o.base * Physics.HITBOX_TRIANGLE
            val h = o.height * Physics.HITBOX_TRIANGLE
            if (circleHitsTriangle(cx, cy, r, tx - b / 2f, 0f, tx + b / 2f, 0f, tx, h)) return true
        }
        for (s in squares) {
            val sx = s.x - distance
            if (abs(sx - cx) > s.size + Layout.BALL_RADIUS) continue
            val half = s.size * Rogue.HITBOX_SQUARE / 2f
            if (circleHitsRect(cx, cy, r, sx - half, 0f, sx + half, s.size * Rogue.HITBOX_SQUARE)) return true
        }
        return false
    }

    companion object {
        fun moveToward(current: Float, target: Float, maxDelta: Float): Float =
            when {
                current < target -> min(current + maxDelta, target)
                current > target -> max(current - maxDelta, target)
                else -> target
            }

        /** ¿El círculo (cx, cy, r) toca el triángulo A-B-P? */
        fun circleHitsTriangle(
            cx: Float, cy: Float, r: Float,
            ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float,
        ): Boolean {
            // Centro dentro del triángulo
            val d1 = edgeSign(cx, cy, ax, ay, bx, by)
            val d2 = edgeSign(cx, cy, bx, by, px, py)
            val d3 = edgeSign(cx, cy, px, py, ax, ay)
            val hasNeg = d1 < 0f || d2 < 0f || d3 < 0f
            val hasPos = d1 > 0f || d2 > 0f || d3 > 0f
            if (!(hasNeg && hasPos)) return true
            // Centro cerca de algún lado
            return segmentDistance(cx, cy, ax, ay, bx, by) < r ||
                segmentDistance(cx, cy, bx, by, px, py) < r ||
                segmentDistance(cx, cy, px, py, ax, ay) < r
        }

        /** ¿El círculo (cx, cy, r) toca el rectángulo [left, right] x [bottom, top]? */
        fun circleHitsRect(cx: Float, cy: Float, r: Float, left: Float, bottom: Float, right: Float, top: Float): Boolean {
            val nx = cx.coerceIn(left, right)
            val ny = cy.coerceIn(bottom, top)
            val dx = cx - nx
            val dy = cy - ny
            return dx * dx + dy * dy < r * r
        }

        private fun edgeSign(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float =
            (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3)

        private fun segmentDistance(cx: Float, cy: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((cx - x1) * dx + (cy - y1) * dy) / len2).coerceIn(0f, 1f)
            val ex = x1 + t * dx - cx
            val ey = y1 + t * dy - cy
            return sqrt(ex * ex + ey * ey)
        }
    }
}
