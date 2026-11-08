package com.aurora.player.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.aurora.player.settings.ColorMode
import com.aurora.player.settings.ParticleStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Un sistema per ogni stile di particelle: si possono attivare più stili insieme. */
class ParticleSystem(private val max: Int = 500, private val burstMode: Boolean = false) {
    private val x = FloatArray(max)
    private val y = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val life = FloatArray(max)
    private val maxLife = FloatArray(max)
    private val sz = FloatArray(max)
    private val tone = FloatArray(max)
    private var count = 0
    private var spawnAcc = 0f
    private val rnd = Random(42 + max)

    fun clear() {
        count = 0
        spawnAcc = 0f
    }

    /** Esplosione di luce in un punto (tocchi sullo schermo). */
    fun burst(px: Float, py: Float, n: Int) {
        repeat(n) {
            if (count >= max) return
            val i = count++
            val a = rnd.nextFloat() * TAU
            val sp = 120f + rnd.nextFloat() * 420f
            x[i] = px; y[i] = py
            vx[i] = cos(a) * sp; vy[i] = sin(a) * sp
            life[i] = 0f
            maxLife[i] = 0.6f + rnd.nextFloat() * 0.8f
            sz[i] = 1.5f + rnd.nextFloat() * 2.5f
            tone[i] = rnd.nextFloat()
        }
    }

    fun update(dt: Float, w: Float, h: Float, style: ParticleStyle, density: Float, bass: Float, beat: Float, treble: Float) {
        val margin = 60f
        var i = 0
        while (i < count) {
            life[i] += dt
            if (burstMode) {
                val drag = exp(-dt * 2.5f)
                vx[i] *= drag; vy[i] *= drag
                x[i] += vx[i] * dt; y[i] += vy[i] * dt
            } else when (style) {
                ParticleStyle.STARDUST -> {
                    val boost = 1f + bass * 3.5f + beat * 2f
                    x[i] += vx[i] * dt * boost
                    y[i] += vy[i] * dt * boost
                }
                ParticleStyle.EMBERS -> {
                    vx[i] += sin(life[i] * 3f + tone[i] * 10f) * 14f * dt
                    x[i] += vx[i] * dt
                    y[i] += vy[i] * dt * (1f + bass * 1.8f)
                }
                ParticleStyle.BUBBLES -> {
                    x[i] += vx[i] * dt + sin(life[i] * 1.5f + tone[i] * 6f) * 12f * dt
                    y[i] += vy[i] * dt * (1f + bass * 0.8f)
                }
                ParticleStyle.CONFETTI -> {
                    vy[i] += 150f * dt
                    vx[i] *= exp(-dt * 0.8f)
                    x[i] += (vx[i] + sin(life[i] * 3f + tone[i] * 9f) * 25f) * dt
                    y[i] += vy[i] * dt
                }
                ParticleStyle.FIREFLIES -> {
                    vx[i] += (rnd.nextFloat() - 0.5f) * 90f * dt
                    vy[i] += (rnd.nextFloat() - 0.5f) * 90f * dt
                    val drag = exp(-dt * 0.6f)
                    vx[i] *= drag; vy[i] *= drag
                    val k = 1f + treble * 2f
                    x[i] += vx[i] * dt * k
                    y[i] += vy[i] * dt * k
                }
                ParticleStyle.SNOW -> {
                    x[i] += (vx[i] + sin(life[i] * 1.2f + tone[i] * 8f) * 18f) * dt
                    y[i] += vy[i] * dt * (1f + bass * 0.5f)
                }
            }
            val out = x[i] < -margin || x[i] > w + margin || y[i] < -margin || y[i] > h + margin
            if (life[i] >= maxLife[i] || out) {
                val last = count - 1
                x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                life[i] = life[last]; maxLife[i] = maxLife[last]; sz[i] = sz[last]; tone[i] = tone[last]
                count--
                continue
            }
            i++
        }
        if (burstMode) return

        val rate = density * when (style) {
            ParticleStyle.STARDUST -> 60f + bass * 500f + beat * 700f
            ParticleStyle.EMBERS -> 30f + bass * 140f + beat * 220f
            ParticleStyle.BUBBLES -> 5f + bass * 20f + beat * 35f
            ParticleStyle.CONFETTI -> bass * 30f + beat * 450f
            ParticleStyle.FIREFLIES -> 6f + treble * 25f
            ParticleStyle.SNOW -> 18f + bass * 20f
        }
        spawnAcc += rate * dt
        val cx = w / 2f
        val cy = h / 2f
        val minDim = min(w, h)
        while (spawnAcc >= 1f && count < max) {
            spawn(style, w, h, cx, cy, minDim)
            spawnAcc -= 1f
        }
        if (count >= max) spawnAcc = 0f
    }

    private fun spawn(style: ParticleStyle, w: Float, h: Float, cx: Float, cy: Float, minDim: Float) {
        val i = count++
        life[i] = 0f
        tone[i] = rnd.nextFloat()
        when (style) {
            ParticleStyle.STARDUST -> {
                val a = rnd.nextFloat() * TAU
                val r0 = minDim * (0.04f + rnd.nextFloat() * 0.12f)
                x[i] = cx + cos(a) * r0
                y[i] = cy + sin(a) * r0
                val sp = 50f + rnd.nextFloat() * 170f
                vx[i] = cos(a) * sp
                vy[i] = sin(a) * sp
                maxLife[i] = 1.5f + rnd.nextFloat() * 2.5f
                sz[i] = 1f + rnd.nextFloat() * 2f
            }
            ParticleStyle.EMBERS -> {
                x[i] = rnd.nextFloat() * w
                y[i] = h + 10f
                vx[i] = (rnd.nextFloat() - 0.5f) * 30f
                vy[i] = -(40f + rnd.nextFloat() * 110f)
                maxLife[i] = 3f + rnd.nextFloat() * 5f
                sz[i] = 1.5f + rnd.nextFloat() * 3f
            }
            ParticleStyle.BUBBLES -> {
                x[i] = rnd.nextFloat() * w
                y[i] = h * 0.3f + rnd.nextFloat() * h * 0.8f
                vx[i] = (rnd.nextFloat() - 0.5f) * 10f
                vy[i] = -(10f + rnd.nextFloat() * 35f)
                maxLife[i] = 4f + rnd.nextFloat() * 6f
                sz[i] = 6f + rnd.nextFloat() * 24f
            }
            ParticleStyle.CONFETTI -> {
                x[i] = rnd.nextFloat() * w
                y[i] = -10f
                vx[i] = (rnd.nextFloat() - 0.5f) * 120f
                vy[i] = 40f + rnd.nextFloat() * 120f
                maxLife[i] = 6f + rnd.nextFloat() * 3f
                sz[i] = 3f + rnd.nextFloat() * 3f
            }
            ParticleStyle.FIREFLIES -> {
                x[i] = rnd.nextFloat() * w
                y[i] = rnd.nextFloat() * h
                vx[i] = (rnd.nextFloat() - 0.5f) * 30f
                vy[i] = (rnd.nextFloat() - 0.5f) * 30f
                maxLife[i] = 4f + rnd.nextFloat() * 4f
                sz[i] = 1.5f + rnd.nextFloat() * 1.5f
            }
            ParticleStyle.SNOW -> {
                x[i] = rnd.nextFloat() * w
                y[i] = -10f
                vx[i] = (rnd.nextFloat() - 0.5f) * 20f
                vy[i] = 25f + rnd.nextFloat() * 45f
                maxLife[i] = 14f + rnd.nextFloat() * 6f
                sz[i] = 1f + rnd.nextFloat() * 2.5f
            }
        }
    }

    fun draw(scope: DrawScope, style: ParticleStyle, bass: Float, treble: Float, mode: ColorMode, colors: SceneColors, time: Float) = with(scope) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = min(size.width, size.height)
        val d = density
        for (i in 0 until count) {
            val fade = sin(PIF * (life[i] / maxLife[i]).coerceIn(0f, 1f))
            val c = colorAt(mode, colors, tone[i], time)
            val p = Offset(x[i], y[i])
            if (burstMode) {
                val r = sz[i] * d
                drawCircle(c.copy(alpha = fade * 0.25f), r * 3f, p, blendMode = BlendMode.Plus)
                drawCircle(lerp(c, Color.White, 0.4f).copy(alpha = fade), r, p, blendMode = BlendMode.Plus)
                continue
            }
            when (style) {
                ParticleStyle.STARDUST -> {
                    val dx = x[i] - cx
                    val dy = y[i] - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    val streak = 0.025f + bass * 0.06f
                    drawLine(
                        color = c.copy(alpha = (fade * 0.9f).coerceIn(0f, 1f)),
                        start = p,
                        end = Offset(x[i] - vx[i] * streak, y[i] - vy[i] * streak),
                        strokeWidth = sz[i] * d * (0.4f + dist / minDim * 1.6f),
                        cap = StrokeCap.Round,
                        blendMode = BlendMode.Plus,
                    )
                }
                ParticleStyle.EMBERS -> {
                    val r = sz[i] * d * (1f + bass * 0.6f)
                    drawCircle(c.copy(alpha = fade * 0.18f), r * 3.2f, p, blendMode = BlendMode.Plus)
                    drawCircle(c.copy(alpha = fade * 0.9f), r, p, blendMode = BlendMode.Plus)
                }
                ParticleStyle.BUBBLES -> {
                    val r = sz[i] * d * (1f + bass * 0.25f)
                    drawCircle(c.copy(alpha = fade * 0.5f), r, p, style = Stroke(1.2f * d))
                    drawCircle(Color.White.copy(alpha = fade * 0.5f), r * 0.18f, Offset(x[i] - r * 0.4f, y[i] - r * 0.4f))
                }
                ParticleStyle.CONFETTI -> {
                    val ang = life[i] * (3f + tone[i] * 5f) + tone[i] * 10f
                    val flip = abs(cos(life[i] * (4f + tone[i] * 4f)))
                    val len = sz[i] * d * 1.2f
                    val dx = cos(ang) * len
                    val dy = sin(ang) * len
                    val a = min(1f, (maxLife[i] - life[i]) * 2f)
                    drawLine(
                        c.copy(alpha = 0.95f * a), Offset(x[i] - dx, y[i] - dy), Offset(x[i] + dx, y[i] + dy),
                        strokeWidth = sz[i] * d * (0.35f + 0.65f * flip),
                    )
                }
                ParticleStyle.FIREFLIES -> {
                    val pulse = 0.5f + 0.5f * sin(life[i] * (3f + tone[i] * 3f) + tone[i] * 20f)
                    val a = (fade * (0.25f + 0.75f * pulse) * (0.6f + treble * 0.8f)).coerceIn(0f, 1f)
                    val r = sz[i] * d
                    drawCircle(c.copy(alpha = a * 0.25f), r * 4f, p, blendMode = BlendMode.Plus)
                    drawCircle(lerp(c, Color.White, 0.5f).copy(alpha = a), r, p, blendMode = BlendMode.Plus)
                }
                ParticleStyle.SNOW -> {
                    drawCircle(lerp(Color.White, c, 0.25f).copy(alpha = fade * 0.8f), sz[i] * d, p)
                }
            }
        }
    }
}
