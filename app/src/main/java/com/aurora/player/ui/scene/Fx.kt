package com.aurora.player.ui.scene

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.aurora.player.audio.AudioAnalyzer
import com.aurora.player.audio.AudioAnalyzer.Companion.BANDS
import com.aurora.player.settings.ColorMode
import kotlin.math.min

internal const val TAU = (2 * Math.PI).toFloat()
internal const val PIF = Math.PI.toFloat()

/** Stato condiviso da tutti i livelli in un fotogramma (audio già levigato, colori, buffer di lavoro). */
class Fx {
    var time = 0f
    var dt = 0.016f
    val bands = FloatArray(BANDS)
    val echo = FloatArray(BANDS)
    val peaks = FloatArray(BANDS)
    var wave = FloatArray(AudioAnalyzer.WAVE_POINTS)
    var bass = 0f
    var mid = 0f
    var treble = 0f
    var beat = 0f
    var beatHit = false
    var sensitivity = 1f
    var colors = SceneColors.Default
    var mode = ColorMode.ALBUM
    var glow = true
    var eco = false
    var spin = 0f

    val px = FloatArray(1024)
    val py = FloatArray(1024)
    val path = Path()
    val path2 = Path()
    val clip = Path()
    val logoVals = FloatArray(1024)
    val logoTmp = FloatArray(1024)
    val logoEcho = FloatArray(1024)

    fun col(t: Float): Color = colorAt(mode, colors, t, time)

    fun mirrored(arr: FloatArray, i: Int, n: Int): Float = arr[if (i < BANDS) i else n - 1 - i]

    /** Banda interpolata, u in 0..1 (0 = bassi, 1 = alti). */
    fun band(u: Float): Float {
        val x = u.coerceIn(0f, 1f) * (BANDS - 1)
        val i0 = x.toInt()
        val i1 = min(i0 + 1, BANDS - 1)
        val f = x - i0
        return bands[i0] * (1f - f) + bands[i1] * f
    }

    fun smoothClosed(p: Path, n: Int) {
        p.reset()
        p.moveTo((px[n - 1] + px[0]) / 2f, (py[n - 1] + py[0]) / 2f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            p.quadraticTo(px[i], py[i], (px[i] + px[j]) / 2f, (py[i] + py[j]) / 2f)
        }
        p.close()
    }

    fun smoothOpen(p: Path, n: Int) {
        p.reset()
        p.moveTo(px[0], py[0])
        for (i in 1 until n - 1) p.quadraticTo(px[i], py[i], (px[i] + px[i + 1]) / 2f, (py[i] + py[i + 1]) / 2f)
        p.lineTo(px[n - 1], py[n - 1])
    }
}
