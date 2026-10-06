package com.duta.movie.audio

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.duta.movie.R

enum class VoiceEnhancerMode(val id: Int, val titleResId: Int, val descResId: Int, val shortLabelResId: Int) {
    OFF(0, R.string.voice_enhancer_off, R.string.voice_enhancer_off_desc, R.string.voice_enhancer_off_short),
    CLEAR_VOICE(1, R.string.voice_enhancer_clear, R.string.voice_enhancer_clear_desc, R.string.voice_enhancer_clear_short),
    ACTION_SFX_CUT(2, R.string.voice_enhancer_action, R.string.voice_enhancer_action_desc, R.string.voice_enhancer_action_short),
    NIGHT_MAX(3, R.string.voice_enhancer_night, R.string.voice_enhancer_night_desc, R.string.voice_enhancer_night_short);

    fun next(): VoiceEnhancerMode = when (this) {
        OFF -> CLEAR_VOICE
        CLEAR_VOICE -> ACTION_SFX_CUT
        ACTION_SFX_CUT -> NIGHT_MAX
        NIGHT_MAX -> OFF
    }

    companion object {
        fun fromId(id: Int): VoiceEnhancerMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

object VoiceDspHelper {
    /**
     * Calculates the target equalizer gain in millibels (mB, where 100 mB = 1 dB)
     * for a given center frequency in Hz.
     *
     * Dialogue intelligibility is centered around 1000 Hz - 4000 Hz.
     * Heavy explosions, sub-bass rumble, and loud cinematic instruments reside below 300 Hz.
     */
    fun calculateBandGainMb(mode: VoiceEnhancerMode, centerFreqHz: Int): Int {
        return when (mode) {
            VoiceEnhancerMode.OFF -> 0
            VoiceEnhancerMode.CLEAR_VOICE -> {
                when {
                    centerFreqHz < 300 -> -300 // Cut bass rumble by -3 dB
                    centerFreqHz in 300..900 -> 150 // Warm vocal presence +1.5 dB
                    centerFreqHz in 901..4500 -> 450 // Speech intelligibility +4.5 dB
                    else -> -100 // Gentle treble roll-off -1 dB
                }
            }
            VoiceEnhancerMode.ACTION_SFX_CUT -> {
                when {
                    centerFreqHz < 300 -> -600 // Strong bass cut -6 dB (cuts heavy explosions and sound effects)
                    centerFreqHz in 300..900 -> 250 // Lower vocal range +2.5 dB
                    centerFreqHz in 901..4500 -> 750 // Strong speech boost +7.5 dB
                    else -> -250 // Treble roll-off -2.5 dB
                }
            }
            VoiceEnhancerMode.NIGHT_MAX -> {
                when {
                    centerFreqHz < 300 -> -900 // Deep bass attenuation -9 dB
                    centerFreqHz in 300..900 -> 350 // Vocal body +3.5 dB
                    centerFreqHz in 901..4500 -> 1000 // Maximum dialogue boost +10 dB
                    else -> -350 // Treble roll-off -3.5 dB
                }
            }
        }
    }

    /**
     * Target gain for LoudnessEnhancer (dynamic range compression / leveling) in millibels.
     * Increases quiet whispers while limiting loud spikes.
     */
    fun getTargetLoudnessGainMb(mode: VoiceEnhancerMode): Int {
        return when (mode) {
            VoiceEnhancerMode.OFF -> 0
            VoiceEnhancerMode.CLEAR_VOICE -> 300 // +3.0 dB
            VoiceEnhancerMode.ACTION_SFX_CUT -> 600 // +6.0 dB
            VoiceEnhancerMode.NIGHT_MAX -> 900 // +9.0 dB
        }
    }
}

class VoiceEnhancerManager {
    private var currentSessionId: Int = 0
    private var currentMode: VoiceEnhancerMode = VoiceEnhancerMode.OFF
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    @Synchronized
    fun attachSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (currentSessionId == sessionId && (equalizer != null || loudnessEnhancer != null)) {
            // Re-apply in case effects were reset by system audio routing
            applyMode(currentMode)
            return
        }
        release()
        currentSessionId = sessionId
        initEffects(sessionId)
        applyMode(currentMode)
    }

    @Synchronized
    fun setMode(mode: VoiceEnhancerMode) {
        currentMode = mode
        applyMode(mode)
    }

    fun getMode(): VoiceEnhancerMode = currentMode

    private fun initEffects(sessionId: Int) {
        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = false
            }
            Log.d(TAG, "Equalizer initialized for sessionId=$sessionId (bands=${equalizer?.numberOfBands})")
        } catch (e: Throwable) {
            Log.w(TAG, "Equalizer initialization failed on sessionId=$sessionId: ${e.message}")
            equalizer = null
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = false
            }
            Log.d(TAG, "LoudnessEnhancer initialized for sessionId=$sessionId")
        } catch (e: Throwable) {
            Log.w(TAG, "LoudnessEnhancer initialization failed on sessionId=$sessionId: ${e.message}")
            loudnessEnhancer = null
        }
    }

    private fun applyMode(mode: VoiceEnhancerMode) {
        applyEqualizer(mode)
        applyLoudnessEnhancer(mode)
    }

    private fun applyEqualizer(mode: VoiceEnhancerMode) {
        val eq = equalizer ?: return
        try {
            if (mode == VoiceEnhancerMode.OFF) {
                eq.enabled = false
                return
            }

            eq.enabled = true
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            for (i in 0 until numBands) {
                val band = i.toShort()
                val centerFreqHz = eq.getCenterFreq(band) / 1000 // mHz to Hz
                val targetMb = VoiceDspHelper.calculateBandGainMb(mode, centerFreqHz)
                val clamped = targetMb.coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(band, clamped)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to apply Equalizer curve: ${e.message}")
        }
    }

    private fun applyLoudnessEnhancer(mode: VoiceEnhancerMode) {
        val le = loudnessEnhancer ?: return
        try {
            if (mode == VoiceEnhancerMode.OFF) {
                le.enabled = false
                return
            }
            val gainMb = VoiceDspHelper.getTargetLoudnessGainMb(mode)
            le.setTargetGain(gainMb)
            le.enabled = true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to apply LoudnessEnhancer target gain: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Throwable) {}
        equalizer = null

        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (_: Throwable) {}
        loudnessEnhancer = null

        currentSessionId = 0
    }

    companion object {
        private const val TAG = "VoiceEnhancerManager"
    }
}
