package com.aurora.player.ui.scene

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.palette.graphics.Palette
import com.aurora.player.settings.ColorMode
import kotlin.math.max
import kotlin.math.roundToInt

data class SceneColors(val c1: Color, val c2: Color, val c3: Color) {
    companion object {
        val Default = SceneColors(Color(0xFF7C4DFF), Color(0xFF00E5FF), Color(0xFFFF4081))
    }
}

/** Copertina pronta all'uso: originale, versione sfocata per lo sfondo e colori estratti. */
class ArtAssets(val image: ImageBitmap, val blurred: ImageBitmap, val colors: SceneColors) {
    companion object {
        fun from(src: Bitmap): ArtAssets {
            val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
            val p = Palette.from(soft).maximumColorCount(16).generate()
            val d = SceneColors.Default
            val c1 = p.getVibrantColor(p.getDominantColor(0xFF7C4DFF.toInt()))
            val c2 = p.getLightVibrantColor(p.getMutedColor(0xFF00E5FF.toInt()))
            val c3 = p.getDarkVibrantColor(p.getLightMutedColor(0xFFFF4081.toInt()))
            val colors = SceneColors(boost(c1, d.c1), boost(c2, d.c2), boost(c3, d.c3))

            val midW = 96
            val midH = max(1, (midW.toFloat() * soft.height / soft.width).roundToInt())
            val mid = Bitmap.createScaledBitmap(soft, midW, midH, true)
            val tiny = Bitmap.createScaledBitmap(mid, 20, max(1, (20f * midH / midW).roundToInt()), true)
            return ArtAssets(soft.asImageBitmap(), tiny.asImageBitmap(), colors)
        }

        private fun boost(argb: Int, fallback: Color): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, hsv)
            if (hsv[1] > 0.08f) hsv[1] = hsv[1].coerceAtLeast(0.55f)
            hsv[2] = hsv[2].coerceAtLeast(0.9f)
            return Color(android.graphics.Color.HSVToColor(hsv)).takeIf { argb != 0 } ?: fallback
        }
    }
}

fun paletteFor(mode: ColorMode, album: SceneColors?): SceneColors = when (mode) {
    ColorMode.ALBUM, ColorMode.RAINBOW -> album ?: SceneColors.Default
    ColorMode.NEON -> SceneColors(Color(0xFF00E5FF), Color(0xFFFF00E5), Color(0xFF7C4DFF))
    ColorMode.SUNSET -> SceneColors(Color(0xFFFF6B35), Color(0xFFFF2E63), Color(0xFFFFC93C))
    ColorMode.OCEAN -> SceneColors(Color(0xFF00B4D8), Color(0xFF4361EE), Color(0xFF90E0EF))
    ColorMode.MONO -> SceneColors(Color.White, Color(0xFFD0D0E0), Color(0xFFFFFFFF))
}

/** Colore lungo un ciclo 0..1 (usato per barre, particelle, contorni). */
fun colorAt(mode: ColorMode, c: SceneColors, t: Float, time: Float): Color {
    if (mode == ColorMode.RAINBOW) {
        val hue = (((t * 360f + time * 40f) % 360f) + 360f) % 360f
        return Color.hsv(hue, 0.75f, 1f)
    }
    val pos = ((((t + time * 0.03f) % 1f) + 1f) % 1f) * 3f
    val i = pos.toInt().coerceIn(0, 2)
    val f = pos - i
    val a = when (i) { 0 -> c.c1; 1 -> c.c2; else -> c.c3 }
    val b = when (i) { 0 -> c.c2; 1 -> c.c3; else -> c.c1 }
    return lerp(a, b, f)
}
