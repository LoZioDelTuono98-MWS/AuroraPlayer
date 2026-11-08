package com.aurora.player.ui.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aurora.player.audio.AudioAnalyzer
import com.aurora.player.audio.AudioAnalyzer.Companion.BANDS
import com.aurora.player.logo.LogoAssets
import com.aurora.player.settings.BackgroundMode
import com.aurora.player.settings.ColorMode
import com.aurora.player.settings.LogoEdge
import com.aurora.player.settings.VisualSettings
import com.aurora.player.settings.VisualizerStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class SceneInputs(
    val frameNanos: Long,
    val analyzer: AudioAnalyzer,
    val settings: VisualSettings,
    val colors: SceneColors,
    val art: ArtAssets?,
    val customBg: ImageBitmap?,
    val logo: LogoAssets?,
    val playing: Boolean,
)

private const val TAU = (2 * PI).toFloat()
private const val RING_LIFE = 1.3f

/** Disegna tutta la scena a ogni fotogramma: sfondo → foschia → particelle → visualizzatore → flash. */
class SceneRenderer {
    private val particles = ParticleSystem()
    private val display = FloatArray(BANDS)
    private val echo = FloatArray(BANDS)
    private val peaks = FloatArray(BANDS)
    private val px = FloatArray(1024)
    private val py = FloatArray(1024)
    private val path = Path()
    private val path2 = Path()
    private val clip = Path()
    private val rnd = Random(3)
    private val fogSeeds = Random(11).let { r ->
        Array(6) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.35f + r.nextFloat() * 0.3f, r.nextFloat() * TAU, 0.04f + r.nextFloat() * 0.06f) }
    }

    private var time = 0f
    private var lastNanos = 0L
    private var spin = 0f
    private val ringLife = FloatArray(8) { -1f }
    private var prevBeat = 0f
    private var lastDt = 0.016f
    private val logoVals = FloatArray(1024)
    private val logoTmp = FloatArray(1024)
    private val logoEcho = FloatArray(1024)
    private var mode = ColorMode.ALBUM
    private var colors = SceneColors.Default

    private fun col(t: Float): Color = colorAt(mode, colors, t, time)

    fun draw(scope: DrawScope, inp: SceneInputs) = with(scope) {
        val dt = if (lastNanos == 0L) 0.016f else ((inp.frameNanos - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = inp.frameNanos
        lastDt = dt
        time += dt
        mode = inp.settings.colorMode
        colors = inp.colors

        val a = inp.analyzer
        a.update(dt)
        val s = inp.settings
        val sens = s.sensitivity
        for (i in 0 until BANDS) {
            val v = (a.bands[i] * sens).coerceIn(0f, 1.15f)
            display[i] = v
            peaks[i] = if (v > peaks[i]) v else max(0f, peaks[i] - dt * 0.5f)
            echo[i] += (v - echo[i]) * min(1f, dt * 3f)
        }
        val bass = (a.bass * sens).coerceIn(0f, 1.3f)
        val mid = (a.mid * sens).coerceIn(0f, 1.3f)
        val beat = a.beat
        val treble = (a.treble * sens).coerceIn(0f, 1.3f)
        updateRings(dt, beat)
        if (inp.playing && s.spin) spin = (spin + dt * (10f + bass * 40f)) % 360f

        drawBackground(inp, bass, beat)
        if (s.fog) drawFog(s.fogIntensity, mid, beat)
        if (s.rays) drawRays(s.raysIntensity, treble, bass)

        val shakeAmp = if (s.bassShake) beat * 14.dp.toPx() else 0f
        val sx = (rnd.nextFloat() - 0.5f) * shakeAmp
        val sy = (rnd.nextFloat() - 0.5f) * shakeAmp
        translate(sx, sy) {
            if (s.particles) {
                particles.update(dt, size.width, size.height, s.particleStyle, s.particleDensity, bass, beat)
                particles.draw(this, s.particleStyle, bass, mode, colors, time)
            }
            if (s.shockwaves) drawRings()
            when (s.style) {
                VisualizerStyle.CIRCLE_BARS -> drawCircleBars(inp, bass, beat)
                VisualizerStyle.CIRCLE_WAVE -> drawCircleWave(inp, bass, beat)
                VisualizerStyle.BARS -> drawBars(inp, bass, beat)
                VisualizerStyle.WAVEFORM -> drawWaveform(inp, bass, beat)
                VisualizerStyle.LOGO -> drawLogo(inp, bass, beat)
            }
        }

        if (s.beatFlash) drawRect(colors.c1.copy(alpha = beat * 0.10f), blendMode = BlendMode.Plus)
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.6f),
                center = center,
                radius = max(size.width, size.height) * 0.75f,
            )
        )
    }

    // ---------- Sfondo ----------

    private fun DrawScope.drawBackground(inp: SceneInputs, bass: Float, beat: Float) {
        val s = inp.settings
        val zoom = 1f + if (s.backgroundPulse) bass * 0.06f + beat * 0.03f else 0f
        when (s.background) {
            BackgroundMode.ALBUM, BackgroundMode.CUSTOM -> {
                val custom = s.background == BackgroundMode.CUSTOM
                val img = if (custom) inp.customBg else inp.art?.blurred
                if (img != null) {
                    val drift = if (custom) Offset(sin(time * 0.05f) * size.width * 0.02f, cos(time * 0.04f) * size.height * 0.02f) else Offset.Zero
                    drawCover(img, zoom * (if (custom) 1.05f else 1.15f), drift)
                } else {
                    drawUserGradient(s.bgColors.map { Color(it) }, false, true)
                }
            }
            BackgroundMode.GRADIENT -> drawUserGradient(s.bgColors.map { Color(it) }, s.bgRadial, s.bgAnimated, zoom)
            BackgroundMode.SOLID -> drawRect(Color(s.bgColors[0]))
        }
        drawRect(Color.Black.copy(alpha = s.backgroundDim.coerceIn(0f, 0.95f)))
    }

    private fun DrawScope.drawUserGradient(cols: List<Color>, radial: Boolean, animated: Boolean, zoom: Float = 1f) {
        val t = if (animated) time else 0f
        if (radial) {
            val ctr = Offset(
                size.width * (0.5f + 0.15f * sin(t * 0.12f)),
                size.height * (0.5f + 0.12f * cos(t * 0.09f)),
            )
            drawRect(Brush.radialGradient(cols, center = ctr, radius = max(size.width, size.height) * 0.75f * zoom))
        } else {
            val ang = 0.8f + t * 0.12f
            drawRect(
                Brush.linearGradient(
                    cols,
                    start = Offset(size.width * (0.5f + 0.6f * cos(ang)), size.height * (0.5f + 0.6f * sin(ang))),
                    end = Offset(size.width * (0.5f - 0.6f * cos(ang)), size.height * (0.5f - 0.6f * sin(ang))),
                )
            )
        }
    }

    // ---------- Raggi di luce ----------

    private fun DrawScope.drawRays(intensity: Float, treble: Float, bass: Float) {
        val n = 14
        val ctr = center
        val len = max(size.width, size.height)
        for (i in 0 until n) {
            val ang = time * 0.08f + i * TAU / n
            val half = 0.035f + treble * 0.05f + (if (i % 2 == 0) 0.015f else 0f)
            path.reset()
            path.moveTo(ctr.x, ctr.y)
            path.lineTo(ctr.x + cos(ang - half) * len, ctr.y + sin(ang - half) * len)
            path.lineTo(ctr.x + cos(ang + half) * len, ctr.y + sin(ang + half) * len)
            path.close()
            val alpha = (intensity * (0.05f + treble * 0.12f + bass * 0.06f)).coerceIn(0f, 0.5f)
            drawPath(
                path,
                Brush.radialGradient(listOf(col(i.toFloat() / n).copy(alpha = alpha), Color.Transparent), center = ctr, radius = len * 0.7f),
                blendMode = BlendMode.Plus,
            )
        }
    }

    // ---------- Onde d'urto sul battito ----------

    private fun updateRings(dt: Float, beat: Float) {
        if (beat > 0.95f && prevBeat < 0.9f) {
            val slot = ringLife.indexOfFirst { it < 0f }.takeIf { it >= 0 } ?: ringLife.indices.maxByOrNull { ringLife[it] } ?: 0
            ringLife[slot] = 0f
        }
        prevBeat = beat
        for (i in ringLife.indices) {
            if (ringLife[i] < 0f) continue
            ringLife[i] += dt
            if (ringLife[i] > RING_LIFE) ringLife[i] = -1f
        }
    }

    private fun DrawScope.drawRings() {
        val minDim = min(size.width, size.height)
        for (i in ringLife.indices) {
            val l = ringLife[i]
            if (l < 0f) continue
            val k = l / RING_LIFE
            val r = minDim * (0.2f + k * 0.75f)
            val a = (1f - k) * (1f - k) * 0.55f
            drawCircle(colors.c2.copy(alpha = a * 0.35f), r, center, style = Stroke((10f * (1f - k) + 2f) * density), blendMode = BlendMode.Plus)
            drawCircle(colors.c2.copy(alpha = a), r, center, style = Stroke((2.5f * (1f - k) + 0.5f) * density), blendMode = BlendMode.Plus)
        }
    }

    private fun darken(c: Color) = lerp(c, Color.Black, 0.55f)

    private fun DrawScope.drawCover(img: ImageBitmap, scale: Float, offset: Offset) {
        val iw = img.width.toFloat()
        val ih = img.height.toFloat()
        val r = max(size.width / iw, size.height / ih) * scale
        val dw = iw * r
        val dh = ih * r
        drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(((size.width - dw) / 2f + offset.x).roundToInt(), ((size.height - dh) / 2f + offset.y).roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }

    private fun DrawScope.drawFog(intensity: Float, mid: Float, beat: Float) {
        val w = size.width
        val h = size.height
        val maxDim = max(w, h)
        for ((i, f) in fogSeeds.withIndex()) {
            val cx = (f[0] + 0.18f * sin(time * f[4] * TAU + f[3])) * w
            val cy = (f[1] + 0.14f * cos(time * f[4] * 1.7f * TAU + f[3] * 1.3f)) * h
            val r = f[2] * maxDim * (1f + mid * 0.15f)
            val alpha = (intensity * (0.10f + 0.22f * mid + 0.18f * beat)).coerceIn(0f, 0.6f)
            val c = col(i / 6f)
            val ctr = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.4f), Color.Transparent), center = ctr, radius = r),
                radius = r,
                center = ctr,
                blendMode = BlendMode.Screen,
            )
        }
    }

    // ---------- Copertina centrale ----------

    private fun DrawScope.drawCenterArt(inp: SceneInputs, cx: Float, cy: Float, r: Float, beat: Float) {
        val c = colors
        val ctr = Offset(cx, cy)
        val ringW = 3.dp.toPx()
        if (inp.settings.glow) {
            drawCircle(c.c1.copy(alpha = 0.25f + 0.35f * beat), r + ringW + beat * 14.dp.toPx(), ctr, style = Stroke(ringW * 2f), blendMode = BlendMode.Plus)
        }
        val art = inp.art?.image
        if (art != null) {
            clip.reset()
            clip.addOval(Rect(ctr, r))
            clipPath(clip) {
                rotate(spin, ctr) {
                    val side = min(art.width, art.height)
                    drawImage(
                        image = art,
                        srcOffset = IntOffset((art.width - side) / 2, (art.height - side) / 2),
                        srcSize = IntSize(side, side),
                        dstOffset = IntOffset((cx - r).roundToInt(), (cy - r).roundToInt()),
                        dstSize = IntSize((2 * r).roundToInt(), (2 * r).roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                }
            }
        } else {
            drawCircle(Brush.radialGradient(listOf(c.c1, lerp(c.c3, Color.Black, 0.6f)), center = ctr, radius = r), r, ctr)
        }
        drawCircle(Brush.sweepGradient(listOf(c.c1, c.c2, c.c3, c.c1), ctr), r, ctr, style = Stroke(ringW))
    }

    private fun mirrored(arr: FloatArray, i: Int, n: Int): Float = arr[if (i < BANDS) i else n - 1 - i]

    // ---------- Stile: anello di barre ----------

    private fun DrawScope.drawCircleBars(inp: SceneInputs, bass: Float, beat: Float) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = min(size.width, size.height)
        val baseR = minDim * 0.2f * (1f + bass * 0.07f + beat * 0.03f)
        val maxLen = minDim * 0.22f
        val n = BANDS * 2
        val barW = (TAU * baseR / n * 0.55f).coerceAtLeast(2f)
        val gap = 6.dp.toPx()
        val lift = 2.dp.toPx()
        drawCenterArt(inp, cx, cy, baseR - lift, beat)
        for (i in 0 until n) {
            val v = mirrored(display, i, n)
            val ang = -PI.toFloat() / 2f + i * TAU / n
            val ca = cos(ang)
            val sa = sin(ang)
            val r1 = baseR + gap
            val r2 = r1 + lift + v * maxLen
            val c = col(i.toFloat() / n)
            val st = Offset(cx + ca * r1, cy + sa * r1)
            val en = Offset(cx + ca * r2, cy + sa * r2)
            if (inp.settings.glow) drawLine(c.copy(alpha = 0.28f), st, en, strokeWidth = barW * 2.8f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            drawLine(c, st, en, strokeWidth = barW, cap = StrokeCap.Round)
            val pr = r1 + lift + mirrored(peaks, i, n) * maxLen + barW * 1.6f
            drawCircle(c.copy(alpha = 0.85f), barW * 0.45f, Offset(cx + ca * pr, cy + sa * pr))
        }
    }

    // ---------- Stile: onda circolare ----------

    private fun buildRadial(p: Path, arr: FloatArray, n: Int, cx: Float, cy: Float, r0: Float, amp: Float) {
        for (i in 0 until n) {
            val v = mirrored(arr, i, n)
            val ang = -PI.toFloat() / 2f + i * TAU / n
            val r = r0 + v * amp
            px[i] = cx + cos(ang) * r
            py[i] = cy + sin(ang) * r
        }
        smoothClosed(p, n)
    }

    private fun smoothClosed(p: Path, n: Int) {
        p.reset()
        p.moveTo((px[n - 1] + px[0]) / 2f, (py[n - 1] + py[0]) / 2f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            p.quadraticTo(px[i], py[i], (px[i] + px[j]) / 2f, (py[i] + py[j]) / 2f)
        }
        p.close()
    }

    private fun DrawScope.drawCircleWave(inp: SceneInputs, bass: Float, beat: Float) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ctr = Offset(cx, cy)
        val minDim = min(size.width, size.height)
        val baseR = minDim * 0.2f * (1f + bass * 0.08f + beat * 0.04f)
        val maxLen = minDim * 0.2f
        val n = BANDS * 2
        val r0 = baseR + 4.dp.toPx()
        val c = colors
        buildRadial(path, display, n, cx, cy, r0, maxLen)
        buildRadial(path2, echo, n, cx, cy, r0, maxLen * 1.3f)
        val sweep = Brush.sweepGradient(listOf(c.c1, c.c2, c.c3, c.c1), ctr)

        drawPath(path, Brush.radialGradient(listOf(Color.Transparent, c.c1.copy(alpha = 0.25f)), center = ctr, radius = baseR + maxLen))
        drawCenterArt(inp, cx, cy, baseR * 0.82f, beat)
        drawPath(path2, c.c2.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx()))
        if (inp.settings.glow) drawPath(path, sweep, alpha = 0.3f, style = Stroke(10.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(path, sweep, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
    }

    // ---------- Stile: barre specchiate ----------

    private fun DrawScope.drawBars(inp: SceneInputs, bass: Float, beat: Float) {
        val w = size.width
        val h = size.height
        val minDim = min(w, h)
        val n = BANDS * 2
        val left = w * 0.04f
        val slot = w * 0.92f / n
        val bw = slot * 0.62f
        val baseY = h * 0.8f
        val maxH = h * 0.36f
        val minH = 3.dp.toPx()
        drawCenterArt(inp, w / 2f, h * 0.36f, minDim * 0.15f * (1f + bass * 0.06f), beat)
        for (i in 0 until n) {
            val idx = if (i < BANDS) BANDS - 1 - i else i - BANDS
            val v = display[idx]
            val hgt = minH + v * maxH
            val x = left + i * slot + (slot - bw) / 2f
            val c = col(i.toFloat() / n)
            val corner = CornerRadius(bw / 2f)
            if (inp.settings.glow) {
                drawRoundRect(c.copy(alpha = 0.22f), Offset(x - bw * 0.6f, baseY - hgt - bw * 0.6f), Size(bw * 2.2f, hgt + bw * 1.2f), CornerRadius(bw), blendMode = BlendMode.Plus)
            }
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(c, c.copy(alpha = 0.55f)), startY = baseY - hgt, endY = baseY),
                topLeft = Offset(x, baseY - hgt),
                size = Size(bw, hgt),
                cornerRadius = corner,
            )
            drawRoundRect(c.copy(alpha = 0.16f), Offset(x, baseY + minH), Size(bw, hgt * 0.35f), corner)
            val peakY = baseY - minH - peaks[idx] * maxH - bw
            drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(x, peakY), Size(bw, bw * 0.6f), corner)
        }
    }

    // ---------- Stile: oscilloscopio ----------

    private fun DrawScope.drawWaveform(inp: SceneInputs, bass: Float, beat: Float) {
        val a = inp.analyzer
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val n = AudioAnalyzer.WAVE_POINTS
        val amp = h * 0.22f * (0.6f + bass * 0.8f) * inp.settings.sensitivity
        drawCenterArt(inp, w / 2f, cy, min(w, h) * 0.12f, beat)
        val brush = Brush.horizontalGradient(listOf(colors.c1, colors.c2, colors.c3, colors.c1))
        for (k in 2 downTo 0) {
            val ampK = amp * (1f - k * 0.28f) * (if (k % 2 == 1) -1f else 1f)
            for (i in 0 until n) {
                px[i] = w * i / (n - 1)
                // le estremità restano a zero per un'onda più pulita
                val edge = sin(PI.toFloat() * i / (n - 1))
                py[i] = cy + a.wave[(i + k * 7) % n] * ampK * edge
            }
            path.reset()
            path.moveTo(px[0], py[0])
            for (i in 1 until n - 1) path.quadraticTo(px[i], py[i], (px[i] + px[i + 1]) / 2f, (py[i] + py[i + 1]) / 2f)
            path.lineTo(px[n - 1], py[n - 1])
            if (k == 0 && inp.settings.glow) drawPath(path, brush, alpha = 0.3f, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            drawPath(path, brush, alpha = if (k == 0) 1f else 0.35f, style = Stroke((if (k == 0) 2.5f else 1.5f) * density, cap = StrokeCap.Round))
        }
    }

    // ---------- Stile: logo personalizzato con perimetro di frequenze ----------

    private fun DrawScope.drawLogo(inp: SceneInputs, bass: Float, beat: Float) {
        val logo = inp.logo ?: return drawCircleBars(inp, bass, beat)
        val s = inp.settings
        val shape = logo.shape
        val env = shape.envelope
        val m = min(env.size, logoVals.size)
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val minDim = min(w, h)
        val base = minDim * s.logoScale * (1f + bass * 0.05f + beat * 0.03f)
        val sc = minOf(base, w * 0.72f / shape.normW, h * 0.5f / shape.normH)
        val maxLen = minDim * 0.12f
        val off = 3.dp.toPx()
        val c = colors
        val ctr = Offset(cx, cy)
        val sweep = Brush.sweepGradient(listOf(c.c1, c.c2, c.c3, c.c1), ctr)

        // Frequenze distribuite in modo simmetrico (bassi in alto, alti in basso) e levigate lungo il bordo
        for (j in 0 until m) {
            val a = atan2(env.xs[j], -env.ys[j])
            val u = abs(a) / PI.toFloat() * (BANDS - 1)
            val i0 = u.toInt().coerceIn(0, BANDS - 1)
            val i1 = min(i0 + 1, BANDS - 1)
            val f = u - i0
            logoTmp[j] = display[i0] * (1f - f) + display[i1] * f
        }
        for (j in 0 until m) {
            var sum = 0f
            var wsum = 0f
            for (k in -4..4) {
                val wt = (5 - abs(k)).toFloat()
                sum += logoTmp[((j + k) % m + m) % m] * wt
                wsum += wt
            }
            val v = sum / wsum
            logoVals[j] = v
            logoEcho[j] += (v - logoEcho[j]) * min(1f, lastDt * 3f)
        }

        fun envX(j: Int, e: Float) = cx + env.xs[j] * sc + env.nx[j] * e
        fun envY(j: Int, e: Float) = cy + env.ys[j] * sc + env.ny[j] * e

        when (s.logoEdge) {
            LogoEdge.AURA -> {
                for (layer in 3 downTo 1) {
                    val k = 0.45f + 0.35f * layer
                    for (j in 0 until m) {
                        val e = off + logoVals[j] * maxLen * k
                        px[j] = envX(j, e); py[j] = envY(j, e)
                    }
                    smoothClosed(path, m)
                    drawPath(path, sweep, alpha = 0.10f + 0.07f * (3 - layer) + beat * 0.05f, blendMode = BlendMode.Plus)
                }
                if (s.glow) drawPath(path, sweep, alpha = 0.35f, style = Stroke(6.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(path, sweep, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
            }
            LogoEdge.WAVE -> {
                for (j in 0 until m) {
                    val e = off + logoEcho[j] * maxLen * 1.3f
                    px[j] = envX(j, e); py[j] = envY(j, e)
                }
                smoothClosed(path2, m)
                drawPath(path2, c.c2.copy(alpha = 0.35f), style = Stroke(1.2.dp.toPx()))
                for (j in 0 until m) {
                    val e = off + logoVals[j] * maxLen
                    px[j] = envX(j, e); py[j] = envY(j, e)
                }
                smoothClosed(path, m)
                drawPath(path, Brush.radialGradient(listOf(Color.Transparent, c.c1.copy(alpha = 0.22f)), center = ctr, radius = max(shape.normW, shape.normH) * sc), blendMode = BlendMode.Plus)
                if (s.glow) drawPath(path, sweep, alpha = 0.3f, style = Stroke(9.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(path, sweep, style = Stroke(2.2.dp.toPx(), join = StrokeJoin.Round))
            }
            LogoEdge.BARS -> {
                val spacingPx = env.spacing * sc
                val bw = (spacingPx * 0.5f).coerceIn(1.5f, 6.dp.toPx())
                val lift = 2.dp.toPx()
                for (j in 0 until m) {
                    val t = ((atan2(env.ys[j], env.xs[j]) / TAU) + 1f) % 1f
                    val cc = col(t)
                    val st = Offset(envX(j, off), envY(j, off))
                    val e = off + lift + logoVals[j] * maxLen
                    val en = Offset(envX(j, e), envY(j, e))
                    if (s.glow) drawLine(cc.copy(alpha = 0.25f), st, en, strokeWidth = bw * 2.6f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                    drawLine(cc, st, en, strokeWidth = bw, cap = StrokeCap.Round)
                }
            }
            LogoEdge.DOTS -> {
                val spacingPx = env.spacing * sc
                for (j in 0 until m step 2) {
                    val t = ((atan2(env.ys[j], env.xs[j]) / TAU) + 1f) % 1f
                    val e = off + logoVals[j] * maxLen * 0.9f
                    val rad = (spacingPx * 0.7f).coerceIn(1.5f, 6.dp.toPx()) * (0.5f + logoVals[j] * 1.2f)
                    drawCircle(col(t), rad, Offset(envX(j, e), envY(j, e)), blendMode = BlendMode.Plus)
                }
            }
        }

        // Il bordo della forma: una linea sottile e pulita che respira col battito
        for (j in 0 until m) { px[j] = envX(j, 0f); py[j] = envY(j, 0f) }
        smoothClosed(path, m)
        drawPath(path, Color.White.copy(alpha = (0.12f + beat * 0.4f).coerceAtMost(0.6f)), style = Stroke(1.dp.toPx()), blendMode = BlendMode.Plus)

        // Il logo, unico e pulito al centro
        val iw = shape.normW * sc
        val ih = shape.normH * sc
        if (s.glow) {
            val g = 1.04f + beat * 0.04f
            drawImage(
                image = logo.image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(logo.image.width, logo.image.height),
                dstOffset = IntOffset((cx - iw * g / 2f).roundToInt(), (cy - ih * g / 2f).roundToInt()),
                dstSize = IntSize((iw * g).roundToInt(), (ih * g).roundToInt()),
                colorFilter = ColorFilter.tint(c.c1.copy(alpha = 0.25f + 0.45f * beat)),
                blendMode = BlendMode.Plus,
                filterQuality = FilterQuality.High,
            )
        }
        drawImage(
            image = logo.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(logo.image.width, logo.image.height),
            dstOffset = IntOffset((cx - iw / 2f).roundToInt(), (cy - ih / 2f).roundToInt()),
            dstSize = IntSize(iw.roundToInt(), ih.roundToInt()),
            colorFilter = if (s.logoTinted) ColorFilter.tint(lerp(c.c1, Color.White, 0.25f)) else null,
            filterQuality = FilterQuality.High,
        )
    }
}
