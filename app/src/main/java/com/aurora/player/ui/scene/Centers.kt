package com.aurora.player.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aurora.player.audio.AudioAnalyzer.Companion.BANDS
import com.aurora.player.settings.LogoEdge
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------- Copertina tonda centrale ----------------

internal fun DrawScope.drawCenterArt(fx: Fx, inp: SceneInputs, cx: Float, cy: Float, r: Float) {
    val c = fx.colors
    val ctr = Offset(cx, cy)
    val ringW = 3.dp.toPx()
    if (fx.glow) {
        drawCircle(c.c1.copy(alpha = 0.25f + 0.35f * fx.beat), r + ringW + fx.beat * 14.dp.toPx(), ctr, style = Stroke(ringW * 2f), blendMode = BlendMode.Plus)
    }
    val art = inp.art?.image
    if (art != null) {
        fx.clip.reset()
        fx.clip.addOval(Rect(ctr, r))
        clipPath(fx.clip) {
            rotate(fx.spin, ctr) {
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

internal fun DrawScope.drawArtOnly(fx: Fx, inp: SceneInputs) {
    val minDim = min(size.width, size.height)
    drawCenterArt(fx, inp, size.width / 2f, size.height / 2f, minDim * 0.21f * (1f + fx.bass * 0.05f + fx.beat * 0.03f))
}

// ---------------- Anello di barre ----------------

internal fun DrawScope.drawCircleBars(fx: Fx, inp: SceneInputs) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val minDim = min(size.width, size.height)
    val baseR = minDim * 0.2f * (1f + fx.bass * 0.07f + fx.beat * 0.03f)
    val maxLen = minDim * 0.22f
    val n = BANDS * 2
    val barW = (TAU * baseR / n * 0.55f).coerceAtLeast(2f)
    val gap = 6.dp.toPx()
    val lift = 2.dp.toPx()
    drawCenterArt(fx, inp, cx, cy, baseR - lift)
    for (i in 0 until n) {
        val v = fx.mirrored(fx.bands, i, n)
        val ang = -PIF / 2f + i * TAU / n
        val ca = cos(ang)
        val sa = sin(ang)
        val r1 = baseR + gap
        val r2 = r1 + lift + v * maxLen
        val c = fx.col(i.toFloat() / n)
        val st = Offset(cx + ca * r1, cy + sa * r1)
        val en = Offset(cx + ca * r2, cy + sa * r2)
        if (fx.glow) drawLine(c.copy(alpha = 0.28f), st, en, strokeWidth = barW * 2.8f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        drawLine(c, st, en, strokeWidth = barW, cap = StrokeCap.Round)
        val pr = r1 + lift + fx.mirrored(fx.peaks, i, n) * maxLen + barW * 1.6f
        drawCircle(c.copy(alpha = 0.85f), barW * 0.45f, Offset(cx + ca * pr, cy + sa * pr))
    }
}

// ---------------- Onda circolare ----------------

private fun Fx.radial(arr: FloatArray, n: Int, cx: Float, cy: Float, r0: Float, amp: Float) {
    for (i in 0 until n) {
        val v = mirrored(arr, i, n)
        val ang = -PIF / 2f + i * TAU / n
        val r = r0 + v * amp
        px[i] = cx + cos(ang) * r
        py[i] = cy + sin(ang) * r
    }
}

internal fun DrawScope.drawCircleWave(fx: Fx, inp: SceneInputs) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val ctr = Offset(cx, cy)
    val minDim = min(size.width, size.height)
    val baseR = minDim * 0.2f * (1f + fx.bass * 0.08f + fx.beat * 0.04f)
    val maxLen = minDim * 0.2f
    val n = BANDS * 2
    val r0 = baseR + 4.dp.toPx()
    val c = fx.colors
    fx.radial(fx.bands, n, cx, cy, r0, maxLen)
    fx.smoothClosed(fx.path, n)
    fx.radial(fx.echo, n, cx, cy, r0, maxLen * 1.3f)
    fx.smoothClosed(fx.path2, n)
    val sweep = Brush.sweepGradient(listOf(c.c1, c.c2, c.c3, c.c1), ctr)
    drawPath(fx.path, Brush.radialGradient(listOf(Color.Transparent, c.c1.copy(alpha = 0.25f)), center = ctr, radius = baseR + maxLen))
    drawCenterArt(fx, inp, cx, cy, baseR * 0.82f)
    drawPath(fx.path2, c.c2.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx()))
    if (fx.glow) drawPath(fx.path, sweep, alpha = 0.3f, style = Stroke(10.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    drawPath(fx.path, sweep, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
}

// ---------------- Sfera liquida ----------------

internal fun DrawScope.drawOrb(fx: Fx, @Suppress("UNUSED_PARAMETER") inp: SceneInputs) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val ctr = Offset(cx, cy)
    val minDim = min(size.width, size.height)
    val r0 = minDim * 0.17f * (1f + fx.bass * 0.18f + fx.beat * 0.06f)
    val c = fx.colors
    val n = 160
    drawCircle(
        Brush.radialGradient(listOf(c.c1.copy(alpha = 0.22f + 0.25f * fx.bass), Color.Transparent), center = ctr, radius = r0 * 2.6f),
        r0 * 2.6f, ctr, blendMode = BlendMode.Plus,
    )
    for (layer in 0 until 3) {
        val ph = layer * 2.1f
        val speed = 1f + layer * 0.35f
        for (i in 0 until n) {
            val a = i * TAU / n
            val u = abs(2f * (i.toFloat() / n) - 1f)
            val v = fx.band((1f - u) * 0.7f)
            val wob = sin(a * 3f + fx.time * 1.1f * speed + ph) * 0.06f +
                sin(a * 5f - fx.time * 0.7f * speed + ph * 1.7f) * 0.04f +
                sin(a * 2f + fx.time * 0.4f + ph * 0.5f) * 0.05f
            val r = r0 * (1f + wob * (0.6f + fx.mid * 1.8f) + v * 0.25f) * (1f - layer * 0.13f)
            fx.px[i] = cx + cos(a) * r
            fx.py[i] = cy + sin(a) * r
        }
        fx.smoothClosed(fx.path, n)
        val lc = when (layer) { 0 -> c.c1; 1 -> c.c2; else -> c.c3 }
        val hl = Offset(cx - r0 * 0.25f, cy - r0 * 0.3f)
        drawPath(
            fx.path,
            Brush.radialGradient(listOf(lerp(lc, Color.White, 0.35f), lc, lc.copy(alpha = 0.2f)), center = hl, radius = r0 * 1.5f),
            alpha = 0.55f,
            blendMode = BlendMode.Plus,
        )
        if (layer == 0) {
            if (fx.glow) drawPath(fx.path, lc, alpha = 0.35f, style = Stroke(9.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            drawPath(fx.path, lerp(lc, Color.White, 0.5f), alpha = 0.8f, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
    }
    val hl = Offset(cx - r0 * 0.32f, cy - r0 * 0.38f)
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f + 0.3f * fx.beat), Color.Transparent), center = hl, radius = r0 * 0.6f),
        r0 * 0.6f, hl, blendMode = BlendMode.Plus,
    )
}

// ---------------- Mandala ----------------

internal fun DrawScope.drawMandala(fx: Fx, inp: SceneInputs) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val ctr = Offset(cx, cy)
    val minDim = min(size.width, size.height)
    drawCenterArt(fx, inp, cx, cy, minDim * 0.1f * (1f + fx.bass * 0.06f))
    val n = 240
    val petals = intArrayOf(6, 9, 12)
    for (ring in 0 until 3) {
        val k = petals[ring]
        val r0 = minDim * (0.12f + ring * 0.07f) * (1f + fx.bass * 0.05f)
        val amp = minDim * (0.07f + ring * 0.015f)
        val rot = fx.time * (if (ring % 2 == 0) 0.12f else -0.09f) * (1f + ring * 0.3f)
        val bandStart = ring * 0.3f
        for (i in 0 until n) {
            val a = i * TAU / n
            val local = (i.toFloat() * k / n) % 1f
            val u = 1f - abs(2f * local - 1f)
            val v = fx.band(bandStart + u * 0.25f)
            val petal = u * u * (3f - 2f * u)
            val r = r0 + petal * amp * (0.25f + v * 1.1f)
            fx.px[i] = cx + cos(a + rot) * r
            fx.py[i] = cy + sin(a + rot) * r
        }
        fx.smoothClosed(fx.path, n)
        val t0 = ring * 0.33f
        val brush = Brush.sweepGradient(listOf(fx.col(t0), fx.col(t0 + 0.33f), fx.col(t0 + 0.66f), fx.col(t0)), ctr)
        drawPath(fx.path, brush, alpha = 0.1f + 0.12f * fx.beat, blendMode = BlendMode.Plus)
        if (fx.glow) drawPath(fx.path, brush, alpha = 0.3f, style = Stroke(7.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(fx.path, brush, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
        // punti sulle punte dei petali
        for (p in 0 until k) {
            val a = rot + (p + 0.5f) * TAU / k
            val v = fx.band(bandStart + 0.25f)
            val r = r0 + amp * (0.25f + v * 1.1f) + 6.dp.toPx()
            drawCircle(fx.col(t0 + p.toFloat() / k), (1.5f + 2.5f * v).dp.toPx(), Offset(cx + cos(a) * r, cy + sin(a) * r), blendMode = BlendMode.Plus)
        }
    }
}

// ---------------- Logo con involucro di frequenze ----------------

internal fun DrawScope.drawLogo(fx: Fx, inp: SceneInputs) {
    val logo = inp.logo ?: return drawCircleBars(fx, inp)
    val s = inp.settings
    val shape = logo.shape
    val env = shape.envelope
    val m = min(env.size, fx.logoVals.size)
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val minDim = min(w, h)
    val base = minDim * s.logoScale * (1f + fx.bass * 0.05f + fx.beat * 0.03f)
    val sc = minOf(base, w * 0.72f / shape.normW, h * 0.5f / shape.normH)
    val maxLen = minDim * 0.12f
    val off = 3.dp.toPx()
    val c = fx.colors
    val ctr = Offset(cx, cy)
    val sweep = Brush.sweepGradient(listOf(c.c1, c.c2, c.c3, c.c1), ctr)

    for (j in 0 until m) {
        val a = atan2(env.xs[j], -env.ys[j])
        fx.logoTmp[j] = fx.band(abs(a) / PIF)
    }
    for (j in 0 until m) {
        var sum = 0f
        var wsum = 0f
        for (k in -4..4) {
            val wt = (5 - abs(k)).toFloat()
            sum += fx.logoTmp[((j + k) % m + m) % m] * wt
            wsum += wt
        }
        val v = sum / wsum
        fx.logoVals[j] = v
        fx.logoEcho[j] += (v - fx.logoEcho[j]) * min(1f, fx.dt * 3f)
    }

    fun envX(j: Int, e: Float) = cx + env.xs[j] * sc + env.nx[j] * e
    fun envY(j: Int, e: Float) = cy + env.ys[j] * sc + env.ny[j] * e

    when (s.logoEdge) {
        LogoEdge.AURA -> {
            for (layer in 3 downTo 1) {
                val k = 0.45f + 0.35f * layer
                for (j in 0 until m) {
                    val e = off + fx.logoVals[j] * maxLen * k
                    fx.px[j] = envX(j, e); fx.py[j] = envY(j, e)
                }
                fx.smoothClosed(fx.path, m)
                drawPath(fx.path, sweep, alpha = 0.10f + 0.07f * (3 - layer) + fx.beat * 0.05f, blendMode = BlendMode.Plus)
            }
            if (fx.glow) drawPath(fx.path, sweep, alpha = 0.35f, style = Stroke(6.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            drawPath(fx.path, sweep, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
        LogoEdge.WAVE -> {
            for (j in 0 until m) {
                val e = off + fx.logoEcho[j] * maxLen * 1.3f
                fx.px[j] = envX(j, e); fx.py[j] = envY(j, e)
            }
            fx.smoothClosed(fx.path2, m)
            drawPath(fx.path2, c.c2.copy(alpha = 0.35f), style = Stroke(1.2.dp.toPx()))
            for (j in 0 until m) {
                val e = off + fx.logoVals[j] * maxLen
                fx.px[j] = envX(j, e); fx.py[j] = envY(j, e)
            }
            fx.smoothClosed(fx.path, m)
            drawPath(fx.path, Brush.radialGradient(listOf(Color.Transparent, c.c1.copy(alpha = 0.22f)), center = ctr, radius = max(shape.normW, shape.normH) * sc), blendMode = BlendMode.Plus)
            if (fx.glow) drawPath(fx.path, sweep, alpha = 0.3f, style = Stroke(9.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            drawPath(fx.path, sweep, style = Stroke(2.2.dp.toPx(), join = StrokeJoin.Round))
        }
        LogoEdge.BARS -> {
            val spacingPx = env.spacing * sc
            val bw = (spacingPx * 0.5f).coerceIn(1.5f, 6.dp.toPx())
            val lift = 2.dp.toPx()
            for (j in 0 until m) {
                val t = ((atan2(env.ys[j], env.xs[j]) / TAU) + 1f) % 1f
                val cc = fx.col(t)
                val st = Offset(envX(j, off), envY(j, off))
                val e = off + lift + fx.logoVals[j] * maxLen
                val en = Offset(envX(j, e), envY(j, e))
                if (fx.glow) drawLine(cc.copy(alpha = 0.25f), st, en, strokeWidth = bw * 2.6f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                drawLine(cc, st, en, strokeWidth = bw, cap = StrokeCap.Round)
            }
        }
        LogoEdge.DOTS -> {
            val spacingPx = env.spacing * sc
            for (j in 0 until m step 2) {
                val t = ((atan2(env.ys[j], env.xs[j]) / TAU) + 1f) % 1f
                val e = off + fx.logoVals[j] * maxLen * 0.9f
                val rad = (spacingPx * 0.7f).coerceIn(1.5f, 6.dp.toPx()) * (0.5f + fx.logoVals[j] * 1.2f)
                drawCircle(fx.col(t), rad, Offset(envX(j, e), envY(j, e)), blendMode = BlendMode.Plus)
            }
        }
    }

    for (j in 0 until m) { fx.px[j] = envX(j, 0f); fx.py[j] = envY(j, 0f) }
    fx.smoothClosed(fx.path, m)
    drawPath(fx.path, Color.White.copy(alpha = (0.12f + fx.beat * 0.4f).coerceAtMost(0.6f)), style = Stroke(1.dp.toPx()), blendMode = BlendMode.Plus)

    val iw = shape.normW * sc
    val ih = shape.normH * sc
    if (fx.glow) {
        val g = 1.04f + fx.beat * 0.04f
        drawImage(
            image = logo.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(logo.image.width, logo.image.height),
            dstOffset = IntOffset((cx - iw * g / 2f).roundToInt(), (cy - ih * g / 2f).roundToInt()),
            dstSize = IntSize((iw * g).roundToInt(), (ih * g).roundToInt()),
            colorFilter = ColorFilter.tint(c.c1.copy(alpha = 0.25f + 0.45f * fx.beat)),
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
