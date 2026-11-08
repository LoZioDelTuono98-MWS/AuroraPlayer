package com.aurora.player.logo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aurora.player.settings.LogoSource
import com.aurora.player.util.ImageFiles
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

class LogoAssets(val image: ImageBitmap, val shape: LogoShape)

object LogoFactory {
    const val LOGO_FILE = "logo.img"

    fun build(context: Context, source: LogoSource, text: String): LogoAssets? {
        val bmp = when (source) {
            LogoSource.TEXT -> textToBitmap(text)
            LogoSource.IMAGE -> ImageFiles.decode(File(context.filesDir, LOGO_FILE), 1024)?.let { prepareImage(it) }
                ?: textToBitmap(text)
        }
        val shape = LogoContourExtractor.extract(bmp) ?: return null
        return LogoAssets(bmp.asImageBitmap(), shape)
    }

    fun textToBitmap(text: String): Bitmap {
        val t = text.trim().ifEmpty { "AURORA" }.take(24)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 220f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.06f
        }
        val fm = paint.fontMetrics
        val w = ceil(paint.measureText(t)).toInt() + 48
        val h = ceil(fm.descent - fm.ascent).toInt() + 48
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(t, 24f, 24f - fm.ascent, paint)
        return bmp
    }

    /** Se l'immagine non ha trasparenza (es. JPG su sfondo bianco), rimuove lo sfondo uniforme. */
    private fun prepareImage(src: Bitmap): Bitmap {
        val bmp = if (src.config == Bitmap.Config.ARGB_8888 && src.isMutable) src
        else src.copy(Bitmap.Config.ARGB_8888, true)
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var transparent = 0
        for (c in px) if ((c ushr 24) < 128) transparent++
        if (transparent > px.size * 0.03f) return bmp

        val bg = px[0]
        for (i in px.indices) {
            if (colorDist(px[i], bg) < 60) px[i] = 0
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun colorDist(a: Int, b: Int): Int {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return dr + dg + db
    }
}
