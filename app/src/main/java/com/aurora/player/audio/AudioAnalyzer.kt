package com.aurora.player.audio

import android.media.audiofx.Visualizer
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Analizza l'audio in uscita dal telefono (sessione 0 = mix globale), quindi
 * funziona con qualunque app: Spotify, SimpMusic, player locali...
 * Produce 64 bande logaritmiche 0..1, energia bassi/medi/alti e un rilevatore di battito.
 */
class AudioAnalyzer {
    companion object {
        const val BANDS = 64
        const val WAVE_POINTS = 128
    }

    val bands = FloatArray(BANDS)
    val wave = FloatArray(WAVE_POINTS)
    var bass = 0f; private set
    var mid = 0f; private set
    var treble = 0f; private set
    /** 1 nel momento del battito, poi decade verso 0. */
    var beat = 0f; private set

    var status by mutableStateOf<String?>(null)
        private set
    var running by mutableStateOf(false)
        private set

    private val target = FloatArray(BANDS)
    private val waveTarget = FloatArray(WAVE_POINTS)
    private var mags = FloatArray(0)
    private var edges: IntArray? = null
    private var edgesKey = 0L
    private var ceiling = 0.35f
    private val bassHistory = FloatArray(48)
    private var historyPos = 0
    private var lastBeatMs = 0L
    @Volatile private var beatPending = false
    @Volatile private var lastDataMs = 0L
    private var visualizer: Visualizer? = null

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, samplingRate: Int) {
            data ?: return
            val n = data.size
            if (n == 0) return
            for (i in 0 until WAVE_POINTS) {
                val idx = (i.toLong() * n / WAVE_POINTS).toInt().coerceIn(0, n - 1)
                waveTarget[i] = ((data[idx].toInt() and 0xFF) - 128) / 128f
            }
        }

        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            fft ?: return
            val sr = if (samplingRate > 0) samplingRate / 1000f else 44100f
            processFft(fft, sr)
        }
    }

    fun start() {
        if (visualizer != null) return
        try {
            val v = Visualizer(0)
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(min(range[1], 1024))
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            v.setDataCaptureListener(listener, Visualizer.getMaxCaptureRate(), true, true)
            v.setEnabled(true)
            visualizer = v
            running = true
            status = null
        } catch (t: Throwable) {
            visualizer = null
            running = false
            status = "Non riesco ad agganciarmi all'audio di sistema (${t.javaClass.simpleName})"
        }
    }

    fun stop() {
        try {
            visualizer?.setEnabled(false)
            visualizer?.release()
        } catch (t: Throwable) { /* ignorato */ }
        visualizer = null
        running = false
    }

    private fun processFft(fft: ByteArray, sampleRate: Float) {
        val n = fft.size
        val bins = n / 2
        if (bins < 8) return
        if (mags.size != bins) mags = FloatArray(bins)
        mags[0] = abs(fft[0].toFloat())
        for (k in 1 until bins) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            mags[k] = sqrt(re * re + im * im)
        }
        val key = n.toLong() * 1_000_000L + sampleRate.toLong()
        val e = edges?.takeIf { edgesKey == key }
            ?: computeEdges(n, bins, sampleRate).also { edges = it; edgesKey = key }

        var frameMax = 0f
        for (b in 0 until BANDS) {
            val lo = e[b]
            val hi = min(max(e[b + 1], lo + 1), bins)
            var sum = 0f
            var peak = 0f
            for (k in lo until hi) {
                sum += mags[k]
                if (mags[k] > peak) peak = mags[k]
            }
            val avg = sum / max(1, hi - lo)
            val m = avg * 0.5f + peak * 0.5f
            val tilt = 1f + (b.toFloat() / BANDS) * 1.2f // gli alti sono naturalmente più deboli
            val x = ln(1f + m * tilt) / ln(129f)
            target[b] = x
            if (x > frameMax) frameMax = x
        }
        // Guadagno automatico: si adatta al volume del brano
        ceiling = max(ceiling * 0.992f, frameMax).coerceAtLeast(0.12f)
        for (b in 0 until BANDS) target[b] = (target[b] / ceiling).pow(1.4f).coerceIn(0f, 1f)

        // Rilevamento battito sui bassi
        var bassNow = 0f
        for (b in 0 until 8) bassNow += target[b]
        bassNow /= 8f
        var avg = 0f
        for (h in bassHistory) avg += h
        avg /= bassHistory.size
        bassHistory[historyPos] = bassNow
        historyPos = (historyPos + 1) % bassHistory.size
        val now = SystemClock.uptimeMillis()
        if (bassNow > avg * 1.3f + 0.04f && bassNow > 0.3f && now - lastBeatMs > 260) {
            lastBeatMs = now
            beatPending = true
        }
        lastDataMs = now
    }

    private fun computeEdges(n: Int, bins: Int, sr: Float): IntArray {
        val fMin = 32f
        val fMax = min(16000f, sr / 2f)
        val out = IntArray(BANDS + 1)
        for (b in 0..BANDS) {
            val f = fMin * (fMax / fMin).pow(b.toFloat() / BANDS)
            var k = (f * n / sr).roundToInt().coerceIn(1, bins)
            if (b > 0 && k <= out[b - 1]) k = min(out[b - 1] + 1, bins)
            out[b] = k
        }
        return out
    }

    /** Chiamato a ogni fotogramma: rende i movimenti fluidi a 60/120 Hz. */
    fun update(dt: Float) {
        val now = SystemClock.uptimeMillis()
        val stale = now - lastDataMs > 1200
        val up = 1f - exp(-dt * 28f)
        val down = 1f - exp(-dt * 7f)
        var bs = 0f
        var ms = 0f
        var ts = 0f
        for (i in 0 until BANDS) {
            val t = if (stale) 0f else target[i]
            val cur = bands[i]
            bands[i] = cur + (t - cur) * (if (t > cur) up else down)
            when {
                i < 8 -> bs += bands[i]
                i < 36 -> ms += bands[i]
                else -> ts += bands[i]
            }
        }
        bass = bs / 8f
        mid = ms / 28f
        treble = ts / 28f
        val wk = 1f - exp(-dt * 20f)
        for (i in 0 until WAVE_POINTS) {
            val t = if (stale) 0f else waveTarget[i]
            wave[i] += (t - wave[i]) * wk
        }
        if (beatPending) {
            beatPending = false
            beat = 1f
        } else {
            beat = max(0f, beat - dt * 3.2f)
        }
    }
}
