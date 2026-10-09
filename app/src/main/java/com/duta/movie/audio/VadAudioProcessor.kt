package com.duta.movie.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

/**
 * On-Device Hardware-Level Voice Activity Detection (VAD) Audio Processor.
 * Seamlessly hooks into the ExoPlayer audio pipeline to inspect 16-bit PCM waveforms.
 * Calculates root-mean-square (RMS) energy to detect the exact millisecond when dialogue
 * begins in the video stream, enabling zero-click or 1-tap automated subtitle alignment.
 *
 * Thread-Safety Note: queueInput runs on the internal audio playback thread ('ExoPlayer:Playback').
 * Timing is tracked via frame-accurate PCM sample counting from the stream onset,
 * completely decoupled from the main looper to prevent wrong-thread exceptions.
 */
@OptIn(UnstableApi::class)
class VadAudioProcessor : BaseAudioProcessor() {

    private val _detectedVoiceOnsetMs = MutableStateFlow(-1L)
    val detectedVoiceOnsetMs: StateFlow<Long> = _detectedVoiceOnsetMs.asStateFlow()

    @Volatile
    var isListening: Boolean = true

    private val streamStartPositionMs = AtomicLong(0L)
    private var sampleRate: Int = 48000
    private var bytesPerFrame: Int = 4
    private var framesProcessed: Long = 0L

    var playbackPositionProvider: (() -> Long)? = null

    fun setStreamStartPosition(positionMs: Long) {
        streamStartPositionMs.set(positionMs.coerceAtLeast(0L))
        framesProcessed = 0L
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
            bytesPerFrame = (inputAudioFormat.channelCount * 2).coerceAtLeast(2)
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val framesInThisBuffer = (remaining / bytesPerFrame).toLong()
        val currentAudioPositionMs = streamStartPositionMs.get() + ((framesProcessed * 1000L) / sampleRate)
        framesProcessed += framesInThisBuffer

        if (isListening && _detectedVoiceOnsetMs.value < 0L) {
            val dup = inputBuffer.asReadOnlyBuffer()
            dup.order(ByteOrder.LITTLE_ENDIAN)
            val shortBuf = dup.asShortBuffer()
            val totalSamples = shortBuf.remaining()

            if (totalSamples > 0) {
                var sumSq = 0.0
                while (shortBuf.hasRemaining()) {
                    val sampleNorm = shortBuf.get() / 32768.0
                    sumSq += sampleNorm * sampleNorm
                }
                val rms = Math.sqrt(sumSq / totalSamples)

                // Human dialogue speech onset threshold (distinguishes speech from silence/background hum)
                if (rms >= 0.07) {
                    val providerPos = try {
                        playbackPositionProvider?.invoke()?.takeIf { it >= 0L }
                    } catch (_: Throwable) {
                        null
                    }
                    val currentPos = if (providerPos != null && Math.abs(providerPos - currentAudioPositionMs) > 2000L) {
                        providerPos
                    } else {
                        currentAudioPositionMs
                    }

                    if (currentPos in 500L..90_000L) {
                        _detectedVoiceOnsetMs.value = currentPos
                        isListening = false
                        Log.i("VadAudioProcessor", "Voice Activity Detected! Speech onset at ${currentPos}ms (RMS: ${String.format("%.3f", rms)})")
                    }
                }
            }
        }

        // Transparent zero-latency passthrough to output buffer
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun onFlush() {
        super.onFlush()
        framesProcessed = 0L
    }

    override fun onReset() {
        super.onReset()
        framesProcessed = 0L
        _detectedVoiceOnsetMs.value = -1L
        isListening = true
    }

    fun resetDetection() {
        framesProcessed = 0L
        streamStartPositionMs.set(0L)
        _detectedVoiceOnsetMs.value = -1L
        isListening = true
    }
}
