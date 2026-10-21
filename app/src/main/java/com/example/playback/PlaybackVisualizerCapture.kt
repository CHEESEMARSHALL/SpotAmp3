package com.example.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal const val PLAYBACK_VISUALIZER_WAVEFORM_SAMPLES = 128
internal const val PLAYBACK_VISUALIZER_SPECTRUM_BANDS = 48

internal data class PlaybackVisualizerFrame(
    val waveform: FloatArray = FloatArray(PLAYBACK_VISUALIZER_WAVEFORM_SAMPLES),
    val spectrum: FloatArray = FloatArray(PLAYBACK_VISUALIZER_SPECTRUM_BANDS)
) {
    companion object {
        fun silence() = PlaybackVisualizerFrame()
    }
}

/** Captures a throttled view of the decoded PCM already flowing through Media3. */
@UnstableApi
internal class PlaybackVisualizerCapture(
    private val publishFrame: (PlaybackVisualizerFrame) -> Unit,
    private val minimumFrameIntervalMs: Long = 40L,
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() }
) : TeeAudioProcessor.AudioBufferSink {
    @Volatile
    private var enabled = false

    @Volatile
    private var sampleRateHz = 0

    @Volatile
    private var channelCount = 0

    @Volatile
    private var encoding = C.ENCODING_INVALID

    @Volatile
    private var lastFrameAtMs = 0L

    private val stateLock = Any()
    private var generation = 0L

    /** Disabling pauses frame updates and can optionally reset the displayed frame. */
    fun setEnabled(value: Boolean, clearFrameWhenDisabled: Boolean = true) {
        synchronized(stateLock) {
            if (enabled != value) {
                enabled = value
                generation++
                lastFrameAtMs = 0L
            }
            if (!value && clearFrameWhenDisabled) {
                publishFrame(PlaybackVisualizerFrame.silence())
            }
        }
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRateHz = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        lastFrameAtMs = 0L
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val captureGeneration = synchronized(stateLock) {
            if (!enabled) return

            val now = elapsedRealtimeMs()
            if (now - lastFrameAtMs < minimumFrameIntervalMs) return
            lastFrameAtMs = now
            generation
        }

        val frame = analyzePlaybackPcm(
            buffer = buffer,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            encoding = encoding
        )
        synchronized(stateLock) {
            // A pause/stop may arrive while FFT work is in flight. Drop that stale frame
            // instead of allowing the visualizer to move once more after playback stopped.
            if (enabled && generation == captureGeneration) publishFrame(frame)
        }
    }
}

/** Keeps capture aligned with both the user's visualizer choice and Media3 playback state. */
@UnstableApi
internal class PlaybackVisualizerPlaybackGate(
    private val capture: PlaybackVisualizerCapture
) {
    @Volatile
    private var captureRequested = false

    fun setCaptureRequested(enabled: Boolean, isPlaying: Boolean) {
        captureRequested = enabled
        capture.setEnabled(
            value = enabled && isPlaying,
            clearFrameWhenDisabled = !enabled
        )
    }

    fun onPlaybackChanged(isPlaying: Boolean) {
        capture.setEnabled(
            value = captureRequested && isPlaying,
            clearFrameWhenDisabled = false
        )
    }
}

@UnstableApi
internal fun analyzePlaybackPcm(
    buffer: ByteBuffer,
    sampleRateHz: Int,
    channelCount: Int,
    encoding: Int
): PlaybackVisualizerFrame {
    val bytesPerSample = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
        else -> return PlaybackVisualizerFrame.silence()
    }
    if (sampleRateHz <= 0 || channelCount <= 0) return PlaybackVisualizerFrame.silence()

    val input = buffer.duplicate().order(
        if (encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN
        ) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    )
    val start = input.position()
    val frameSize = bytesPerSample * channelCount
    val frameCount = input.remaining() / frameSize
    if (frameCount <= 0) return PlaybackVisualizerFrame.silence()

    fun monoSample(frameIndex: Int): Float {
        val frameOffset = start + frameIndex * frameSize
        var channelSum = 0f
        for (channel in 0 until channelCount) {
            val offset = frameOffset + channel * bytesPerSample
            val sample = when (encoding) {
                C.ENCODING_PCM_8BIT ->
                    ((input.get(offset).toInt() and 0xff) - 128) / 128f
                C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN ->
                    input.getShort(offset) / 32768f
                C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> {
                    val first = input.get(offset).toInt() and 0xff
                    val second = input.get(offset + 1).toInt() and 0xff
                    val third = input.get(offset + 2).toInt() and 0xff
                    var value = if (input.order() == ByteOrder.BIG_ENDIAN) {
                        (first shl 16) or (second shl 8) or third
                    } else {
                        (third shl 16) or (second shl 8) or first
                    }
                    if ((value and 0x800000) != 0) value = value or -0x1000000
                    value / 8_388_608f
                }
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN ->
                    input.getInt(offset) / 2_147_483_648f
                C.ENCODING_PCM_FLOAT -> input.getFloat(offset).takeIf(Float::isFinite) ?: 0f
                else -> 0f
            }
            channelSum += sample.coerceIn(-1f, 1f)
        }
        return channelSum / channelCount
    }

    val waveform = FloatArray(PLAYBACK_VISUALIZER_WAVEFORM_SAMPLES) { index ->
        val sourceIndex = if (PLAYBACK_VISUALIZER_WAVEFORM_SAMPLES == 1) 0 else {
            index * (frameCount - 1) / (PLAYBACK_VISUALIZER_WAVEFORM_SAMPLES - 1)
        }
        monoSample(sourceIndex)
    }

    val fftSize = 512
    val fftInput = FloatArray(fftSize)
    val windowSum = (0 until fftSize).sumOf { index ->
        val window = 0.5 - 0.5 * cos(2.0 * PI * index / (fftSize - 1))
        val sourceIndex = if (frameCount <= fftSize) {
            index * (frameCount - 1) / (fftSize - 1)
        } else {
            frameCount - fftSize + index
        }
        fftInput[index] = monoSample(sourceIndex) * window.toFloat()
        window
    }
    val real = fftInput
    val imaginary = FloatArray(fftSize)
    fftInPlace(real, imaginary)

    val spectrum = FloatArray(PLAYBACK_VISUALIZER_SPECTRUM_BANDS)
    val minFrequencyHz = 35.0
    val maxFrequencyHz = min(16_000.0, sampleRateHz / 2.0)
    val usableMaxBin = fftSize / 2
    val logarithmicRange = ln(maxFrequencyHz / minFrequencyHz)
    if (logarithmicRange <= 0.0) return PlaybackVisualizerFrame(waveform, spectrum)

    for (band in spectrum.indices) {
        val lowerHz = minFrequencyHz * (maxFrequencyHz / minFrequencyHz).pow(
            band.toDouble() / spectrum.size
        )
        val upperHz = minFrequencyHz * (maxFrequencyHz / minFrequencyHz).pow(
            (band + 1).toDouble() / spectrum.size
        )
        val firstBin = max(1, floor(lowerHz * fftSize / sampleRateHz).toInt())
            .coerceAtMost(usableMaxBin)
        val lastBin = max(firstBin, ceil(upperHz * fftSize / sampleRateHz).toInt())
            .coerceAtMost(usableMaxBin)
        var energy = 0.0
        var binCount = 0
        for (bin in firstBin..lastBin) {
            val magnitude = sqrt(
                real[bin].toDouble() * real[bin] + imaginary[bin].toDouble() * imaginary[bin]
            ) / (windowSum / 2.0)
            energy += magnitude * magnitude
            binCount++
        }
        val rmsMagnitude = sqrt(energy / max(1, binCount))
        spectrum[band] = (rmsMagnitude * 4.0).pow(0.72).toFloat().coerceIn(0f, 1f)
    }

    return PlaybackVisualizerFrame(waveform = waveform, spectrum = spectrum)
}

private fun fftInPlace(real: FloatArray, imaginary: FloatArray) {
    val size = real.size
    var reversed = 0
    for (index in 1 until size) {
        var bit = size shr 1
        while (reversed and bit != 0) {
            reversed = reversed xor bit
            bit = bit shr 1
        }
        reversed = reversed xor bit
        if (index < reversed) {
            val realValue = real[index]
            real[index] = real[reversed]
            real[reversed] = realValue
            val imaginaryValue = imaginary[index]
            imaginary[index] = imaginary[reversed]
            imaginary[reversed] = imaginaryValue
        }
    }

    var blockSize = 2
    while (blockSize <= size) {
        val halfBlock = blockSize / 2
        val angleStep = -2.0 * PI / blockSize
        for (blockStart in 0 until size step blockSize) {
            for (offset in 0 until halfBlock) {
                val angle = angleStep * offset
                val twiddleReal = cos(angle).toFloat()
                val twiddleImaginary = kotlin.math.sin(angle).toFloat()
                val evenIndex = blockStart + offset
                val oddIndex = evenIndex + halfBlock
                val oddReal = real[oddIndex] * twiddleReal - imaginary[oddIndex] * twiddleImaginary
                val oddImaginary = real[oddIndex] * twiddleImaginary + imaginary[oddIndex] * twiddleReal
                real[oddIndex] = real[evenIndex] - oddReal
                imaginary[oddIndex] = imaginary[evenIndex] - oddImaginary
                real[evenIndex] += oddReal
                imaginary[evenIndex] += oddImaginary
            }
        }
        blockSize *= 2
    }
}
