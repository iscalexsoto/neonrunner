package com.iscalexsoto.neonrunner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Pantalla única del juego: crea el motor, corre el game loop con withFrameNanos,
 * traduce los toques a presionar/soltar y dibuja cada frame en un Canvas.
 */
@Composable
fun GameScreen() {
    val context = LocalContext.current
    val store = remember { HighScoreStore(context) }
    val sound = remember { SoundManager(context) }
    val haptics = remember { Haptics(context) }
    val renderer = remember { Renderer() }

    val engine = remember {
        GameEngine(
            initialHighScore = store.load(),
            events = object : GameEvents {
                override fun onJump() = sound.playJump()
                override fun onCrash() {
                    sound.playCrash()
                    haptics.crash()
                }
                override fun onDing() = sound.playDing()
                override fun onHighScore(score: Int) = store.save(score)
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { sound.release() }
    }

    // Pausa automática al irse a segundo plano.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) engine.onAppPaused()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Game loop: un paso de simulación por frame de pantalla.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 1f / 20f)
                last = now
                engine.update(dt)
                frame = now
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var wasPressed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.any { it.pressed }
                        if (pressed && !wasPressed) engine.onPress()
                        if (!pressed && wasPressed) engine.onRelease()
                        wasPressed = pressed
                    }
                }
            },
    ) {
        // Leer el estado `frame` hace que el Canvas se redibuje en cada frame.
        if (frame < 0L) return@Canvas
        renderer.draw(this, engine)
    }
}
