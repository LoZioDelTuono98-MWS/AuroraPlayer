package com.aurora.player.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aurora.player.settings.ColorMode
import com.aurora.player.settings.ParticleStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class ParticleSystem(private val max: Int = 600) {
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
    private var lastStyle: ParticleStyle? = null
    private val rnd = Random(42)

    fun update(dt: Float, w: Float, h: Float, style: ParticleStyle, density: Float, bass: Float, beat: Float) {
        if (style != lastStyle) { count = 0; lastStyle = style }
        val cx = w / 2f
        val cy = h / 2f
        val margin = 60f
        var i = 0
        while (i < count) {
            life[i] += dt
            when (style) {
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

        val rate = density * when (style) {
            ParticleStyle.STARDUST -> 60f + bass * 500f + beat * 700f
            ParticleStyle.EMBERS -> 30f + bass * 140f + beat * 220f
            ParticleStyle.BUBBLES -> 5f + bass * 20f + beat * 35f
        }
        spawnAcc += rate * dt
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
                val a = rnd.nextFloat() * 2f * PI.toFloat()
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
        }
    }

    fun draw(scope: DrawScope, style: ParticleStyle, bass: Float, mode: ColorMode, colors: SceneColors, time: Float) = with(scope) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = min(size.width, size.height)
        val d = density
        for (i in 0 until count) {
            val fade = sin(PI.toFloat() * (life[i] / maxLife[i]).coerceIn(0f, 1f))
            val c = colorAt(mode, colors, tone[i], time)
            when (style) {
                ParticleStyle.STARDUST -> {
                    val dx = x[i] - cx
                    val dy = y[i] - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    val streak = 0.025f + bass * 0.06f
                    drawLine(
                        color = c.copy(alpha = (fade * 0.9f).coerceIn(0f, 1f)),
                        start = Offset(x[i], y[i]),
                        end = Offset(x[i] - vx[i] * streak, y[i] - vy[i] * streak),
                        strokeWidth = sz[i] * d * (0.4f + dist / minDim * 1.6f),
                        cap = StrokeCap.Round,
                        blendMode = BlendMode.Plus,
                    )
                }
                ParticleStyle.EMBERS -> {
                    val r = sz[i] * d * (1f + bass * 0.6f)
                    drawCircle(c.copy(alpha = fade * 0.18f), r * 3.2f, Offset(x[i], y[i]), blendMode = BlendMode.Plus)
                    drawCircle(c.copy(alpha = fade * 0.9f), r, Offset(x[i], y[i]), blendMode = BlendMode.Plus)
                }
                ParticleStyle.BUBBLES -> {
                    val r = sz[i] * d * (1f + bass * 0.25f)
                    drawCircle(c.copy(alpha = fade * 0.5f), r, Offset(x[i], y[i]), style = Stroke(1.2f * d))
                    drawCircle(Color.White.copy(alpha = fade * 0.5f), r * 0.18f, Offset(x[i] - r * 0.4f, y[i] - r * 0.4f))
                }
            }
        }
    }
}
