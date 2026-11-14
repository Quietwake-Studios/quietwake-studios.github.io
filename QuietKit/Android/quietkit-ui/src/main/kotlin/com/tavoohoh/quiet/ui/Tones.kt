package com.tavoohoh.quiet.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Short tones synthesized on device, no audio files (port of QuietUI's `ToneService`): each note
 * with a soft attack and exponential decay, 60 ms apart. Game audio at the media volume; like
 * iOS's ambient category it stays quiet when the phone is on silent or vibrate.
 */
class QuietTones(context: Context) {
    enum class Voice {
        SOFT,
        /** Adds the bar's characteristic overtone (~4×) that fades faster. */
        MARIMBA,
    }

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val cache = HashMap<String, AudioTrack>()

    /** Frequencies (Hz) played 60 ms apart. */
    fun play(notes: List<Double>, voice: Voice = Voice.SOFT) {
        if (notes.isEmpty() || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val key = "$voice-$notes"
        val track = cache.getOrPut(key) { track(synthesize(notes, voice)) ?: return }
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() {
        cache.values.forEach { it.release() }
        cache.clear()
    }

    private fun track(samples: ShortArray): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also { it.write(samples, 0, samples.size) }
    }.getOrNull()

    private fun synthesize(notes: List<Double>, voice: Voice): ShortArray {
        val spacing = 0.06
        val length = 0.35
        val frames = ((notes.size - 1) * spacing * RATE + length * RATE).toInt()
        val samples = DoubleArray(frames)
        for ((n, frequency) in notes.withIndex()) {
            val start = (n * spacing * RATE).toInt()
            for (i in 0 until (length * RATE).toInt()) {
                if (start + i >= frames) break
                val t = i.toDouble() / RATE
                val attack = min(t / 0.005, 1.0)
                val value = when (voice) {
                    Voice.SOFT -> sin(2 * PI * frequency * t) * exp(-t * 14)
                    Voice.MARIMBA -> sin(2 * PI * frequency * t) * exp(-t * 10) + 0.35 * sin(2 * PI * frequency * 3.9 * t) * exp(-t * 30)
                }
                samples[start + i] += value * attack * 0.16
            }
        }
        return ShortArray(frames) { (samples[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }

    private companion object {
        const val RATE = 44_100
    }
}
