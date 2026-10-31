package com.deadline.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.math.pow
import kotlin.math.sin

enum class AmbientSoundTrack(val displayName: String, val emoji: String, val subtitle: String) {
    NONE("None / Mute", "🔇", "Silent Focus & zero distraction"),
    ALPHA_FOCUS("10Hz Alpha Focus", "🧠", "Binaural alpha waves for deep cognitive flow"),
    PILLOW_RAIN("A Pillow for the Rain", "🌧️", "Calming rain & droplet resonance"),
    FOREST_CANOPY("Forest Canopy", "🌲", "Chirping birds & gentle rustling leaves"),
    SUMMER_LEAVES("Beneath the Summer Leaves", "🍃", "Warm canopy breeze & rustle"),
    CLEAR_ALIGNMENT("Clear Alignment", "✨", "432Hz harmonic alignment tone"),
    SEVEN_MILES("Seven Miles from Land", "🌊", "Deep rhythmic slow ocean waves"),
    UNFINISHED_MANUSCRIPT("The Unfinished Manuscript", "🕯️", "Warm ambient hearth & soft crackle"),
    THREE_AM_SCRIBBLES("Three AM Scribbles", "🌙", "Late night low-frequency atmosphere"),
    RAINFOREST("Rainforest", "🌿", "Lush tropical canopy & gentle drops")
}

class AmbientSoundPlayer {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var currentTrack = AmbientSoundTrack.NONE
    private var currentVolume = 0.5f
    private var audioThread: Thread? = null

    fun getActiveTrack(): AmbientSoundTrack = currentTrack
    fun isAudioPlaying(): Boolean = isPlaying
    fun getVolume(): Float = currentVolume

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            audioTrack?.setVolume(currentVolume)
        } catch (_: Exception) {}
    }

    fun playTrack(track: AmbientSoundTrack) = play(track)

    fun play(track: AmbientSoundTrack) {
        if (track == AmbientSoundTrack.NONE) {
            stop()
            return
        }
        stop()

        currentTrack = track
        isPlaying = true

        val sampleRate = 44100
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer > 0) minBuffer * 2 else 4096

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.setVolume(currentVolume)
            audioTrack?.play()
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
            currentTrack = AmbientSoundTrack.NONE
            return
        }

        audioThread = Thread {
            val random = Random()
            val buffer = ShortArray(bufferSize / 2)
            var phase1 = 0.0
            var phase2 = 0.0
            var lastSample = 0.0

            // Bird chirp synthesis state for FOREST_CANOPY
            var birdCountdown = random.nextInt(sampleRate * 2)
            var birdChirpLength = 0
            var birdChirpProgress = 0
            var birdFreq = 3000.0
            var birdPhase = 0.0
            var birdSweep = 0.0

            while (isPlaying) {
                for (i in buffer.indices) {
                    val sample: Double = when (currentTrack) {
                        AmbientSoundTrack.ALPHA_FOCUS -> {
                            // 200 Hz carrier + 10 Hz alpha wave binaural modulation
                            phase1 += 2.0 * Math.PI * 200.0 / sampleRate
                            phase2 += 2.0 * Math.PI * 10.0 / sampleRate
                            val carrier = sin(phase1)
                            val mod = (sin(phase2) + 1.0) * 0.5
                            carrier * mod * 0.35
                        }
                        AmbientSoundTrack.PILLOW_RAIN -> {
                            // Soft rain: random droplets with resonance decay
                            if (random.nextInt(100) < 4) {
                                (random.nextDouble() * 0.7 - 0.35)
                            } else {
                                lastSample * 0.88 + (random.nextDouble() * 0.08 - 0.04)
                            }
                        }
                        AmbientSoundTrack.FOREST_CANOPY -> {
                            // 1. Woodland gentle breeze & rustling leaves
                            phase1 += 2.0 * Math.PI * 0.16 / sampleRate
                            val breeze = (sin(phase1) + 1.25) * 0.35
                            val white = (random.nextDouble() * 2.0 - 1.0)
                            lastSample = (lastSample * 0.93) + (white * 0.07)
                            val leaves = lastSample * breeze * 0.45

                            // 2. Intermittent chirping birds (frequency-modulated sweeps with smooth envelope)
                            var birdSample = 0.0
                            if (birdCountdown > 0) {
                                birdCountdown--
                            } else if (birdChirpLength > 0) {
                                birdChirpProgress++
                                val progressRatio = birdChirpProgress.toDouble() / birdChirpLength
                                val envelope = sin(progressRatio * Math.PI)
                                val currentFreq = birdFreq + (progressRatio * birdSweep)
                                birdPhase += 2.0 * Math.PI * currentFreq / sampleRate
                                birdSample = sin(birdPhase) * envelope * 0.18

                                if (birdChirpProgress >= birdChirpLength) {
                                    birdChirpLength = 0
                                    birdChirpProgress = 0
                                    birdCountdown = sampleRate * 4 / 5 + random.nextInt(sampleRate * 12 / 5)
                                }
                            } else {
                                birdChirpLength = (sampleRate * 0.07).toInt() + random.nextInt((sampleRate * 0.08).toInt())
                                birdChirpProgress = 0
                                birdFreq = 2600.0 + random.nextInt(1200)
                                birdSweep = (random.nextDouble() * 1200.0) - 400.0
                            }

                            leaves + birdSample
                        }
                        AmbientSoundTrack.SUMMER_LEAVES -> {
                            // Rustling wind / leaves: pink noise with slow sinusoidal modulation
                            phase1 += 2.0 * Math.PI * 0.2 / sampleRate
                            val wind = (sin(phase1) + 1.2) * 0.4
                            val white = (random.nextDouble() * 2.0 - 1.0)
                            lastSample = (lastSample * 0.94) + (white * 0.06)
                            lastSample * wind * 0.6
                        }
                        AmbientSoundTrack.CLEAR_ALIGNMENT -> {
                            // 432 Hz pure tone with subtle harmonic and gentle tremolo
                            phase1 += 2.0 * Math.PI * 432.0 / sampleRate
                            phase2 += 2.0 * Math.PI * 0.5 / sampleRate
                            val tremolo = 0.85 + 0.15 * sin(phase2)
                            val tone = sin(phase1) * 0.7 + sin(phase1 * 2.0) * 0.15
                            tone * tremolo * 0.25
                        }
                        AmbientSoundTrack.SEVEN_MILES -> {
                            // Ocean waves: rolling surf envelope (~0.12 Hz cycle)
                            phase1 += 2.0 * Math.PI * 0.12 / sampleRate
                            val surf = (sin(phase1).coerceAtLeast(0.0)).pow(2.0) * 0.8 + 0.1
                            val white = (random.nextDouble() * 2.0 - 1.0)
                            lastSample = (lastSample * 0.96) + (white * 0.04)
                            lastSample * surf * 0.7
                        }
                        AmbientSoundTrack.UNFINISHED_MANUSCRIPT -> {
                            // Hearth crackles + low warm hum
                            phase1 += 2.0 * Math.PI * 65.0 / sampleRate
                            val hum = sin(phase1) * 0.08
                            val crackle = if (random.nextInt(1000) < 5) (random.nextDouble() * 0.9 - 0.45) else 0.0
                            val hiss = (random.nextDouble() * 2.0 - 1.0) * 0.02
                            hum + crackle + hiss
                        }
                        AmbientSoundTrack.THREE_AM_SCRIBBLES -> {
                            // Late night drone (110 Hz fundamental + sub-bass 55 Hz)
                            phase1 += 2.0 * Math.PI * 110.0 / sampleRate
                            phase2 += 2.0 * Math.PI * 55.0 / sampleRate
                            val drone = sin(phase1) * 0.25 + sin(phase2) * 0.15
                            drone * 0.4
                        }
                        AmbientSoundTrack.RAINFOREST -> {
                            // Rainforest: gentle rain bed + intermittent procedural water drop / chirp
                            val white = (random.nextDouble() * 2.0 - 1.0)
                            lastSample = (lastSample * 0.90) + (white * 0.05)
                            val drip = if (random.nextInt(600) < 3) (random.nextDouble() * 0.5 - 0.25) else 0.0
                            (lastSample * 0.4) + drip
                        }
                        AmbientSoundTrack.NONE -> 0.0
                    }

                    buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                try {
                    val track = audioTrack
                    if (track != null && isPlaying) {
                        track.write(buffer, 0, buffer.size)
                    } else {
                        break
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }.apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    fun stop() {
        isPlaying = false
        currentTrack = AmbientSoundTrack.NONE
        try {
            audioThread?.join(500)
        } catch (_: Exception) {}
        audioThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
