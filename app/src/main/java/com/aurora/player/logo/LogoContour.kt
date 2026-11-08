package com.aurora.player.logo

import android.graphics.Bitmap
import com.aurora.player.settings.LogoHalo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Contorno chiuso, campionato a passo costante, con normali verso l'esterno (coordinate normalizzate). */
class Contour(
    val xs: FloatArray,
    val ys: FloatArray,
    val nx: FloatArray,
    val ny: FloatArray,
    val spacing: Float,
) {
    val size: Int get() = xs.size
}

/**
 * Coordinate normalizzate centrate in (0,0); 1.0 = lato più lungo del logo.
 * [envelope] è UNA sola forma morbida che avvolge tutto il logo (lettere comprese).
 */
class LogoShape(val envelope: Contour, val normW: Float, val normH: Float)

/**
 * Calcola l'"involucro" del logo: unisce tutte le parti (lettere, dettagli, buchi)
 * in una sola sagoma liscia a distanza costante dal logo. Le frequenze partono da lì,
 * così il logo resta un blocco pulito al centro e l'effetto lo circonda in modo uniforme.
 */
object LogoContourExtractor {
    private val DX = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    private val DY = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

    fun extract(src: Bitmap, halo: LogoHalo, points: Int = 240): LogoShape? {
        val maxDim = 200
        val scale = maxDim.toFloat() / max(src.width, src.height)
        val sw = max(1, (src.width * scale).roundToInt())
        val sh = max(1, (src.height * scale).roundToInt())
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val small = Bitmap.createScaledBitmap(soft, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)

        val pad = 64
        val w = sw + pad * 2
        val h = sh + pad * 2
        val mask = BooleanArray(w * h)
        var any = false
        for (y in 0 until sh) for (x in 0 until sw) {
            if ((px[y * sw + x] ushr 24) >= 100) {
                mask[(y + pad) * w + x + pad] = true
                any = true
            }
        }
        if (!any) return null
        val s = max(sw, sh).toFloat()
        val cxPix = pad + sw / 2f
        val cyPix = pad + sh / 2f

        if (halo == LogoHalo.CIRCLE) return circleShape(mask, w, h, cxPix, cyPix, s, sw, sh, points)

        val dist = distanceTransform(mask, w, h)
        var haloPx = max(3, (halo.factor * maxDim).roundToInt())
        var env = BooleanArray(0)
        var labels = IntArray(0)
        var best = -1
        var bestStart = 0
        var bestCount = 0
        for (attempt in 0 until 4) {
            env = fillHoles(BooleanArray(w * h) { dist[it] <= haloPx * 3 }, w, h)
            val res = largestComponent(env, w, h)
            labels = res.labels
            best = res.label
            bestStart = res.start
            bestCount = res.count
            if (res.components <= 1 || haloPx > pad - 6) break
            haloPx = min(pad - 6, (haloPx * 1.7f).roundToInt()) // unisci le parti lontane
        }
        if (best < 0) return null

        val pts = trace(labels, w, h, best, bestStart, bestCount)
        if (pts.size < 16) return null
        val xs = FloatArray(pts.size) { (pts[it] % w).toFloat() + 0.5f }
        val ys = FloatArray(pts.size) { (pts[it] / w).toFloat() + 0.5f }
        repeat(6) { smooth(xs, 3); smooth(ys, 3) }
        val len = perimeter(xs, ys)
        val (rx, ry) = resample(xs, ys, points)
        repeat(2) { smooth(rx, 2); smooth(ry, 2) }
        val (nx, ny) = normals(rx, ry, 3)
        orientOutward(rx, ry, nx, ny, env, w, h)
        for (i in 0 until points) {
            rx[i] = (rx[i] - cxPix) / s
            ry[i] = (ry[i] - cyPix) / s
        }
        return LogoShape(Contour(rx, ry, nx, ny, len / points / s), sw / s, sh / s)
    }

    private fun circleShape(mask: BooleanArray, w: Int, h: Int, cx: Float, cy: Float, s: Float, sw: Int, sh: Int, n: Int): LogoShape {
        var maxR = 0f
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y * w + x]) continue
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            val r = sqrt(dx * dx + dy * dy)
            if (r > maxR) maxR = r
        }
        val r = (maxR + s * 0.05f) / s
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val nx = FloatArray(n)
        val ny = FloatArray(n)
        for (i in 0 until n) {
            val a = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / n
            nx[i] = cos(a); ny[i] = sin(a)
            xs[i] = nx[i] * r; ys[i] = ny[i] * r
        }
        return LogoShape(Contour(xs, ys, nx, ny, 2f * PI.toFloat() * r / n), sw / s, sh / s)
    }

    /** Distanza (chamfer 3-4) dal pixel pieno più vicino. */
    private fun distanceTransform(mask: BooleanArray, w: Int, h: Int): IntArray {
        val inf = 1_000_000
        val d = IntArray(w * h) { if (mask[it]) 0 else inf }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (d[i] == 0) continue
            var m = d[i]
            if (x > 0) m = min(m, d[i - 1] + 3)
            if (y > 0) {
                m = min(m, d[i - w] + 3)
                if (x > 0) m = min(m, d[i - w - 1] + 4)
                if (x < w - 1) m = min(m, d[i - w + 1] + 4)
            }
            d[i] = m
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (d[i] == 0) continue
            var m = d[i]
            if (x < w - 1) m = min(m, d[i + 1] + 3)
            if (y < h - 1) {
                m = min(m, d[i + w] + 3)
                if (x < w - 1) m = min(m, d[i + w + 1] + 4)
                if (x > 0) m = min(m, d[i + w - 1] + 4)
            }
            d[i] = m
        }
        return d
    }

    /** Riempie i buchi interni (es. dentro una "O"): resta solo la sagoma esterna. */
    private fun fillHoles(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val outside = BooleanArray(w * h)
        val q = IntArray(w * h)
        var head = 0
        var tail = 0
        fun push(i: Int) {
            if (!m[i] && !outside[i]) { outside[i] = true; q[tail++] = i }
        }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (head < tail) {
            val p = q[head++]
            val x = p % w
            val y = p / w
            if (x > 0) push(p - 1)
            if (x < w - 1) push(p + 1)
            if (y > 0) push(p - w)
            if (y < h - 1) push(p + w)
        }
        return BooleanArray(w * h) { !outside[it] }
    }

    private class Components(val labels: IntArray, val label: Int, val start: Int, val count: Int, val components: Int)

    private fun largestComponent(m: BooleanArray, w: Int, h: Int): Components {
        val labels = IntArray(w * h)
        val q = IntArray(w * h)
        var label = 0
        var best = -1
        var bestStart = 0
        var bestCount = 0
        for (i in 0 until w * h) {
            if (!m[i] || labels[i] != 0) continue
            label++
            var head = 0
            var tail = 0
            q[tail++] = i
            labels[i] = label
            while (head < tail) {
                val p = q[head++]
                val x = p % w
                val y = p / w
                if (x > 0 && m[p - 1] && labels[p - 1] == 0) { labels[p - 1] = label; q[tail++] = p - 1 }
                if (x < w - 1 && m[p + 1] && labels[p + 1] == 0) { labels[p + 1] = label; q[tail++] = p + 1 }
                if (y > 0 && m[p - w] && labels[p - w] == 0) { labels[p - w] = label; q[tail++] = p - w }
                if (y < h - 1 && m[p + w] && labels[p + w] == 0) { labels[p + w] = label; q[tail++] = p + w }
            }
            if (tail > bestCount) { bestCount = tail; best = label; bestStart = i }
        }
        return Components(labels, best, bestStart, bestCount, label)
    }

    private fun trace(labels: IntArray, w: Int, h: Int, label: Int, start: Int, count: Int): IntArray {
        fun inside(x: Int, y: Int) = x >= 0 && y >= 0 && x < w && y < h && labels[y * w + x] == label
        val pts = ArrayList<Int>()
        val sx = start % w
        val sy = start / w
        var cx = sx
        var cy = sy
        var back = 0
        pts.add(start)
        val maxPts = count * 4 + 16
        while (pts.size < maxPts) {
            var found = -1
            for (k in 1..8) {
                val d = (back + k) % 8
                if (inside(cx + DX[d], cy + DY[d])) { found = d; break }
            }
            if (found < 0) break
            val nxp = cx + DX[found]
            val nyp = cy + DY[found]
            if (cx == sx && cy == sy && pts.size > 1 && nyp * w + nxp == pts[1]) break
            val prev = (found + 7) % 8
            back = dirIndex(cx + DX[prev] - nxp, cy + DY[prev] - nyp)
            cx = nxp
            cy = nyp
            pts.add(cy * w + cx)
        }
        if (pts.size > 2 && pts.last() == start) pts.removeAt(pts.size - 1)
        return pts.toIntArray()
    }

    private fun dirIndex(dx: Int, dy: Int): Int {
        for (i in 0 until 8) if (DX[i] == dx && DY[i] == dy) return i
        return 0
    }

    private fun smooth(a: FloatArray, r: Int) {
        val n = a.size
        val src = a.copyOf()
        for (i in 0 until n) {
            var sum = 0f
            for (k in -r..r) sum += src[((i + k) % n + n) % n]
            a[i] = sum / (2 * r + 1)
        }
    }

    private fun perimeter(xs: FloatArray, ys: FloatArray): Float {
        var l = 0f
        for (i in xs.indices) {
            val j = (i + 1) % xs.size
            val dx = xs[j] - xs[i]
            val dy = ys[j] - ys[i]
            l += sqrt(dx * dx + dy * dy)
        }
        return l
    }

    private fun resample(xs: FloatArray, ys: FloatArray, m: Int): Pair<FloatArray, FloatArray> {
        val n = xs.size
        val cum = FloatArray(n + 1)
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = xs[j] - xs[i]
            val dy = ys[j] - ys[i]
            cum[i + 1] = cum[i] + sqrt(dx * dx + dy * dy)
        }
        val total = cum[n]
        val ox = FloatArray(m)
        val oy = FloatArray(m)
        var seg = 0
        for (k in 0 until m) {
            val d = total * k / m
            while (seg < n - 1 && cum[seg + 1] < d) seg++
            val segLen = cum[seg + 1] - cum[seg]
            val t = if (segLen > 1e-5f) (d - cum[seg]) / segLen else 0f
            val j = (seg + 1) % n
            ox[k] = xs[seg] + (xs[j] - xs[seg]) * t
            oy[k] = ys[seg] + (ys[j] - ys[seg]) * t
        }
        return ox to oy
    }

    private fun normals(xs: FloatArray, ys: FloatArray, span: Int): Pair<FloatArray, FloatArray> {
        val n = xs.size
        val nx = FloatArray(n)
        val ny = FloatArray(n)
        for (i in 0 until n) {
            val a = ((i - span) % n + n) % n
            val b = (i + span) % n
            val tx = xs[b] - xs[a]
            val ty = ys[b] - ys[a]
            val len = sqrt(tx * tx + ty * ty).coerceAtLeast(1e-5f)
            nx[i] = ty / len
            ny[i] = -tx / len
        }
        return nx to ny
    }

    private fun orientOutward(xs: FloatArray, ys: FloatArray, nx: FloatArray, ny: FloatArray, mask: BooleanArray, w: Int, h: Int) {
        var insideVotes = 0
        for (i in xs.indices) {
            val x = (xs[i] + nx[i] * 3f).toInt()
            val y = (ys[i] + ny[i] * 3f).toInt()
            if (x in 0 until w && y in 0 until h && mask[y * w + x]) insideVotes++
        }
        if (insideVotes > xs.size / 2) {
            for (i in xs.indices) { nx[i] = -nx[i]; ny[i] = -ny[i] }
        }
    }
}
