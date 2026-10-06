package com.duta.movie.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEnhancerTest {

    @Test
    fun testModeFromId() {
        assertEquals(VoiceEnhancerMode.OFF, VoiceEnhancerMode.fromId(0))
        assertEquals(VoiceEnhancerMode.CLEAR_VOICE, VoiceEnhancerMode.fromId(1))
        assertEquals(VoiceEnhancerMode.ACTION_SFX_CUT, VoiceEnhancerMode.fromId(2))
        assertEquals(VoiceEnhancerMode.NIGHT_MAX, VoiceEnhancerMode.fromId(3))
        assertEquals(VoiceEnhancerMode.OFF, VoiceEnhancerMode.fromId(-1))
        assertEquals(VoiceEnhancerMode.OFF, VoiceEnhancerMode.fromId(99))
    }

    @Test
    fun testModeNextCycle() {
        var mode = VoiceEnhancerMode.OFF
        mode = mode.next()
        assertEquals(VoiceEnhancerMode.CLEAR_VOICE, mode)
        mode = mode.next()
        assertEquals(VoiceEnhancerMode.ACTION_SFX_CUT, mode)
        mode = mode.next()
        assertEquals(VoiceEnhancerMode.NIGHT_MAX, mode)
        mode = mode.next()
        assertEquals(VoiceEnhancerMode.OFF, mode)
    }

    @Test
    fun testDspGainOffMode() {
        assertEquals(0, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.OFF, 60))
        assertEquals(0, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.OFF, 1000))
        assertEquals(0, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.OFF, 4000))
        assertEquals(0, VoiceDspHelper.getTargetLoudnessGainMb(VoiceEnhancerMode.OFF))
    }

    @Test
    fun testDspGainClearVoice() {
        // Bass rumble < 300 Hz should be attenuated
        assertTrue(VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.CLEAR_VOICE, 80) < 0)
        assertEquals(-300, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.CLEAR_VOICE, 120))

        // Vocal presence 1000-4000 Hz should be boosted
        assertEquals(450, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.CLEAR_VOICE, 2500))

        // Loudness leveling
        assertEquals(300, VoiceDspHelper.getTargetLoudnessGainMb(VoiceEnhancerMode.CLEAR_VOICE))
    }

    @Test
    fun testDspGainActionSfxCut() {
        // Heavy bass rumble / explosions should be strongly cut (-6dB)
        assertEquals(-600, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.ACTION_SFX_CUT, 60))
        assertEquals(-600, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.ACTION_SFX_CUT, 250))

        // Dialogue frequencies boosted significantly (+7.5dB)
        assertEquals(750, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.ACTION_SFX_CUT, 1500))
        assertEquals(750, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.ACTION_SFX_CUT, 3500))

        // Loudness dynamic compression gain
        assertEquals(600, VoiceDspHelper.getTargetLoudnessGainMb(VoiceEnhancerMode.ACTION_SFX_CUT))
    }

    @Test
    fun testDspGainNightMax() {
        // Deep bass cut (-9dB) to avoid shaking walls / loud explosions
        assertEquals(-900, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.NIGHT_MAX, 100))

        // Maximum dialogue intelligibility (+10dB)
        assertEquals(1000, VoiceDspHelper.calculateBandGainMb(VoiceEnhancerMode.NIGHT_MAX, 2000))

        // Maximum loudness compression (+9dB)
        assertEquals(900, VoiceDspHelper.getTargetLoudnessGainMb(VoiceEnhancerMode.NIGHT_MAX))
    }
}
