package com.aurora.player.logo

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Un contorno chiuso, campionato a passo costante, con le normali rivolte verso l'esterno. */
class Contour(
    val xs: FloatArray,
    val ys: FloatArray,
    val nx: FloatArray,
    val ny: FloatArray,
    /** Distanza media tra due punti, in unità normalizzate. */
    val spacing: Float,
) {
    val size: Int get() = xs.size
}

/**
 * Forma di un logo: coordinate normalizzate centrate in (0,0), dove 1.0 = lato più lungo dell'immagine.
 * normW/normH = dimensioni dell'immagine nella stessa scala.
 */
class LogoShape(val contours: List<Contour>, val normW: Float, val normH: Float)

/**
 * Trova il perimetro di qualsiasi logo (PNG con trasparenza, o testo) così che
 * le onde di frequenza possano "uscire" esattamente dai suoi bordi.
 * Pipeline: maschera alfa -> componenti connesse -> tracciamento Moore -> levigatura -> ricampionamento -> normali.
 */
object LogoContourExtractor {
    private val DX = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    private val DY = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

    fun extract(src: Bitmap, targetPoints: Int = 420): LogoShape? {
        val maxDim = 220
        val scale = maxDim.toFloat() / max(src.width, src.height)
        val sw = max(1, (src.width * scale).roundToInt())
        val sh = max(1, (src.height * scale).roundToInt())
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val small = Bitmap.createScaledBitmap(soft, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)

        val pad = 4
        val w = sw + pad * 2
        val h = sh + pad * 2
        val raw = BooleanArray(w * h)
        for (y in 0 until sh) for (x in 0 until sw) {
            raw[(y + pad) * w + x + pad] = (px[y * sw + x] ushr 24) >= 110
        }
        val mask = dilate(raw, w, h)

        // Componenti connesse (4-vicinato)
        val labels = IntArray(w * h)
        val queue = IntArray(w * h)
        data class Comp(val label: Int, val start: Int, val count: Int)
        val comps = ArrayList<Comp>()
        var label = 0
        var totalOn = 0
        for (i in 0 until w * h) {
            if (!mask[i] || labels[i] != 0) continue
            label++
            var head = 0
            var tail = 0
            queue[tail++] = i
            labels[i] = label
            while (head < tail) {
                val p = queue[head++]
                val x = p % w
                val y = p / w
                if (x > 0 && mask[p - 1] && labels[p - 1] == 0) { labels[p - 1] = label; queue[tail++] = p - 1 }
                if (x < w - 1 && mask[p + 1] && labels[p + 1] == 0) { labels[p + 1] = label; queue[tail++] = p + 1 }
                if (y > 0 && mask[p - w] && labels[p - w] == 0) { labels[p - w] = label; queue[tail++] = p - w }
                if (y < h - 1 && mask[p + w] && labels[p + w] == 0) { labels[p + w] = label; queue[tail++] = p + w }
            }
            comps.add(Comp(label, i, tail))
            totalOn += tail
        }
        if (totalOn == 0) return null
        val minCount = max(25, (totalOn * 0.004f).toInt())

        // Traccia i bordi esterni
        val rawContours = ArrayList<Pair<FloatArray, FloatArray>>()
        for (c in comps) {
            if (c.count < minCount) continue
            val pts = trace(labels, w, h, c.label, c.start, c.count)
            if (pts.size < 12) continue
            val xs = FloatArray(pts.size) { (pts[it] % w).toFloat() + 0.5f }
            val ys = FloatArray(pts.size) { (pts[it] / w).toFloat() + 0.5f }
            smooth(xs); smooth(xs)
            smooth(ys); smooth(ys)
            rawContours.add(xs to ys)
        }
        if (rawContours.isEmpty()) return null

        val lengths = rawContours.map { perimeter(it.first, it.second) }
        val totalLen = lengths.sum()
        val s = max(sw, sh).toFloat()
        val contours = ArrayList<Contour>()
        for ((idx, rc) in rawContours.withIndex()) {
            val m = max(24, (targetPoints * lengths[idx] / totalLen).roundToInt())
            val (rx, ry) = resample(rc.first, rc.second, m)
            val (nx, ny) = normals(rx, ry)
            orientOutward(rx, ry, nx, ny, mask, w, h)
            for (i in 0 until m) {
                rx[i] = (rx[i] - pad - sw / 2f) / s
                ry[i] = (ry[i] - pad - sh / 2f) / s
            }
            contours.add(Contour(rx, ry, nx, ny, lengths[idx] / m / s))
        }
        return LogoShape(contours, sw / s, sh / s)
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            out[i] = m[i] || m[i - 1] || m[i + 1] || m[i - w] || m[i + w]
        }
        return out
    }

    private fun trace(labels: IntArray, w: Int, h: Int, label: Int, start: Int, count: Int): IntArray {
        fun inside(x: Int, y: Int) = x >= 0 && y >= 0 && x < w && y < h && labels[y * w + x] == label
        val pts = ArrayList<Int>()
        val sx = start % w
        val sy = start / w
        var cx = sx
        var cy = sy
        var back = 0 // il pixel a ovest del primo è sicuramente vuoto
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
            val bx = cx + DX[prev] - nxp
            val by = cy + DY[prev] - nyp
            back = dirIndex(bx, by)
            cx = nxp
            cy = nyp
            pts.add(cy * w + cx)
        }
        // l'ultimo punto coincide col primo: rimuovilo
        if (pts.size > 2 && pts.last() == start) pts.removeAt(pts.size - 1)
        return pts.toIntArray()
    }

    private fun dirIndex(dx: Int, dy: Int): Int {
        for (i in 0 until 8) if (DX[i] == dx && DY[i] == dy) return i
        return 0
    }

    private fun smooth(a: FloatArray) {
        val n = a.size
        val src = a.copyOf()
        for (i in 0 until n) {
            var sum = 0f
            for (k in -2..2) sum += src[(i + k + n) % n]
            a[i] = sum / 5f
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

    private fun normals(xs: FloatArray, ys: FloatArray): Pair<FloatArray, FloatArray> {
        val n = xs.size
        val nx = FloatArray(n)
        val ny = FloatArray(n)
        for (i in 0 until n) {
            val a = (i - 1 + n) % n
            val b = (i + 1) % n
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
            val x = (xs[i] + nx[i] * 2.5f).toInt()
            val y = (ys[i] + ny[i] * 2.5f).toInt()
            if (x in 0 until w && y in 0 until h && mask[y * w + x]) insideVotes++
        }
        if (insideVotes > xs.size / 2) {
            for (i in xs.indices) { nx[i] = -nx[i]; ny[i] = -ny[i] }
        }
    }
}
