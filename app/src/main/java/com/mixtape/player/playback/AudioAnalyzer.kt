package com.mixtape.player.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Listens to the decoded audio inside the player (no microphone involved) and turns it
 * into frequency bands, a waveform and an overall level for the visualizer.
 * Both crossfade players feed the same analyzer.
 */
@OptIn(UnstableApi::class)
class AudioAnalyzer : TeeAudioProcessor.AudioBufferSink {

    companion object {
        const val BANDS = 48
        const val WAVE_POINTS = 160
        private const val N = 1024
        private const val MIN_HZ = 40.0
        private const val MAX_HZ = 16_000.0
    }

    // Latest results, read by the UI each frame.
    @Volatile var bands = FloatArray(BANDS); private set
    @Volatile var wave = FloatArray(WAVE_POINTS); private set
    @Volatile var level = 0f; private set
    @Volatile var updatedAtNanos = 0L; private set

    private var sampleRate = 44_100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    private val ring = FloatArray(N)
    private var filled = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        synchronized(this) {
            sampleRate = sampleRateHz
            channels = max(1, channelCount)
            this.encoding = encoding
            filled = 0
        }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        synchronized(this) {
            val b = buffer.duplicate().order(ByteOrder.nativeOrder())
            when (encoding) {
                C.ENCODING_PCM_16BIT -> while (b.remaining() >= 2 * channels) {
                    var sum = 0f
                    repeat(channels) { sum += b.short / 32768f }
                    push(sum / channels)
                }
                C.ENCODING_PCM_FLOAT -> while (b.remaining() >= 4 * channels) {
                    var sum = 0f
                    repeat(channels) { sum += b.float }
                    push(sum / channels)
                }
                else -> return
            }
        }
    }

    private fun push(sample: Float) {
        ring[filled++] = sample
        if (filled == N) {
            analyze()
            // Keep half the window for overlap, so updates come twice as often.
            System.arraycopy(ring, N / 2, ring, 0, N / 2)
            filled = N / 2
        }
    }

    private fun analyze() {
        // Waveform + level
        var sq = 0f
        val w = FloatArray(WAVE_POINTS)
        val step = N.toFloat() / WAVE_POINTS
        for (i in 0 until WAVE_POINTS) w[i] = ring[(i * step).toInt()]
        for (s in ring) sq += s * s
        val rms = sqrt(sq / N)

        // Spectrum
        for (i in 0 until N) {
            re[i] = ring[i] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val out = FloatArray(BANDS)
        val binHz = sampleRate.toDouble() / N
        for (b in 0 until BANDS) {
            val lo = MIN_HZ * (MAX_HZ / MIN_HZ).pow(b.toDouble() / BANDS)
            val hi = MIN_HZ * (MAX_HZ / MIN_HZ).pow((b + 1).toDouble() / BANDS)
            val from = max(1, (lo / binHz).toInt())
            val to = min(N / 2 - 1, max(from, (hi / binHz).toInt()))
            var peak = 0f
            for (k in from..to) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                if (m > peak) peak = m
            }
            // Magnitude → decibels → 0..1 over a 60 dB range; a gentle tilt lifts the highs.
            val db = 20 * log10(peak / (N / 4f) + 1e-9f) + b * 0.25f
            out[b] = ((db + 60f) / 60f).coerceIn(0f, 1f)
        }

        bands = out
        wave = w
        level = min(1f, rms * 2.5f)
        updatedAtNanos = System.nanoTime()
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
