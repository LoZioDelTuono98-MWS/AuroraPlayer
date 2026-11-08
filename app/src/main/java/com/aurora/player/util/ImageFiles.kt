package com.aurora.player.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ImageFiles {
    const val BG_FILE = "background.img"

    /** Copia l'immagine scelta dentro l'app (gli URI del selettore foto non sono permanenti). */
    suspend fun copyFromUri(context: Context, uri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(context.filesDir, name).outputStream().use { input.copyTo(it) }
            } != null
        } catch (e: Exception) {
            false
        }
    }

    fun decode(file: File, maxSize: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, opts)
    }
}
