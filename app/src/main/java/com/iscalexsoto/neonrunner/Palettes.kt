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
    val enemy: Color,
    val hud: Color,
    val hudGlow: Color,
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
        enemy = Color(0xFFFF2BD6),
        hud = Color(0xFFE6FBFF),
        hudGlow = Color(0xFF00E5FF),
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
        ball = Color(0xFF9BFFEA),
        ballCore = Color(0xFFFFFFFF),
        enemy = Color(0xFFFF2E63),
        hud = Color(0xFFFFF8E7),
        hudGlow = Color(0xFFFFB347),
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
            enemy = lerp(a.enemy, b.enemy, k),
            hud = lerp(a.hud, b.hud, k),
            hudGlow = lerp(a.hudGlow, b.hudGlow, k),
        )
    }
}
