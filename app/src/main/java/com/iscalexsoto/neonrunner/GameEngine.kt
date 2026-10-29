package com.iscalexsoto.neonrunner

import com.iscalexsoto.neonrunner.GameConfig.Cycle
import com.iscalexsoto.neonrunner.GameConfig.Flow
import com.iscalexsoto.neonrunner.GameConfig.Layout
import com.iscalexsoto.neonrunner.GameConfig.Obstacles
import com.iscalexsoto.neonrunner.GameConfig.Physics
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
    fun onHighScore(score: Int)
}

/**
 * Lógica pura del juego (sin dependencias de Android ni Compose).
 *
 * Sistema de coordenadas del mundo: unidades en anchos de pantalla (W), el suelo está en y = 0
 * y la "y" crece hacia ARRIBA. La cámara avanza con [distance]; la pelota está siempre en
 * x = [Layout.BALL_X] en pantalla y los obstáculos tienen una posición absoluta en el mundo.
 */
class GameEngine(
    initialHighScore: Int,
    private val events: GameEvents,
    private val rng: Random = Random(System.nanoTime()),
) {
    enum class Phase { READY, PLAYING, DEAD, PAUSED }

    /** Triángulo en el suelo. [x] es el centro de la base en coordenadas de mundo (W). */
    class Obstacle(val x: Float, val base: Float, val height: Float) {
        val left get() = x - base / 2f
        val right get() = x + base / 2f
    }

    var phase = Phase.READY; private set
    /** Tiempo total transcurrido (s), para animaciones ambientales. */
    var time = 0f; private set
    /** Distancia recorrida por la cámara (W). */
    var distance = 0f; private set
    var speed = Speed.START; private set
    var score = 0; private set
    var highScore = initialHighScore; private set
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

    private var holding = false
    private var holdTime = 0f
    private var jumpBuffered = false
    private var nextSpawnX = Obstacles.FIRST_OBSTACLE_DISTANCE

    val canRestart: Boolean get() = phase == Phase.DEAD && deadTime >= Flow.RESTART_DELAY

    // ---------------------------------------------------------------- input

    fun onPress() {
        holding = true
        when (phase) {
            Phase.READY -> {
                phase = Phase.PLAYING
                jump()
            }
            Phase.PLAYING -> {
                if (!airborne) {
                    jump()
                } else if (ballVy < 0f && ballY <= Physics.JUMP_BUFFER_HEIGHT) {
                    jumpBuffered = true
                }
            }
            Phase.DEAD -> if (canRestart) restart()
            Phase.PAUSED -> phase = Phase.PLAYING
        }
    }

    fun onRelease() {
        holding = false
    }

    /** Llamar cuando la app pasa a segundo plano. */
    fun onAppPaused() {
        holding = false
        if (phase == Phase.PLAYING) phase = Phase.PAUSED
    }

    // ---------------------------------------------------------------- update

    fun update(dt: Float) {
        time += dt
        if (hudFlashTime >= 0f) {
            hudFlashTime += dt
            if (hudFlashTime > Scoring.HUD_FLASH_DURATION) hudFlashTime = -1f
        }
        updateSquash(dt)
        when (phase) {
            Phase.PLAYING -> step(dt)
            Phase.DEAD -> deadTime += dt
            Phase.READY, Phase.PAUSED -> Unit
        }
    }

    private fun step(dt: Float) {
        speed = min(Speed.MAX, Speed.START + score * Speed.PER_POINT)
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

        // Puntuación
        val newScore = (distance * Scoring.POINTS_PER_W).toInt()
        if (newScore / Scoring.DING_EVERY > score / Scoring.DING_EVERY) {
            hudFlashTime = 0f
            events.onDing()
        }
        score = newScore

        // Ciclo día / noche: alterna cada PERIOD_POINTS y transiciona suavemente.
        val cycleIndex = score / Cycle.PERIOD_POINTS
        val nightIsTarget = (cycleIndex % 2 == 0) == Cycle.START_AT_NIGHT
        val target = if (nightIsTarget) 1f else 0f
        nightBlend = moveToward(nightBlend, target, dt / Cycle.TRANSITION_SECONDS)

        if (collides()) die()
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
        squash = -0.5f
        squashVel = 0f
        if (score > highScore) {
            highScore = score
            isNewHighScore = true
            events.onHighScore(score)
        }
        events.onCrash()
    }

    private fun restart() {
        obstacles.clear()
        distance = 0f
        speed = Speed.START
        score = 0
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
        phase = Phase.PLAYING
    }

    // ---------------------------------------------------------------- obstacles

    private fun spawnObstacles() {
        while (nextSpawnX < distance + Obstacles.SPAWN_X) {
            val count = pickGroupSize()
            val maxHeight = when (count) {
                1 -> Obstacles.MAX_HEIGHT
                2 -> Obstacles.GROUP2_MAX_HEIGHT
                else -> Obstacles.GROUP3_MAX_HEIGHT
            }
            var x = nextSpawnX
            var groupEnd = x
            repeat(count) {
                val h = Obstacles.MIN_HEIGHT + rng.nextFloat() * (maxHeight - Obstacles.MIN_HEIGHT)
                val jitter = 1f + (rng.nextFloat() * 2f - 1f) * Obstacles.BASE_JITTER
                val b = h * Obstacles.BASE_RATIO * jitter
                obstacles.add(Obstacle(x + b / 2f, b, h))
                groupEnd = x + b
                x += b * Obstacles.GROUP_SPACING
            }
            // El hueco crece con la velocidad para que siempre se pueda saltar.
            val minGap = speed * (Obstacles.MAX_AIR_TIME * Obstacles.GAP_SAFETY + Obstacles.GAP_REACTION)
            val extra = rng.nextFloat() * speed * Obstacles.GAP_RANDOM_MAX
            nextSpawnX = groupEnd + minGap + extra
        }
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
