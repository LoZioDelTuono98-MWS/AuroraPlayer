package com.aurora.player.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.aurora.player.audio.AudioAnalyzer
import com.aurora.player.audio.AudioAnalyzer.Companion.BANDS
import com.aurora.player.logo.LogoAssets
import com.aurora.player.settings.BackgroundMode
import com.aurora.player.settings.Layer
import com.aurora.player.settings.ParticleStyle
import com.aurora.player.settings.VisualSettings
import com.aurora.player.settings.VisualizerStyle
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
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val eco: Boolean = false,
)

private const val RING_LIFE = 1.3f

/**
 * Compone la scena a strati:
 * sfondo → foschia/raggi → livelli di fondo → onde d'urto → particelle → orbite → CENTRO → livelli in primo piano → tocchi → flash.
 */
class SceneRenderer {
    private val fx = Fx()
    private val layers = LayerState()
    private val systems = HashMap<ParticleStyle, ParticleSystem>()
    private val touchParticles = ParticleSystem(260, burstMode = true)
    private val pendingTouches = ArrayList<Offset>()
    private val rippleX = FloatArray(6)
    private val rippleY = FloatArray(6)
    private val rippleLife = FloatArray(6) { -1f }
    private val ringLife = FloatArray(8) { -1f }
    private val rnd = Random(3)
    private val fogSeeds = Random(11).let { r ->
        Array(6) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.35f + r.nextFloat() * 0.3f, r.nextFloat() * TAU, 0.04f + r.nextFloat() * 0.06f) }
    }
    private var lastNanos = 0L

    /** Chiamato dal tocco sullo schermo. */
    fun touch(p: Offset) {
        if (pendingTouches.size < 8) pendingTouches.add(p)
    }

    fun draw(scope: DrawScope, inp: SceneInputs) = with(scope) {
        val dt = if (lastNanos == 0L) 0.016f else ((inp.frameNanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = inp.frameNanos
        val s = inp.settings
        fx.dt = dt
        fx.time += dt
        fx.mode = s.colorMode
        fx.colors = inp.colors
        fx.glow = s.glow
        fx.eco = inp.eco
        fx.sensitivity = s.sensitivity

        val a = inp.analyzer
        a.update(min(dt, 0.05f))
        val sens = s.sensitivity
        for (i in 0 until BANDS) {
            val v = (a.bands[i] * sens).coerceIn(0f, 1.15f)
            fx.bands[i] = v
            fx.peaks[i] = if (v > fx.peaks[i]) v else max(0f, fx.peaks[i] - dt * 0.5f)
            fx.echo[i] += (v - fx.echo[i]) * min(1f, dt * 3f)
        }
        fx.wave = a.wave
        fx.bass = (a.bass * sens).coerceIn(0f, 1.3f)
        fx.mid = (a.mid * sens).coerceIn(0f, 1.3f)
        fx.treble = (a.treble * sens).coerceIn(0f, 1.3f)
        val prevBeat = fx.beat
        fx.beat = a.beat
        fx.beatHit = fx.beat > 0.95f && prevBeat < 0.9f
        if (inp.playing && s.spin) fx.spin = (fx.spin + dt * (10f + fx.bass * 40f)) % 360f
        updateRings(dt)

        val bgShift = if (s.parallax) Offset(-inp.tiltX, inp.tiltY) * 22.dp.toPx() else Offset.Zero
        val fgShift = if (s.parallax) Offset(inp.tiltX, -inp.tiltY) * 12.dp.toPx() else Offset.Zero

        drawBackground(inp, bgShift)
        if (s.fog) drawFog(s.fogIntensity)
        if (s.rays) drawRays(s.raysIntensity)

        val camScale = if (s.cameraMotion) 1f + fx.beat * 0.045f + fx.bass * 0.02f else 1f
        val camRot = if (s.cameraMotion) sin(fx.time * 0.35f) * 2.2f else 0f
        val shakeAmp = if (s.bassShake) fx.beat * 14.dp.toPx() else 0f
        val tx = (rnd.nextFloat() - 0.5f) * shakeAmp + fgShift.x
        val ty = (rnd.nextFloat() - 0.5f) * shakeAmp + fgShift.y
        val L = s.layers

        withTransform({
            translate(tx, ty)
            rotate(camRot, center)
            scale(camScale, camScale, center)
        }) {
            if (Layer.AURORA in L) layers.drawAurora(this, fx)
            if (Layer.TUNNEL in L) layers.drawTunnel(this, fx)
            if (Layer.WARP in L) layers.drawWarp(this, fx)
            if (Layer.GRID in L) layers.drawGrid(this, fx)
            if (Layer.RIDGES in L) layers.drawRidges(this, fx)
            if (s.shockwaves) drawRings()
            drawParticles(s)
            if (Layer.ORBITS in L) layers.drawOrbits(this, fx)
            when (s.style) {
                VisualizerStyle.RING -> drawCircleBars(fx, inp)
                VisualizerStyle.WAVE -> drawCircleWave(fx, inp)
                VisualizerStyle.ORB -> drawOrb(fx, inp)
                VisualizerStyle.MANDALA -> drawMandala(fx, inp)
                VisualizerStyle.ART -> drawArtOnly(fx, inp)
                VisualizerStyle.LOGO -> drawLogo(fx, inp)
                VisualizerStyle.NONE -> Unit
            }
            if (Layer.BARS in L) layers.drawBars(this, fx)
            if (Layer.SCOPE in L) layers.drawScope(this, fx)
            if (Layer.VECTOR in L) layers.drawVector(this, fx)
            if (Layer.LIGHTNING in L) layers.drawLightning(this, fx)
        }

        drawTouches(s.touchFx)

        if (s.beatFlash) drawRect(fx.colors.c1.copy(alpha = fx.beat * 0.10f), blendMode = BlendMode.Plus)
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.6f),
                center = center,
                radius = max(size.width, size.height) * 0.75f,
            )
        )
    }

    // ---------- Particelle (più stili insieme) ----------

    private fun DrawScope.drawParticles(s: VisualSettings) {
        for (st in ParticleStyle.entries) {
            val on = s.particles && st in s.particleStyles
            val existing = systems[st]
            if (!on) {
                existing?.clear()
                continue
            }
            val sys = existing ?: ParticleSystem().also { systems[st] = it }
            sys.update(fx.dt, size.width, size.height, st, s.particleDensity, fx.bass, fx.beat, fx.treble)
            sys.draw(this, st, fx.bass, fx.treble, fx.mode, fx.colors, fx.time)
        }
    }

    // ---------- Tocchi luminosi ----------

    private fun DrawScope.drawTouches(enabled: Boolean) {
        if (enabled) {
            for (p in pendingTouches) {
                touchParticles.burst(p.x, p.y, if (fx.eco) 25 else 60)
                val slot = rippleLife.indexOfFirst { it < 0f }.takeIf { it >= 0 } ?: 0
                rippleX[slot] = p.x; rippleY[slot] = p.y; rippleLife[slot] = 0f
            }
        }
        pendingTouches.clear()
        touchParticles.update(fx.dt, size.width, size.height, ParticleStyle.STARDUST, 0f, 0f, 0f, 0f)
        touchParticles.draw(this, ParticleStyle.STARDUST, 0f, 0f, fx.mode, fx.colors, fx.time)
        val minDim = min(size.width, size.height)
        for (i in rippleLife.indices) {
            if (rippleLife[i] < 0f) continue
            rippleLife[i] += fx.dt
            val k = rippleLife[i] / 0.9f
            if (k >= 1f) { rippleLife[i] = -1f; continue }
            val r = minDim * 0.04f + k * minDim * 0.35f
            val a = (1f - k) * (1f - k)
            val p = Offset(rippleX[i], rippleY[i])
            drawCircle(fx.colors.c2.copy(alpha = a * 0.3f), r, p, style = Stroke(8.dp.toPx() * (1f - k) + 1f), blendMode = BlendMode.Plus)
            drawCircle(Color.White.copy(alpha = a * 0.8f), r, p, style = Stroke(1.5.dp.toPx()), blendMode = BlendMode.Plus)
        }
    }

    // ---------- Sfondo ----------

    private fun DrawScope.drawBackground(inp: SceneInputs, shift: Offset) {
        val s = inp.settings
        val zoom = 1f + (if (s.backgroundPulse) fx.bass * 0.06f + fx.beat * 0.03f else 0f) + (if (s.parallax) 0.08f else 0f)
        when (s.background) {
            BackgroundMode.ALBUM, BackgroundMode.CUSTOM -> {
                val custom = s.background == BackgroundMode.CUSTOM
                val img = if (custom) inp.customBg else inp.art?.blurred
                if (img != null) {
                    val drift = if (custom) Offset(sin(fx.time * 0.05f) * size.width * 0.02f, cos(fx.time * 0.04f) * size.height * 0.02f) else Offset.Zero
                    drawCover(img, zoom * (if (custom) 1.05f else 1.15f), drift + shift)
                } else {
                    drawUserGradient(s.bgColors.map { Color(it) }, false, true, 1f)
                }
            }
            BackgroundMode.GRADIENT -> drawUserGradient(s.bgColors.map { Color(it) }, s.bgRadial, s.bgAnimated, zoom)
            BackgroundMode.SOLID -> drawRect(Color(s.bgColors[0]))
        }
        drawRect(Color.Black.copy(alpha = s.backgroundDim.coerceIn(0f, 0.95f)))
    }

    private fun DrawScope.drawUserGradient(cols: List<Color>, radial: Boolean, animated: Boolean, zoom: Float) {
        val t = if (animated) fx.time else 0f
        if (radial) {
            val ctr = Offset(size.width * (0.5f + 0.15f * sin(t * 0.12f)), size.height * (0.5f + 0.12f * cos(t * 0.09f)))
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

    // ---------- Foschia ----------

    private fun DrawScope.drawFog(intensity: Float) {
        val w = size.width
        val h = size.height
        val maxDim = max(w, h)
        for ((i, f) in fogSeeds.withIndex()) {
            val cx = (f[0] + 0.18f * sin(fx.time * f[4] * TAU + f[3])) * w
            val cy = (f[1] + 0.14f * cos(fx.time * f[4] * 1.7f * TAU + f[3] * 1.3f)) * h
            val r = f[2] * maxDim * (1f + fx.mid * 0.15f)
            val alpha = (intensity * (0.10f + 0.22f * fx.mid + 0.18f * fx.beat)).coerceIn(0f, 0.6f)
            val c = fx.col(i / 6f)
            val ctr = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.4f), Color.Transparent), center = ctr, radius = r),
                radius = r,
                center = ctr,
                blendMode = BlendMode.Screen,
            )
        }
    }

    // ---------- Raggi di luce ----------

    private fun DrawScope.drawRays(intensity: Float) {
        val n = 14
        val ctr = center
        val len = max(size.width, size.height)
        for (i in 0 until n) {
            val ang = fx.time * 0.08f + i * TAU / n
            val half = 0.035f + fx.treble * 0.05f + (if (i % 2 == 0) 0.015f else 0f)
            fx.path.reset()
            fx.path.moveTo(ctr.x, ctr.y)
            fx.path.lineTo(ctr.x + cos(ang - half) * len, ctr.y + sin(ang - half) * len)
            fx.path.lineTo(ctr.x + cos(ang + half) * len, ctr.y + sin(ang + half) * len)
            fx.path.close()
            val alpha = (intensity * (0.05f + fx.treble * 0.12f + fx.bass * 0.06f)).coerceIn(0f, 0.5f)
            drawPath(
                fx.path,
                Brush.radialGradient(listOf(fx.col(i.toFloat() / n).copy(alpha = alpha), Color.Transparent), center = ctr, radius = len * 0.7f),
                blendMode = BlendMode.Plus,
            )
        }
    }

    // ---------- Onde d'urto ----------

    private fun updateRings(dt: Float) {
        if (fx.beatHit) {
            val slot = ringLife.indexOfFirst { it < 0f }.takeIf { it >= 0 } ?: ringLife.indices.maxByOrNull { ringLife[it] } ?: 0
            ringLife[slot] = 0f
        }
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
            drawCircle(fx.colors.c2.copy(alpha = a * 0.35f), r, center, style = Stroke((10f * (1f - k) + 2f) * density), blendMode = BlendMode.Plus)
            drawCircle(fx.colors.c2.copy(alpha = a), r, center, style = Stroke((2.5f * (1f - k) + 0.5f) * density), blendMode = BlendMode.Plus)
        }
    }
}
