package com.iscalexsoto.neonrunner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Paleta central. Todo lo que se dibuja toma su color de aquí, y el ciclo día/noche
 * simplemente interpola entre [Palettes.NIGHT] y [Palettes.DAY].
 */
data class Palette(
    val skyTop: Color,
    val skyBottom: Color,
    val stars: Color,
    val sunTop: Color,
    val sunBottom: Color,
    val mountainsFar: Color,
    val mountainsFarFill: Color,
    val mountainsNear: Color,
    val mountainsNearFill: Color,
    val horizon: Color,
    val grid: Color,
    val floorTop: Color,
    val floorBottom: Color,
    val ball: Color,
    val ballCore: Color,
    /** Color del halo de la pelota (puede diferir del cuerpo para contrastar con el cielo). */
    val ballGlow: Color,
    /** Intensidad del halo de la pelota (1 = normal). */
    val ballGlowStrength: Float,
    val enemy: Color,
    /** Relleno oscuro de los triángulos para que no se pierdan contra el cielo. */
    val enemyFill: Color,
    val hud: Color,
    val hudGlow: Color,
    /** Sombra oscura detrás de todos los textos y del contorno de los triángulos. */
    val shadow: Color,
)

object Palettes {
    /** Noche: fondo casi negro, magenta y cian intensos. */
    val NIGHT = Palette(
        skyTop = Color(0xFF050211),
        skyBottom = Color(0xFF1C0638),
        stars = Color(0xFFE8F6FF),
        sunTop = Color(0xFFB8F3FF),
        sunBottom = Color(0xFF7A3CFF),
        mountainsFar = Color(0xFF8A3DFF),
        mountainsFarFill = Color(0xFF0B0322),
        mountainsNear = Color(0xFF00E5FF),
        mountainsNearFill = Color(0xFF07021A),
        horizon = Color(0xFFFF2BD6),
        grid = Color(0xFFB04CFF),
        floorTop = Color(0xFF16052F),
        floorBottom = Color(0xFF040110),
        ball = Color(0xFF00F0FF),
        ballCore = Color(0xFFFFFFFF),
        ballGlow = Color(0xFF00F0FF),
        ballGlowStrength = 1f,
        enemy = Color(0xFFFF2BD6),
        enemyFill = Color(0xFF12042A),
        hud = Color(0xFFE6FBFF),
        hudGlow = Color(0xFF00E5FF),
        shadow = Color(0xD9000000),
    )

    /** Día / atardecer: cielo morado-rosado más claro, neones cálidos. */
    val DAY = Palette(
        skyTop = Color(0xFF3B1D6E),
        skyBottom = Color(0xFFFF6FB5),
        stars = Color(0x26FFFFFF),
        sunTop = Color(0xFFFFF27A),
        sunBottom = Color(0xFFFF5E7E),
        mountainsFar = Color(0xFFFFA347),
        mountainsFarFill = Color(0xFF4B1F6B),
        mountainsNear = Color(0xFFFF3D8E),
        mountainsNearFill = Color(0xFF2A0F4F),
        horizon = Color(0xFFFFD166),
        grid = Color(0xFFFF7AC6),
        floorTop = Color(0xFF3A1458),
        floorBottom = Color(0xFF140626),
        // Cian saturado con halo azul más fuerte: la pelota debe seguir siendo lo más llamativo
        // aun contra el cielo rosa.
        ball = Color(0xFF00D8FF),
        ballCore = Color(0xFFEAFFFF),
        ballGlow = Color(0xFF00A6FF),
        ballGlowStrength = 1.7f,
        enemy = Color(0xFFFF1F5A),
        enemyFill = Color(0xFF2A0F4F),
        hud = Color(0xFFFFF8E7),
        hudGlow = Color(0xFFFFB347),
        shadow = Color(0xE61A0530),
    )

    /** Interpola todos los colores de la paleta. t = 0 → a, t = 1 → b. */
    fun blend(a: Palette, b: Palette, t: Float): Palette {
        val k = t.coerceIn(0f, 1f)
        return Palette(
            skyTop = lerp(a.skyTop, b.skyTop, k),
            skyBottom = lerp(a.skyBottom, b.skyBottom, k),
            stars = lerp(a.stars, b.stars, k),
            sunTop = lerp(a.sunTop, b.sunTop, k),
            sunBottom = lerp(a.sunBottom, b.sunBottom, k),
            mountainsFar = lerp(a.mountainsFar, b.mountainsFar, k),
            mountainsFarFill = lerp(a.mountainsFarFill, b.mountainsFarFill, k),
            mountainsNear = lerp(a.mountainsNear, b.mountainsNear, k),
            mountainsNearFill = lerp(a.mountainsNearFill, b.mountainsNearFill, k),
            horizon = lerp(a.horizon, b.horizon, k),
            grid = lerp(a.grid, b.grid, k),
            floorTop = lerp(a.floorTop, b.floorTop, k),
            floorBottom = lerp(a.floorBottom, b.floorBottom, k),
            ball = lerp(a.ball, b.ball, k),
            ballCore = lerp(a.ballCore, b.ballCore, k),
            ballGlow = lerp(a.ballGlow, b.ballGlow, k),
            ballGlowStrength = a.ballGlowStrength + (b.ballGlowStrength - a.ballGlowStrength) * k,
            enemy = lerp(a.enemy, b.enemy, k),
            enemyFill = lerp(a.enemyFill, b.enemyFill, k),
            hud = lerp(a.hud, b.hud, k),
            hudGlow = lerp(a.hudGlow, b.hudGlow, k),
            shadow = lerp(a.shadow, b.shadow, k),
        )
    }
}
