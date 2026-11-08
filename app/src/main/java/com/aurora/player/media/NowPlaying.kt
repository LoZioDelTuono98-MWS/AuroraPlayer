package com.aurora.player.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: Bitmap? = null,
    val sourceApp: String = "",
    val sourcePackage: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val positionUpdateTime: Long = 0L,
    val speed: Float = 1f,
    val hasSession: Boolean = false,
) {
    fun currentPosition(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying || positionUpdateTime == 0L) return positionMs
        val p = positionMs + ((now - positionUpdateTime) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }
}

/** Segue la sessione multimediale attiva di qualsiasi app e ne espone info e controlli. */
object NowPlaying {
    private val _track = MutableStateFlow(TrackInfo())
    val track: StateFlow<TrackInfo> = _track.asStateFlow()

    private var appContext: Context? = null
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastArtKey: String? = null
    private var lastArt: Bitmap? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list ?: emptyList()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            refresh()
            if (state?.state != PlaybackState.STATE_PLAYING) recheckSoon()
        }
        override fun onSessionDestroyed() = recheckSoon()
    }

    /** Ritorna false se l'accesso alle notifiche non è ancora stato concesso. */
    fun attach(context: Context): Boolean {
        val ctx = context.applicationContext
        appContext = ctx
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(ctx, MediaListenerService::class.java)
        return try {
            if (sessionManager == null) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
                sessionManager = msm
            }
            pickController(msm.getActiveSessions(component))
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun recheckSoon() {
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, 700)
    }

    private val recheck = Runnable {
        val ctx = appContext ?: return@Runnable
        val msm = sessionManager ?: return@Runnable
        try {
            pickController(msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java)))
        } catch (e: SecurityException) { /* permesso revocato */ }
    }

    private fun pickController(list: List<MediaController>) {
        val playing = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val current = controller?.let { c -> list.firstOrNull { it.sessionToken == c.sessionToken } }
        val chosen = playing ?: current ?: list.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, handler)
        }
        refresh()
    }

    private fun refresh() {
        val c = controller
        if (c == null) {
            _track.value = TrackInfo()
            return
        }
        val md = c.metadata
        val st = c.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val pkg = c.packageName ?: ""
        _track.value = TrackInfo(
            title = title,
            artist = artist,
            album = album,
            art = resolveArt(md, "$pkg|$title|$artist|$album"),
            sourceApp = appLabel(pkg),
            sourcePackage = pkg,
            isPlaying = st?.state == PlaybackState.STATE_PLAYING,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = st?.position ?: 0L,
            positionUpdateTime = st?.lastPositionUpdateTime ?: 0L,
            speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            hasSession = true,
        )
    }

    private fun resolveArt(md: MediaMetadata?, key: String): Bitmap? {
        md ?: return null
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) {
            lastArtKey = key; lastArt = bmp
            return bmp
        }
        if (key == lastArtKey) return lastArt
        val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val loaded = uri?.let { loadLocalUri(it) }
        lastArtKey = key; lastArt = loaded
        return loaded
    }

    private fun loadLocalUri(s: String): Bitmap? {
        val ctx = appContext ?: return null
        return try {
            val uri = Uri.parse(s)
            if (uri.scheme != "content" && uri.scheme != "file" && uri.scheme != "android.resource") return null
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun appLabel(pkg: String): String {
        val pm = appContext?.packageManager ?: return pkg
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    fun launchIntentForSource(): Intent? {
        val pkg = _track.value.sourcePackage.ifEmpty { return null }
        return appContext?.packageManager?.getLaunchIntentForPackage(pkg)
    }
}
