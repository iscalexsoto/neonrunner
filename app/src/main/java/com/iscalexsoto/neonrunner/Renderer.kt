package com.iscalexsoto.neonrunner

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.iscalexsoto.neonrunner.GameConfig.Flow
import com.iscalexsoto.neonrunner.GameConfig.Layout
import com.iscalexsoto.neonrunner.GameConfig.Rogue
import com.iscalexsoto.neonrunner.GameConfig.Scoring
import com.iscalexsoto.neonrunner.GameConfig.Visual
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * Dibuja un fotograma completo a partir del estado del [GameEngine].
 * Todo es vectorial: líneas con "glow" simulado por trazos superpuestos de distinto grosor y alfa.
 */
class Renderer {

    private val path = Path()
    private val fillPath = Path()
    private val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    // Se recalculan en cada frame a partir del tamaño del canvas.
    private var w = 0f
    private var h = 0f
    private var ground = 0f
    private var lineWidth = 1f

    fun draw(scope: DrawScope, engine: GameEngine) {
        w = scope.size.width
        h = scope.size.height
        ground = h * Layout.GROUND_Y
        lineWidth = w * Visual.LINE_WIDTH

        val palette = Palettes.blend(Palettes.DAY, Palettes.NIGHT, engine.nightBlend)
        val shake = shakeOffset(engine)

        with(scope) {
            drawSky(palette)
            drawStars(palette, engine)
            drawSun(palette)
            withTransform({ translate(shake.x, shake.y) }) {
                drawMountains(
                    palette.mountainsFar, palette.mountainsFarFill, engine.distance,
                    Visual.FAR_MOUNTAIN_PARALLAX, Visual.FAR_MOUNTAIN_SEGMENT,
                    Visual.FAR_MOUNTAIN_MIN, Visual.FAR_MOUNTAIN_MAX, seed = 11,
                )
                drawMountains(
                    palette.mountainsNear, palette.mountainsNearFill, engine.distance,
                    Visual.NEAR_MOUNTAIN_PARALLAX, Visual.NEAR_MOUNTAIN_SEGMENT,
                    Visual.NEAR_MOUNTAIN_MIN, Visual.NEAR_MOUNTAIN_MAX, seed = 23,
                )
                drawFloor(palette, engine)
                drawObstacles(palette, engine)
                if (engine.mode == GameMode.ROGUE) {
                    drawSquares(palette, engine)
                    drawGems(palette, engine)
                    drawBullets(palette, engine)
                }
                drawBall(palette, engine)
            }
            drawFlash(engine)
            if (engine.phase == GameEngine.Phase.SELECT) {
                drawModeSelect(palette, engine)
            } else {
                drawHud(palette, engine)
                if (engine.mode == GameMode.ROGUE) drawXpBar(palette, engine)
                drawMessages(palette, engine)
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Convierte coordenadas de mundo (W, y hacia arriba desde el suelo) a píxeles. */
    private fun px(xW: Float) = xW * w
    private fun py(yW: Float) = ground - yW * w

    private fun shakeOffset(engine: GameEngine): Offset {
        if (engine.phase != GameEngine.Phase.DEAD || engine.deadTime > Flow.SHAKE_DURATION) return Offset.Zero
        val k = 1f - engine.deadTime / Flow.SHAKE_DURATION
        val amp = w * 0.012f * k
        return Offset(amp * sin(engine.deadTime * 95f), amp * 0.6f * sin(engine.deadTime * 71f + 1f))
    }

    /** Trazo neón: halo ancho y tenue, halo medio, línea de color y núcleo claro. */
    private fun DrawScope.glowPath(p: Path, color: Color, width: Float = lineWidth, intensity: Float = 1f) {
        val round = StrokeCap.Round
        val join = StrokeJoin.Round
        drawPath(p, color, alpha = 0.10f * intensity, style = Stroke(width * 7f, cap = round, join = join))
        drawPath(p, color, alpha = 0.25f * intensity, style = Stroke(width * 3.2f, cap = round, join = join))
        drawPath(p, color, alpha = 0.95f, style = Stroke(width, cap = round, join = join))
        drawPath(p, lerp(color, Color.White, 0.65f), alpha = 0.9f, style = Stroke(width * 0.4f, cap = round, join = join))
    }

    private fun DrawScope.glowLine(start: Offset, end: Offset, color: Color, width: Float = lineWidth, intensity: Float = 1f) {
        drawLine(color, start, end, strokeWidth = width * 7f, cap = StrokeCap.Round, alpha = 0.10f * intensity)
        drawLine(color, start, end, strokeWidth = width * 3.2f, cap = StrokeCap.Round, alpha = 0.25f * intensity)
        drawLine(color, start, end, strokeWidth = width, cap = StrokeCap.Round, alpha = 0.95f)
        drawLine(lerp(color, Color.White, 0.65f), start, end, strokeWidth = width * 0.4f, cap = StrokeCap.Round, alpha = 0.9f)
    }

    /** Color del cielo a una altura dada (para que las franjas del sol coincidan con el fondo). */
    private fun skyColorAt(palette: Palette, y: Float): Color =
        lerp(palette.skyTop, palette.skyBottom, (y / ground).coerceIn(0f, 1f))

    // ------------------------------------------------------------------ layers

    private fun DrawScope.drawSky(palette: Palette) {
        drawRect(
            Brush.verticalGradient(0f to palette.skyTop, 1f to palette.skyBottom, startY = 0f, endY = ground),
            topLeft = Offset.Zero,
            size = Size(w, ground + 1f),
        )
    }

    private fun DrawScope.drawStars(palette: Palette, engine: GameEngine) {
        val scroll = engine.distance * Visual.STAR_PARALLAX
        for (i in 0 until Visual.STAR_COUNT) {
            var x = hash(i, 1) - scroll
            x -= floor(x)                               // repetir horizontalmente
            val y = 0.02f + hash(i, 2) * 0.58f           // fracción de la altura del cielo
            val twinkle = 0.55f + 0.45f * sin(engine.time * (1.5f + hash(i, 3) * 2.5f) + hash(i, 4) * 6.28f)
            val r = w * (0.0025f + hash(i, 5) * 0.003f)
            drawCircle(palette.stars, radius = r * 2.2f, center = Offset(x * w, y * ground), alpha = 0.18f * twinkle)
            drawCircle(palette.stars, radius = r, center = Offset(x * w, y * ground), alpha = twinkle)
        }
    }

    private fun DrawScope.drawSun(palette: Palette) {
        val r = w * Visual.SUN_RADIUS
        val cx = w * Visual.SUN_X
        val cy = ground - w * Visual.SUN_ELEVATION
        val center = Offset(cx, cy)
        // Halo
        drawCircle(palette.sunBottom, radius = r * 1.6f, center = center, alpha = 0.10f)
        drawCircle(palette.sunBottom, radius = r * 1.3f, center = center, alpha = 0.14f)
        drawCircle(palette.sunTop, radius = r * 1.1f, center = center, alpha = 0.22f)
        // Cuerpo con degradado vertical
        drawCircle(
            Brush.verticalGradient(0f to palette.sunTop, 1f to palette.sunBottom, startY = cy - r, endY = cy + r),
            radius = r,
            center = center,
        )
        // Franjas horizontales (recortadas al círculo), más gruesas hacia abajo
        path.reset()
        path.addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r))
        clipPath(path) {
            var y = cy + r * 0.05f
            for (k in 0 until Visual.SUN_STRIPES) {
                val thickness = r * (0.025f + 0.028f * k)
                drawRect(skyColorAt(palette, y), topLeft = Offset(cx - r, y), size = Size(2f * r, thickness))
                y += thickness + r * (0.14f - 0.012f * k)
            }
        }
    }

    /**
     * Cordillera infinita: la altura de cada pico sale de un hash de su índice, así la capa
     * puede desplazarse para siempre sin repetirse de forma evidente.
     */
    private fun DrawScope.drawMountains(
        stroke: Color, fill: Color, distance: Float,
        parallax: Float, segment: Float, minH: Float, maxH: Float, seed: Int,
    ) {
        val offset = distance * parallax
        val first = floor(offset / segment).toInt() - 1
        val count = ceil(1f / segment).toInt() + 3
        path.reset()
        fillPath.reset()
        for (n in 0..count) {
            val i = first + n
            val xW = i * segment - offset
            val hW = minH + hash(i, seed) * (maxH - minH)
            val x = px(xW)
            val y = py(hW)
            if (n == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, ground + 2f)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (n == count) {
                fillPath.lineTo(x, ground + 2f)
                fillPath.close()
            }
        }
        drawPath(fillPath, fill)
        glowPath(path, stroke, width = lineWidth * 0.8f)
    }

    private fun DrawScope.drawFloor(palette: Palette, engine: GameEngine) {
        val floorH = h - ground
        drawRect(
            Brush.verticalGradient(0f to palette.floorTop, 1f to palette.floorBottom, startY = ground, endY = h),
            topLeft = Offset(0f, ground),
            size = Size(w, floorH),
        )

        val vp = Offset(w / 2f, ground)
        val spacing = Visual.GRID_SPACING
        val scroll = engine.distance * Visual.GRID_PARALLAX
        val shift = scroll - floor(scroll / spacing) * spacing   // desplazamiento dentro de un periodo
        val fade = Brush.verticalGradient(
            0f to Color.Transparent, 0.35f to palette.grid.copy(alpha = 0.55f), 1f to palette.grid.copy(alpha = 0.8f),
            startY = ground, endY = h,
        )
        // Líneas "verticales": convergen al punto de fuga y se desplazan a la velocidad del juego.
        val range = ceil(0.5f * Visual.GRID_SPREAD / spacing).toInt() + 2
        for (k in -range..range) {
            val dx = (k * spacing - shift) * Visual.GRID_SPREAD
            val xBottom = vp.x + dx * w
            drawLine(fade, vp, Offset(xBottom, h), strokeWidth = lineWidth * 0.55f)
        }
        // Líneas horizontales: filas en perspectiva, con un leve avance hacia el jugador.
        val drift = engine.time * Visual.GRID_ROW_DRIFT
        val frac = drift - floor(drift)
        for (j in 1..Visual.GRID_ROWS + 1) {
            val k = j - frac
            val y = ground + floorH / (1f + k * 0.55f)
            val near = ((y - ground) / floorH).coerceIn(0f, 1f)   // 1 = borde inferior
            val alpha = (0.15f + 0.65f * near) * (1f - near * 0.6f)
            drawLine(palette.grid, Offset(0f, y), Offset(w, y), strokeWidth = lineWidth * (0.3f + 0.4f * near), alpha = alpha)
        }
        // Línea del horizonte: la superficie por la que corre la pelota.
        glowLine(Offset(0f, ground), Offset(w, ground), palette.horizon, width = lineWidth * 1.1f, intensity = 1.4f)
    }

    private fun DrawScope.drawObstacles(palette: Palette, engine: GameEngine) {
        for (o in engine.obstacles) {
            val cxW = o.x - engine.distance
            if (cxW + o.base < -0.1f || cxW - o.base > 1.1f) continue
            val left = px(cxW - o.base / 2f)
            val right = px(cxW + o.base / 2f)
            val apex = py(o.height)
            path.reset()
            path.moveTo(left, ground)
            path.lineTo(px(cxW), apex)
            path.lineTo(right, ground)
            path.close()
            // Relleno oscuro y borde de sombra: el triángulo se recorta contra el cielo claro.
            drawPath(path, palette.enemyFill)
            drawPath(
                path,
                Brush.verticalGradient(0f to palette.enemy.copy(alpha = 0.05f), 1f to palette.enemy.copy(alpha = 0.35f), startY = apex, endY = ground),
            )
            drawPath(path, palette.shadow, style = Stroke(lineWidth * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            glowPath(path, palette.enemy, intensity = 1.2f)
        }
    }

    // ------------------------------------------------------------------ rogue-lite

    private fun DrawScope.drawSquares(palette: Palette, engine: GameEngine) {
        for (s in engine.squares) {
            val cxW = s.x - engine.distance
            if (cxW + s.size < -0.1f || cxW - s.size > 1.1f) continue
            val left = px(cxW - s.size / 2f)
            val right = px(cxW + s.size / 2f)
            val top = py(s.size)
            val sizePx = right - left
            // Relleno oscuro y "vida" como nivel de líquido que baja con cada impacto
            drawRect(palette.squareFill, topLeft = Offset(left, top), size = Size(sizePx, sizePx))
            val hpH = sizePx * s.hpFraction
            drawRect(palette.square, topLeft = Offset(left, ground - hpH), size = Size(sizePx, hpH), alpha = 0.28f)
            path.reset()
            path.moveTo(left, ground)
            path.lineTo(left, top)
            path.lineTo(right, top)
            path.lineTo(right, ground)
            path.close()
            drawPath(path, palette.shadow, style = Stroke(lineWidth * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            glowPath(path, palette.square, intensity = 1.1f)
        }
    }

    private fun DrawScope.drawBullets(palette: Palette, engine: GameEngine) {
        val len = w * 0.03f
        val core = lerp(palette.ball, Color.White, 0.6f)
        for (b in engine.bullets) {
            val x = px(b.x)
            val y = py(b.y)
            val start = Offset(x - len, y)
            val end = Offset(x, y)
            drawLine(palette.ballGlow, start, end, strokeWidth = lineWidth * 4f, cap = StrokeCap.Round, alpha = 0.25f)
            drawLine(core, start, end, strokeWidth = lineWidth * 1.2f, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.drawGems(palette: Palette, engine: GameEngine) {
        val r = w * Rogue.GEM_RADIUS
        for (g in engine.gems) {
            val xW = g.x - engine.distance
            if (xW < -0.05f || xW > 1.05f) continue
            val bob = if (g.landed) r * 0.25f * sin(engine.time * 8f + g.x * 40f) else 0f
            val cx = px(xW)
            val cy = py(g.y) - bob
            // Parpadea en su último segundo de vida
            val remaining = Rogue.GEM_LIFETIME - g.age
            if (remaining < 1f && (remaining * 10f).toInt() % 2 == 0) continue
            drawCircle(palette.gem, radius = r * 2.4f, center = Offset(cx, cy), alpha = 0.18f)
            path.reset()
            path.moveTo(cx, cy - r * 1.3f)
            path.lineTo(cx + r, cy)
            path.lineTo(cx, cy + r * 1.3f)
            path.lineTo(cx - r, cy)
            path.close()
            drawPath(path, palette.gem, alpha = 0.9f)
            drawPath(path, Color.White, alpha = 0.6f, style = Stroke(lineWidth * 0.5f, join = StrokeJoin.Round))
        }
    }

    private fun DrawScope.drawXpBar(palette: Palette, engine: GameEngine) {
        val margin = w * 0.05f
        val barH = w * 0.012f
        val y = h - h * 0.035f
        val fullW = w - 2f * margin
        val frac = (engine.xp.toFloat() / engine.xpToNext).coerceIn(0f, 1f)
        drawRect(palette.shadow, topLeft = Offset(margin, y - barH), size = Size(fullW, barH * 3f), alpha = 0.6f)
        drawRect(palette.square, topLeft = Offset(margin, y), size = Size(fullW, barH), alpha = 0.18f)
        if (frac > 0f) {
            drawRect(palette.square, topLeft = Offset(margin, y - barH * 0.6f), size = Size(fullW * frac, barH * 2.2f), alpha = 0.25f)
            drawRect(palette.square, topLeft = Offset(margin, y), size = Size(fullW * frac, barH))
        }
        val size = w * 0.038f
        neonText(
            drawContext.canvas.nativeCanvas, "NIVEL " + engine.level.toString().padStart(2, '0'),
            margin, y - barH * 2.2f, size, palette.hud, palette.square, palette.shadow, android.graphics.Paint.Align.LEFT,
        )
    }

    private fun DrawScope.drawBall(palette: Palette, engine: GameEngine) {
        val r = w * Layout.BALL_RADIUS
        val cx = px(Layout.BALL_X)
        val bottom = py(engine.ballY)

        // Squash & stretch
        val velStretch = if (engine.airborne) {
            (abs(engine.ballVy) * Visual.STRETCH_PER_VELOCITY).coerceAtMost(Visual.STRETCH_MAX)
        } else 0f
        val idle = if (engine.phase == GameEngine.Phase.SELECT) 0.03f * sin(engine.time * 3f) else 0f
        val squash = engine.squash + idle
        val sy = (1f + velStretch) * (1f + squash)
        val sx = (1f - velStretch * 0.5f) * (1f - squash * 0.6f)

        // Sombra en el suelo, se desvanece con la altura
        val shadowK = (1f - engine.ballY / 0.3f).coerceIn(0f, 1f)
        drawOval(
            palette.ball,
            topLeft = Offset(cx - r * (1.1f + 0.4f * (1f - shadowK)), ground - r * 0.12f),
            size = Size(r * 2f * (1.1f + 0.4f * (1f - shadowK)), r * 0.24f),
            alpha = 0.25f * shadowK,
        )

        withTransform({
            translate(cx, bottom)
            scale(sx, sy, pivot = Offset.Zero)
        }) {
            val c = Offset(0f, -r)
            // Halo (color e intensidad vienen de la paleta)
            val g = palette.ballGlowStrength
            drawCircle(palette.ballGlow, radius = r * 2.2f, center = c, alpha = (0.08f * g).coerceAtMost(1f))
            drawCircle(palette.ballGlow, radius = r * 1.6f, center = c, alpha = (0.14f * g).coerceAtMost(1f))
            drawCircle(palette.ballGlow, radius = r * 1.25f, center = c, alpha = (0.25f * g).coerceAtMost(1f))
            // Cuerpo: núcleo claro que se funde al color neón en el borde
            drawCircle(
                Brush.radialGradient(
                    0f to palette.ballCore, 0.45f to lerp(palette.ballCore, palette.ball, 0.5f), 1f to palette.ball,
                    center = Offset(-r * 0.15f, -r * 1.15f),
                    radius = r * 1.1f,
                ),
                radius = r,
                center = c,
            )
            // Borde brillante
            drawCircle(lerp(palette.ball, Color.White, 0.5f), radius = r, center = c, style = Stroke(lineWidth * 0.6f), alpha = 0.9f)
        }
    }

    private fun DrawScope.drawFlash(engine: GameEngine) {
        if (engine.phase != GameEngine.Phase.DEAD || engine.deadTime > Flow.FLASH_DURATION) return
        val a = 0.55f * (1f - engine.deadTime / Flow.FLASH_DURATION)
        drawRect(Color.White, alpha = a)
    }

    // ------------------------------------------------------------------ texto

    /**
     * Texto neón legible sobre cualquier fondo: primero una copia oscura con sombra difusa
     * (recorta el texto contra el cielo claro) y encima el texto brillante con su glow.
     * Si [maxWidth] > 0 y el texto no cabe, se reduce el tamaño de letra.
     */
    private fun neonText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Color,
        glow: Color,
        shadow: Color,
        align: android.graphics.Paint.Align,
        alpha: Int = 255,
        maxWidth: Float = 0f,
    ) {
        textPaint.textAlign = align
        textPaint.textSize = size
        if (maxWidth > 0f) {
            val measured = textPaint.measureText(text)
            if (measured > maxWidth) textPaint.textSize = size * maxWidth / measured
        }
        val fontSize = textPaint.textSize
        // Pasada 1: sombra oscura
        textPaint.color = shadow.toArgb()
        textPaint.alpha = (shadow.alpha * alpha).toInt()
        textPaint.setShadowLayer(fontSize * 0.45f, 0f, fontSize * 0.04f, shadow.toArgb())
        canvas.drawText(text, x, y, textPaint)
        // Pasada 2: texto brillante con glow
        textPaint.color = color.toArgb()
        textPaint.alpha = alpha
        textPaint.setShadowLayer(fontSize * 0.3f, 0f, 0f, glow.toArgb())
        canvas.drawText(text, x, y, textPaint)
        textPaint.alpha = 255
    }

    private fun DrawScope.drawHud(palette: Palette, engine: GameEngine) {
        val size = w * 0.046f
        val margin = w * 0.05f
        val top = h * 0.055f + size
        val canvas = drawContext.canvas.nativeCanvas
        val right = android.graphics.Paint.Align.RIGHT
        val scoreText = engine.score.toString().padStart(5, '0')
        val hiText = "HI " + engine.highScore.toString().padStart(5, '0')

        // Parpadeo del marcador al cruzar cada 100 puntos (estilo Chrome).
        val flashing = engine.hudFlashTime >= 0f
        val visible = !flashing || ((engine.hudFlashTime / Scoring.HUD_BLINK_INTERVAL).toInt() % 2 == 0)
        if (visible) neonText(canvas, scoreText, w - margin, top, size, palette.hud, palette.hudGlow, palette.shadow, right)

        textPaint.textSize = size
        val scoreWidth = textPaint.measureText("00000")
        neonText(canvas, hiText, w - margin - scoreWidth - size * 1.2f, top, size, palette.hud, palette.hudGlow, palette.shadow, right, alpha = 190)

        if (engine.mode == GameMode.ROGUE) {
            val left = android.graphics.Paint.Align.LEFT
            neonText(canvas, "LV " + engine.level.toString().padStart(2, '0'), margin, top, size, palette.hud, palette.square, palette.shadow, left, alpha = 190)
        }
    }

    /** Pantalla inicial: dos paneles, uno por modo. El toque elige según su altura (Flow.MODE_SPLIT_Y). */
    private fun DrawScope.drawModeSelect(palette: Palette, engine: GameEngine) {
        val canvas = drawContext.canvas.nativeCanvas
        val center = android.graphics.Paint.Align.CENTER
        val cx = w / 2f
        val maxWidth = w * Visual.MESSAGE_MAX_WIDTH
        val pulse = 0.5f + 0.5f * sin(engine.time * 3f)

        neonText(canvas, "NEON RUNNER", cx, h * 0.085f, w * 0.09f, palette.hud, palette.enemy, palette.shadow, center, maxWidth = maxWidth)
        neonText(canvas, "ELIGE UN MODO", cx, h * 0.085f + w * 0.07f, w * 0.036f, palette.hud, palette.hudGlow, palette.shadow, center, alpha = 200)

        val split = h * Flow.MODE_SPLIT_Y
        val panelH = w * 0.30f
        val gap = w * 0.05f
        drawModePanel(
            palette, top = split - gap / 2f - panelH, height = panelH, accent = palette.ball, pulse = pulse,
            title = "RUNNER", line1 = "Salta los triángulos.", line2 = "Solo tú y tu pulgar.",
            hi = engine.highScoreFor(GameMode.RUNNER),
        )
        drawModePanel(
            palette, top = split + gap / 2f, height = panelH, accent = palette.square, pulse = pulse,
            title = "ROGUE-LITE", line1 = "Destruye cuadrados, sube de nivel.", line2 = "Disparas solo; tu trabajo es no chocar.",
            hi = engine.highScoreFor(GameMode.ROGUE),
        )
    }

    private fun DrawScope.drawModePanel(
        palette: Palette, top: Float, height: Float, accent: Color, pulse: Float,
        title: String, line1: String, line2: String, hi: Int,
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        val center = android.graphics.Paint.Align.CENTER
        val margin = w * 0.08f
        val left = margin
        val right = w - margin
        val cx = w / 2f
        val corner = w * 0.03f
        val rect = androidx.compose.ui.geometry.Rect(left, top, right, top + height)
        path.reset()
        path.addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, corner, corner))
        drawPath(path, palette.shadow, alpha = 0.8f)
        glowPath(path, accent, width = lineWidth * 0.9f, intensity = 0.8f + 0.5f * pulse)

        val innerWidth = (right - left) - w * 0.08f
        neonText(canvas, title, cx, top + height * 0.36f, w * 0.075f, palette.hud, accent, palette.shadow, center, maxWidth = innerWidth)
        neonText(canvas, line1, cx, top + height * 0.58f, w * 0.034f, palette.hud, accent, palette.shadow, center, alpha = 210, maxWidth = innerWidth)
        neonText(canvas, line2, cx, top + height * 0.72f, w * 0.034f, palette.hud, accent, palette.shadow, center, alpha = 210, maxWidth = innerWidth)
        neonText(canvas, "HI " + hi.toString().padStart(5, '0'), cx, top + height * 0.90f, w * 0.034f, palette.hud, palette.hudGlow, palette.shadow, center, alpha = 170)
    }

    private fun DrawScope.drawMessages(palette: Palette, engine: GameEngine) {
        if (engine.phase == GameEngine.Phase.PLAYING && engine.levelMessageTime < 0f) return
        val canvas = drawContext.canvas.nativeCanvas
        val center = android.graphics.Paint.Align.CENTER
        val cx = w / 2f
        val maxWidth = w * Visual.MESSAGE_MAX_WIDTH
        // El bloque va en la zona de estrellas, por encima del sol y su resplandor,
        // pero nunca encimado con el marcador en pantallas cortas.
        val hudBottom = h * 0.055f + w * 0.046f
        val cy = maxOf(ground - w * Visual.MESSAGE_TITLE_ELEVATION, hudBottom + w * 0.2f)

        fun title(text: String, y: Float, glow: Color) =
            neonText(canvas, text, cx, y, w * 0.11f, palette.hud, glow, palette.shadow, center, maxWidth = maxWidth)

        fun subtitle(text: String, y: Float, alpha: Int = 230) =
            neonText(canvas, text, cx, y, w * 0.042f, palette.hud, palette.hudGlow, palette.shadow, center, alpha, maxWidth)

        val pulse = (160 + 95 * (0.5f + 0.5f * sin(engine.time * 4f))).toInt()
        when (engine.phase) {
            GameEngine.Phase.PLAYING -> {
                // Aviso de subida de nivel (modo Rogue): aparece y se desvanece.
                val t = engine.levelMessageTime / Rogue.LEVEL_MESSAGE_DURATION
                val fade = (1f - t).coerceIn(0f, 1f) * 255
                val a = minOf(255f, fade * 2f).toInt()
                neonText(canvas, "NIVEL ${engine.level}", cx, cy, w * 0.08f, palette.hud, palette.square, palette.shadow, center, alpha = a)
                subtitle(engine.levelMessage, cy + w * 0.09f, a)
            }
            GameEngine.Phase.SELECT -> Unit
            GameEngine.Phase.DEAD -> {
                title("GAME OVER", cy, palette.enemy)
                var y = cy + w * 0.11f
                if (engine.mode == GameMode.ROGUE) {
                    subtitle("NIVEL ${engine.level} · ${engine.squaresDestroyed} CUADRADOS", y, 200)
                    y += w * 0.07f
                }
                if (engine.isNewHighScore) {
                    subtitle("¡NUEVO RÉCORD!", y, pulse)
                    y += w * 0.07f
                }
                if (engine.canRestart) {
                    subtitle("TOCA PARA REINICIAR", y + w * 0.02f, pulse)
                    subtitle("MANTÉN PARA CAMBIAR DE MODO", y + w * 0.09f, 150)
                    // Progreso de la pulsación larga
                    val p = engine.modeSwitchProgress
                    if (p > 0f) {
                        val barW = w * 0.4f
                        drawRect(palette.hud, topLeft = Offset(cx - barW / 2f, y + w * 0.12f), size = Size(barW * p, w * 0.008f), alpha = 0.8f)
                    }
                }
            }
            GameEngine.Phase.PAUSED -> {
                title("PAUSA", cy, palette.ball)
                subtitle("TOCA PARA CONTINUAR", cy + w * 0.11f, pulse)
            }
        }
    }

    private companion object {
        /** Pseudoaleatorio determinista en [0, 1) a partir de un índice y una semilla. */
        fun hash(i: Int, seed: Int): Float {
            var x = i * 374761393 + seed * 668265263
            x = (x xor (x ushr 13)) * 1274126177
            x = x xor (x ushr 16)
            return (x and 0x7FFFFFFF) / 2147483647f
        }
    }
}
