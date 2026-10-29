package com.iscalexsoto.neonrunner

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Efectos de sonido estilo arcade generados por código (sin archivos de audio en el proyecto).
 * Al arrancar se sintetizan tres clips, se escriben como WAV en el caché y se cargan en un SoundPool.
 */
class SoundManager(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loaded = HashSet<Int>()
    private val jumpId: Int
    private val crashId: Int
    private val dingId: Int

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded.add(sampleId) }
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        jumpId = pool.load(writeWav(File(dir, "jump.wav"), synthJump()), 1)
        crashId = pool.load(writeWav(File(dir, "crash.wav"), synthCrash()), 1)
        dingId = pool.load(writeWav(File(dir, "ding.wav"), synthDing()), 1)
    }

    fun playJump() = play(jumpId)
    fun playCrash() = play(crashId)
    fun playDing() = play(dingId)

    fun release() = pool.release()

    private fun play(id: Int) {
        if (id in loaded) pool.play(id, GameConfig.Audio.VOLUME, GameConfig.Audio.VOLUME, 1, 0, 1f)
    }

    // ------------------------------------------------------------ síntesis

    private val rate = GameConfig.Audio.SAMPLE_RATE

    /** Salto: barrido ascendente con onda cuadrada suave, muy corto. */
    private fun synthJump(): FloatArray {
        val n = (rate * 0.13f).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val freq = 320.0 + 520.0 * t.toDouble().pow(0.7)
            phase += 2.0 * PI * freq / rate
            val square = if (sin(phase) >= 0) 1.0 else -1.0
            val tri = sin(phase)
            val env = (1f - t).pow(1.4f) * minOf(1f, t * 40f)
            out[i] = ((square * 0.35 + tri * 0.65) * env * 0.6).toFloat()
        }
        return out
    }

    /** Choque: ruido con caída de tono grave, estilo explosión 8-bit. */
    private fun synthCrash(): FloatArray {
        val n = (rate * 0.38f).toInt()
        val out = FloatArray(n)
        val rnd = Random(7)
        var phase = 0.0
        var noise = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val freq = 200.0 - 150.0 * t
            phase += 2.0 * PI * freq / rate
            val square = if (sin(phase) >= 0) 1.0 else -1.0
            // Ruido "crujiente": cambia de valor solo cada pocas muestras.
            if (i % 3 == 0) noise = rnd.nextDouble(-1.0, 1.0)
            val env = exp(-4.5 * t)
            out[i] = ((square * 0.4 + noise * 0.6) * env * 0.8).toFloat()
        }
        return out
    }

    /** Ding: dos notas de campana (C6 → G6) con cola corta. */
    private fun synthDing(): FloatArray {
        val n = (rate * 0.22f).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / rate
            val progress = i.toFloat() / n
            val freq = if (progress < 0.45f) 1046.5 else 1568.0
            val local = if (progress < 0.45f) progress / 0.45f else (progress - 0.45f) / 0.55f
            val env = exp(-3.0 * local) * minOf(1.0, local * 30.0)
            val tone = sin(2.0 * PI * freq * t) + 0.3 * sin(2.0 * PI * freq * 2 * t)
            out[i] = (tone * env * 0.45).toFloat()
        }
        return out
    }

    /** Escribe un WAV PCM 16 bits mono y devuelve su ruta. */
    private fun writeWav(file: File, samples: FloatArray): String {
        val dataSize = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)              // tamaño del bloque fmt
        buf.putShort(1)             // PCM
        buf.putShort(1)             // mono
        buf.putInt(rate)
        buf.putInt(rate * 2)        // bytes por segundo
        buf.putShort(2)             // bytes por bloque
        buf.putShort(16)            // bits por muestra
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in samples) {
            buf.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        file.writeBytes(buf.array())
        return file.absolutePath
    }
}
