package com.aurora.player.ui.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.aurora.player.audio.AudioAnalyzer.Companion.BANDS
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Tutti i livelli sovrapponibili, ognuno con il proprio piccolo stato. */
class LayerState {
    private val rnd = Random(7)

    // ======================= Spettro in basso =======================

    fun drawBars(ds: DrawScope, fx: Fx) = with(ds) {
        val w = size.width
        val h = size.height
        val n = BANDS * 2
        val left = w * 0.04f
        val slot = w * 0.92f / n
        val bw = slot * 0.62f
        val baseY = h * 0.84f
        val maxH = h * 0.2f
        val minH = 2.dp.toPx()
        for (i in 0 until n) {
            val idx = if (i < BANDS) BANDS - 1 - i else i - BANDS
            val v = fx.bands[idx]
            val hgt = minH + v * maxH
            val x = left + i * slot + (slot - bw) / 2f
            val c = fx.col(i.toFloat() / n)
            val corner = CornerRadius(bw / 2f)
            if (fx.glow) {
                drawRoundRect(c.copy(alpha = 0.2f), Offset(x - bw * 0.6f, baseY - hgt - bw * 0.6f), Size(bw * 2.2f, hgt + bw * 1.2f), CornerRadius(bw), blendMode = BlendMode.Plus)
            }
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(c, c.copy(alpha = 0.55f)), startY = baseY - hgt, endY = baseY),
                topLeft = Offset(x, baseY - hgt),
                size = Size(bw, hgt),
                cornerRadius = corner,
            )
            drawRoundRect(c.copy(alpha = 0.14f), Offset(x, baseY + minH), Size(bw, hgt * 0.25f), corner)
            val peakY = baseY - minH - fx.peaks[idx] * maxH - bw
            drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(x, peakY), Size(bw, bw * 0.6f), corner)
        }
    }

    // ======================= Oscilloscopio =======================

    fun drawScope(ds: DrawScope, fx: Fx) = with(ds) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val wave = fx.wave
        val n = wave.size
        val amp = h * 0.2f * (0.6f + fx.bass * 0.8f) * fx.sensitivity
        val brush = Brush.horizontalGradient(listOf(fx.colors.c1, fx.colors.c2, fx.colors.c3, fx.colors.c1))
        for (k in 2 downTo 0) {
            val ampK = amp * (1f - k * 0.28f) * (if (k % 2 == 1) -1f else 1f)
            for (i in 0 until n) {
                fx.px[i] = w * i / (n - 1)
                val edge = sin(PIF * i / (n - 1))
                fx.py[i] = cy + wave[(i + k * 7) % n] * ampK * edge
            }
            fx.smoothOpen(fx.path, n)
            if (k == 0 && fx.glow) drawPath(fx.path, brush, alpha = 0.3f, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            drawPath(fx.path, brush, alpha = if (k == 0) 1f else 0.35f, style = Stroke((if (k == 0) 2.5f else 1.5f) * density, cap = StrokeCap.Round))
        }
    }

    // ======================= Lissajous =======================

    fun drawVector(ds: DrawScope, fx: Fx) = with(ds) {
        val wave = fx.wave
        val n = wave.size
        val off = n / 4
        val cx = size.width / 2f
        val cy = size.height / 2f
        val amp = min(size.width, size.height) * 0.34f * (0.7f + fx.bass * 0.6f) * fx.sensitivity
        for (i in 0 until n) {
            val x = wave[i]
            val y = wave[(i + off) % n]
            fx.px[i] = cx + (x - y) * 0.707f * amp
            fx.py[i] = cy + (x + y) * 0.707f * amp
        }
        fx.smoothClosed(fx.path, n)
        val sweep = Brush.sweepGradient(listOf(fx.colors.c1, fx.colors.c2, fx.colors.c3, fx.colors.c1), Offset(cx, cy))
        if (fx.glow) drawPath(fx.path, sweep, alpha = 0.3f, style = Stroke(8.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(fx.path, sweep, alpha = 0.9f, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    }

    // ======================= Montagne (stile "Unknown Pleasures") =======================

    private val ridgeRows = 28
    private val ridgePts = 72
    private val ridges = FloatArray(ridgeRows * ridgePts)
    private var ridgeHead = 0
    private var ridgeTimer = 0f

    fun drawRidges(ds: DrawScope, fx: Fx) = with(ds) {
        ridgeTimer += fx.dt
        if (ridgeTimer >= 0.055f) {
            ridgeTimer = 0f
            ridgeHead = (ridgeHead + 1) % ridgeRows
            val base = ridgeHead * ridgePts
            for (j in 0 until ridgePts) {
                val u = j / (ridgePts - 1f)
                val m = 1f - abs(2f * u - 1f)
                val d = u - 0.5f
                val bell = exp(-(d * d) / 0.03f)
                ridges[base + j] = fx.band((1f - m) * 0.75f) * bell * (0.85f + 0.3f * rnd.nextFloat())
            }
        }
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val top = h * 0.46f
        val bottom = h * 0.97f
        val fill = Color(0xF2050508)
        val sw = 1.3.dp.toPx()
        for (k in 0 until ridgeRows) {
            val row = (ridgeHead + 1 + k) % ridgeRows
            val depth = k / (ridgeRows - 1f)
            val y = top + (bottom - top) * depth
            val half = w * (0.26f + 0.2f * depth)
            val amp = h * (0.08f + 0.12f * depth)
            val base = row * ridgePts
            for (j in 0 until ridgePts) {
                fx.px[j] = cx - half + 2f * half * j / (ridgePts - 1f)
                fx.py[j] = y - ridges[base + j] * amp
            }
            fx.smoothOpen(fx.path, ridgePts)
            fx.path.lineTo(fx.px[ridgePts - 1], y + sw * 2f)
            fx.path.lineTo(fx.px[0], y + sw * 2f)
            fx.path.close()
            drawPath(fx.path, fill)
            fx.smoothOpen(fx.path2, ridgePts)
            val c = fx.col(depth * 0.6f)
            if (fx.glow && k > ridgeRows - 6) drawPath(fx.path2, c.copy(alpha = 0.3f * depth), style = Stroke(sw * 4f), blendMode = BlendMode.Plus)
            drawPath(fx.path2, c.copy(alpha = 0.25f + 0.75f * depth), style = Stroke(sw))
        }
    }

    // ======================= Griglia synthwave =======================

    private var gridPhase = 0f

    fun drawGrid(ds: DrawScope, fx: Fx) = with(ds) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val hy = h * 0.62f
        gridPhase = (gridPhase + fx.dt * (0.35f + fx.bass * 1.8f + fx.beat * 1.2f)) % 1f
        val c1 = fx.colors.c1
        val c2 = fx.colors.c2
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = hy, endY = h), topLeft = Offset(0f, hy), size = Size(w, h - hy))
        val gh = h * 0.1f
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, c2.copy(alpha = 0.25f + 0.35f * fx.bass), Color.Transparent), startY = hy - gh, endY = hy + gh),
            topLeft = Offset(0f, hy - gh), size = Size(w, 2f * gh), blendMode = BlendMode.Plus,
        )
        // profilo di montagne dallo spettro
        val n = 96
        for (i in 0 until n) {
            val u = i / (n - 1f)
            val m = 1f - abs(2f * u - 1f)
            fx.px[i] = u * w
            fx.py[i] = hy - fx.band(m * 0.7f) * h * 0.1f * (0.35f + 0.65f * (1f - m))
        }
        fx.smoothOpen(fx.path, n)
        if (fx.glow) drawPath(fx.path, c2.copy(alpha = 0.35f), style = Stroke(6.dp.toPx()), blendMode = BlendMode.Plus)
        drawPath(fx.path, c2, style = Stroke(1.6.dp.toPx()))
        val sw = 1.2.dp.toPx()
        val lines = 12
        for (i in 0 until lines) {
            val f = (i + gridPhase) / lines
            val y = hy + (h - hy) * f * f * 1.05f
            val a = (f * 0.9f).coerceIn(0f, 0.9f)
            if (fx.glow) drawLine(c1.copy(alpha = a * 0.3f), Offset(0f, y), Offset(w, y), strokeWidth = sw * 4f, blendMode = BlendMode.Plus)
            drawLine(c1.copy(alpha = a), Offset(0f, y), Offset(w, y), strokeWidth = sw * (0.6f + f))
        }
        for (j in -11..11) {
            val xb = cx + j * w * 0.12f
            drawLine(c1.copy(alpha = 0.45f), Offset(cx + j * w * 0.012f, hy), Offset(xb + (xb - cx) * 1.5f, h), strokeWidth = sw)
        }
    }

    // ======================= Tunnel =======================

    private val tunnelZ = FloatArray(16) { 0.1f + it * 0.1f }
    private val order = IntArray(16)

    fun drawTunnel(ds: DrawScope, fx: Fx) = with(ds) {
        val n = tunnelZ.size
        val span = n * 0.1f
        val speed = 0.25f + fx.bass * 1.4f + fx.beat * 1.0f
        for (i in 0 until n) {
            tunnelZ[i] -= fx.dt * speed
            if (tunnelZ[i] < 0.06f) tunnelZ[i] += span
        }
        for (i in 0 until n) order[i] = i
        for (i in 1 until n) {
            val k = order[i]
            var j = i - 1
            while (j >= 0 && tunnelZ[order[j]] < tunnelZ[k]) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = k
        }
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = min(size.width, size.height)
        val maxDim = max(size.width, size.height)
        val sides = 6
        for (oi in 0 until n) {
            val z = tunnelZ[order[oi]]
            val r = minDim * 0.09f / z
            if (r > maxDim * 1.2f) continue
            val rot = fx.time * 0.25f + z * 1.4f
            for (sd in 0 until sides) {
                val a = rot + sd * TAU / sides
                val v = fx.bands[min(sd * 9 + 2, BANDS - 1)]
                val rr = r * (1f + v * 0.12f)
                fx.px[sd] = cx + cos(a) * rr
                fx.py[sd] = cy + sin(a) * rr
            }
            fx.path.reset()
            fx.path.moveTo(fx.px[0], fx.py[0])
            for (sd in 1 until sides) fx.path.lineTo(fx.px[sd], fx.py[sd])
            fx.path.close()
            val fade = ((span - z) / span).coerceIn(0f, 1f) * (z * 6f).coerceIn(0f, 1f)
            val sw = (1.2.dp.toPx() / z * 0.25f).coerceIn(0.8f, 9.dp.toPx())
            val c = fx.col(z / span + fx.time * 0.02f)
            if (fx.glow) drawPath(fx.path, c.copy(alpha = fade * 0.3f), style = Stroke(sw * 3f, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            drawPath(fx.path, c.copy(alpha = fade * 0.9f), style = Stroke(sw, join = StrokeJoin.Round))
        }
    }

    // ======================= Iperspazio (stelle in corsa) =======================

    private val starMax = 320
    private val sx = FloatArray(starMax)
    private val sy = FloatArray(starMax)
    private val sz = FloatArray(starMax)
    private var starsReady = false

    private fun respawnStar(i: Int, z: Float) {
        sx[i] = rnd.nextFloat() * 2f - 1f
        sy[i] = rnd.nextFloat() * 2f - 1f
        sz[i] = z.coerceAtLeast(0.05f)
    }

    fun drawWarp(ds: DrawScope, fx: Fx) = with(ds) {
        if (!starsReady) {
            for (i in 0 until starMax) respawnStar(i, rnd.nextFloat())
            starsReady = true
        }
        val count = if (fx.eco) 110 else starMax
        val speed = 0.12f + fx.bass * 0.8f + fx.beat * 0.7f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val f = min(size.width, size.height) * 0.5f
        val maxW = 2.6.dp.toPx()
        for (i in 0 until count) {
            val pz = sz[i]
            sz[i] -= fx.dt * speed
            if (sz[i] <= 0.03f) { respawnStar(i, 1f); continue }
            val x1 = cx + sx[i] / sz[i] * f
            val y1 = cy + sy[i] / sz[i] * f
            if (x1 < -60f || x1 > size.width + 60f || y1 < -60f || y1 > size.height + 60f) { respawnStar(i, 1f); continue }
            val x0 = cx + sx[i] / pz * f
            val y0 = cy + sy[i] / pz * f
            val k = (1f - sz[i]).coerceIn(0f, 1f)
            drawLine(
                lerp(Color.White, fx.col(i.toFloat() / count), 0.4f).copy(alpha = k),
                Offset(x0, y0), Offset(x1, y1),
                strokeWidth = (k * maxW).coerceAtLeast(0.8f),
                cap = StrokeCap.Round,
            )
        }
    }

    // ======================= Aurora boreale =======================

    private val auroraBottom = FloatArray(64)

    fun drawAurora(ds: DrawScope, fx: Fx) = with(ds) {
        val w = size.width
        val h = size.height
        val n = 48
        for (r in 0 until 3) {
            val baseY = h * (0.14f + r * 0.09f)
            val c = fx.col(r * 0.33f + 0.1f)
            for (i in 0 until n) {
                val t = i / (n - 1f)
                fx.px[i] = -0.05f * w + t * 1.1f * w
                fx.py[i] = baseY +
                    sin(t * 5f + fx.time * (0.35f + r * 0.12f) + r * 1.7f) * h * 0.045f +
                    sin(t * 13f - fx.time * 0.7f + r * 2.3f) * h * 0.012f
                auroraBottom[i] = fx.py[i] + h * (0.05f + 0.16f * fx.band(0.08f + t * 0.5f) * (0.6f + fx.mid))
            }
            fx.path.reset()
            fx.path.moveTo(fx.px[0], fx.py[0])
            for (i in 1 until n) fx.path.lineTo(fx.px[i], fx.py[i])
            for (i in n - 1 downTo 0) fx.path.lineTo(fx.px[i], auroraBottom[i])
            fx.path.close()
            val a = (0.22f + 0.35f * fx.mid + 0.15f * fx.beat).coerceAtMost(0.7f)
            drawPath(
                fx.path,
                Brush.verticalGradient(listOf(c.copy(alpha = a), c.copy(alpha = a * 0.35f), Color.Transparent), startY = baseY - h * 0.05f, endY = baseY + h * 0.25f),
                blendMode = BlendMode.Plus,
            )
        }
    }

    // ======================= Orbite =======================

    private val orbitAng = FloatArray(3)
    private val tilts = floatArrayOf(0.35f, -0.55f, 1.25f)
    private val counts = intArrayOf(3, 4, 6)

    fun drawOrbits(ds: DrawScope, fx: Fx) = with(ds) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ctr = Offset(cx, cy)
        val minDim = min(size.width, size.height)
        val groups = floatArrayOf(fx.bass, fx.mid, fx.treble)
        for (o in 0 until 3) {
            val rx = minDim * (0.28f + o * 0.06f) * (1f + fx.bass * 0.04f)
            val ry = rx * 0.3f
            val dir = if (o % 2 == 0) 1f else -1f
            orbitAng[o] += fx.dt * (0.35f + groups[o] * 2.2f) * dir
            val ct = cos(tilts[o])
            val st = sin(tilts[o])
            val c = fx.col(o * 0.33f)
            rotate(Math.toDegrees(tilts[o].toDouble()).toFloat(), ctr) {
                drawOval(c.copy(alpha = 0.18f), topLeft = Offset(cx - rx, cy - ry), size = Size(2f * rx, 2f * ry), style = Stroke(1.dp.toPx()))
            }
            for (s in 0 until counts[o]) {
                for (k in 7 downTo 0) {
                    val a = orbitAng[o] + s * TAU / counts[o] - dir * k * 0.055f
                    val ex = cos(a) * rx
                    val ey = sin(a) * ry
                    val p = Offset(cx + ex * ct - ey * st, cy + ex * st + ey * ct)
                    val depth = 0.75f + 0.25f * sin(a)
                    val rad = 2.4.dp.toPx() * (1f + groups[o] * 1.6f) * depth * (1f - k * 0.1f)
                    if (k == 0) {
                        if (fx.glow) drawCircle(c.copy(alpha = 0.3f), rad * 3f, p, blendMode = BlendMode.Plus)
                        drawCircle(lerp(c, Color.White, 0.4f), rad, p, blendMode = BlendMode.Plus)
                    } else {
                        drawCircle(c.copy(alpha = (1f - k / 8f) * 0.45f), rad, p, blendMode = BlendMode.Plus)
                    }
                }
            }
        }
    }

    // ======================= Fulmini =======================

    private val boltPts = 24
    private val boltX = Array(4) { FloatArray(boltPts) }
    private val boltY = Array(4) { FloatArray(boltPts) }
    private val boltLife = FloatArray(4) { -1f }

    private fun spawnBolt(b: Int, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val ang = rnd.nextFloat() * TAU
        val r0 = min(w, h) * 0.2f
        val x0 = cx + cos(ang) * r0
        val y0 = cy + sin(ang) * r0
        val far = max(w, h) * 0.75f
        val dx = cx + cos(ang) * far - x0
        val dy = cy + sin(ang) * far - y0
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val nx = -dy / len
        val ny = dx / len
        var off = 0f
        for (i in 0 until boltPts) {
            val t = i / (boltPts - 1f)
            off += (rnd.nextFloat() * 2f - 1f) * len * 0.035f
            val env = sin(PIF * t)
            boltX[b][i] = x0 + dx * t + nx * off * env
            boltY[b][i] = y0 + dy * t + ny * off * env
        }
        boltLife[b] = 0f
    }

    fun drawLightning(ds: DrawScope, fx: Fx) = with(ds) {
        if (fx.beatHit && fx.bass > 0.4f) {
            val slot = boltLife.indexOfFirst { it < 0f }
            if (slot >= 0) spawnBolt(slot, size.width, size.height)
        }
        for (b in 0 until 4) {
            if (boltLife[b] < 0f) continue
            boltLife[b] += fx.dt
            val k = boltLife[b] / 0.32f
            if (k >= 1f) { boltLife[b] = -1f; continue }
            val a = 1f - k
            fx.path.reset()
            fx.path.moveTo(boltX[b][0], boltY[b][0])
            for (i in 1 until boltPts) fx.path.lineTo(boltX[b][i], boltY[b][i])
            val c = fx.colors.c2
            drawPath(fx.path, c.copy(alpha = a * 0.35f), style = Stroke(9.dp.toPx() * a + 1f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            drawPath(fx.path, lerp(c, Color.White, 0.7f).copy(alpha = a), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
