package com.pocketarcade

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.pocketarcade.storage.PrefsManager
import java.util.concurrent.Executors
import kotlin.math.*

object SoundManager {

    private val pool = Executors.newFixedThreadPool(4)
    private const val RATE = 22050

    enum class SFX {
        PONG_BOUNCE, PONG_SCORE, PONG_WIN, PONG_LOSE,
        ASTEROID_SHOOT, ASTEROID_EXPLODE, ASTEROID_WAVE_CLEAR, ASTEROID_GAME_OVER,
        BB_BRICK, BB_PADDLE, BB_POWERUP, BB_LEVEL_CLEAR, BB_GAME_OVER,
        SNAKE_EAT, SNAKE_DIE
    }

    fun play(sfx: SFX, ctx: Context) {
        if (!PrefsManager.isSoundEnabled(ctx)) return
        pool.submit { runCatching { emit(build(sfx)) } }
    }

    private fun build(sfx: SFX): ShortArray = when (sfx) {
        SFX.PONG_BOUNCE         -> square(440f, 45)
        SFX.PONG_SCORE          -> melody(listOf(659f to 80, 784f to 80, 1047f to 120))
        SFX.PONG_WIN            -> melody(listOf(523f to 100, 659f to 100, 784f to 100, 1047f to 250))
        SFX.PONG_LOSE           -> sweep(440f, 150f, 600)
        SFX.ASTEROID_SHOOT      -> square(1200f, 50, vol = 0.5f, decay = 0.3f)
        SFX.ASTEROID_EXPLODE    -> noise(350, 0.65f)
        SFX.ASTEROID_WAVE_CLEAR -> melody(listOf(440f to 80, 554f to 80, 659f to 80, 880f to 180))
        SFX.ASTEROID_GAME_OVER  -> sweep(500f, 80f, 800)
        SFX.BB_BRICK            -> square(580f, 55, vol = 0.6f, decay = 0.5f)
        SFX.BB_PADDLE           -> square(310f, 70, vol = 0.65f, decay = 0.6f)
        SFX.BB_POWERUP          -> melody(listOf(880f to 75, 1047f to 75, 1319f to 110))
        SFX.BB_LEVEL_CLEAR      -> melody(listOf(523f to 90, 659f to 90, 784f to 75, 1047f to 90, 1319f to 200))
        SFX.BB_GAME_OVER        -> sweep(450f, 100f, 700)
        SFX.SNAKE_EAT           -> sine(660f, 100, vol = 0.35f)
        SFX.SNAKE_DIE           -> sweep(480f, 90f, 500)
    }

    private fun sine(
        freq: Float, ms: Int,
        vol: Float = 0.5f
    ): ShortArray {
        val n = RATE * ms / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val env = (1.0 - i.toDouble() / n).pow(0.6)
            (sin(2.0 * PI * freq * t) * env * vol * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun square(
        freq: Float, ms: Int,
        vol: Float = 0.7f, decay: Float = 1.0f
    ): ShortArray {
        val n = RATE * ms / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val raw = if (sin(2.0 * PI * freq * t) >= 0) 1.0 else -1.0
            val env = (1.0 - i.toDouble() / n).pow(decay.toDouble())
            (raw * env * vol * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun sweep(
        freqStart: Float, freqEnd: Float, ms: Int,
        vol: Float = 0.65f
    ): ShortArray {
        val n = RATE * ms / 1000
        var phase = 0.0
        return ShortArray(n) { i ->
            val progress = i.toDouble() / n
            val freq = (freqStart + (freqEnd - freqStart) * progress).toDouble()
            phase += 2.0 * PI * freq / RATE
            val raw = if (sin(phase) >= 0) 1.0 else -1.0
            val env = (1.0 - progress).pow(0.5)
            (raw * env * vol * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun noise(ms: Int, vol: Float = 0.5f): ShortArray {
        val n = RATE * ms / 1000
        val rng = java.util.Random()
        return ShortArray(n) { i ->
            val env = (1.0 - i.toDouble() / n).pow(1.8)
            (rng.nextGaussian().coerceIn(-1.0, 1.0) * env * vol * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun melody(notes: List<Pair<Float, Int>>): ShortArray =
        notes.map { (f, ms) -> square(f, ms, vol = 0.6f, decay = 1.5f) }
            .reduce { acc, a -> acc + a }

    private operator fun ShortArray.plus(other: ShortArray): ShortArray {
        val out = ShortArray(size + other.size)
        copyInto(out); other.copyInto(out, size)
        return out
    }

    private fun emit(samples: ShortArray) {
        if (samples.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = maxOf(samples.size * 2, minBuf)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        Thread.sleep(samples.size * 1000L / RATE + 50L)
        track.stop()
        track.release()
    }
}
