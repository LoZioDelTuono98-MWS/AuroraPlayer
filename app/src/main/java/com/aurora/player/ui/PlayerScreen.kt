package com.aurora.player.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.player.audio.AudioAnalyzer
import com.aurora.player.logo.LogoAssets
import com.aurora.player.logo.LogoFactory
import com.aurora.player.media.NowPlaying
import com.aurora.player.media.TrackInfo
import com.aurora.player.settings.BackgroundMode
import com.aurora.player.settings.SettingsStore
import com.aurora.player.settings.VisualizerStyle
import com.aurora.player.ui.glass.GlassIconButton
import com.aurora.player.ui.glass.LiquidGlass
import com.aurora.player.ui.scene.ArtAssets
import com.aurora.player.ui.scene.SceneColors
import com.aurora.player.ui.scene.SceneInputs
import com.aurora.player.ui.scene.SceneRenderer
import com.aurora.player.ui.scene.paletteFor
import com.aurora.player.util.ImageFiles
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private fun VisualizerStyle.next() = VisualizerStyle.entries[(ordinal + 1) % VisualizerStyle.entries.size]

@Composable
fun PlayerScreen(store: SettingsStore, analyzer: AudioAnalyzer, micGranted: Boolean, onRequestMic: () -> Unit) {
    val context = LocalContext.current
    val settings by store.settings.collectAsStateWithLifecycle()
    val track by NowPlaying.track.collectAsStateWithLifecycle()

    val art by produceState<ArtAssets?>(null, track.art, track.hasSession) {
        val bmp = track.art
        if (bmp == null) {
            if (!track.hasSession) value = null
        } else {
            value = withContext(Dispatchers.Default) { runCatching { ArtAssets.from(bmp) }.getOrNull() }
        }
    }
    val customBg by produceState<ImageBitmap?>(null, settings.background, settings.customBgVersion) {
        value = if (settings.background == BackgroundMode.CUSTOM) {
            withContext(Dispatchers.IO) { ImageFiles.decode(File(context.filesDir, ImageFiles.BG_FILE), 2048)?.asImageBitmap() }
        } else null
    }
    val logo by produceState<LogoAssets?>(null, settings.logoSource, settings.logoText, settings.logoVersion) {
        delay(250) // evita ricalcoli mentre si digita
        value = withContext(Dispatchers.Default) {
            runCatching { LogoFactory.build(context, settings.logoSource, settings.logoText) }.getOrNull()
        }
    }

    val target = paletteFor(settings.colorMode, art?.colors)
    val c1 by animateColorAsState(target.c1, tween(1200), label = "c1")
    val c2 by animateColorAsState(target.c2, tween(1200), label = "c2")
    val c3 by animateColorAsState(target.c3, tween(1200), label = "c3")
    val colors = SceneColors(c1, c2, c3)

    val frame = remember { mutableLongStateOf(0L) }
    val beatState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                frame.longValue = it
                beatState.floatValue = analyzer.beat
            }
        }
    }

    val renderer = remember { SceneRenderer() }
    val hazeState = remember { HazeState() }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(interaction, showSettings) {
        if (!showSettings) {
            delay(4500)
            controlsVisible = false
        }
    }
    fun poke() {
        controlsVisible = true
        interaction++
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (controlsVisible) controlsVisible = false else poke() },
                        onDoubleTap = {
                            store.update { it.copy(style = it.style.next()) }
                            poke()
                        },
                        onLongPress = { showSettings = true },
                    )
                }
        ) {
            renderer.draw(
                this,
                SceneInputs(
                    frameNanos = frame.longValue,
                    analyzer = analyzer,
                    settings = settings,
                    colors = colors,
                    art = art,
                    customBg = customBg,
                    logo = logo,
                    playing = track.isPlaying,
                ),
            )
        }

        if (settings.showInfoPanel) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(16.dp)
            ) {
                NowPlayingCard(
                    track = track,
                    art = art?.image,
                    hazeState = hazeState,
                    accent = colors.c1,
                    pulse = beatState,
                    frame = frame,
                    onOpen = {
                        NowPlaying.launchIntentForSource()?.let {
                            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.Rounded.SkipPrevious, "Precedente", hazeState, colors.c1, beatState) { NowPlaying.previous() }
                    GlassIconButton(
                        if (track.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Play o pausa", hazeState, colors.c1, beatState, size = 54.dp,
                    ) { NowPlaying.playPause() }
                    GlassIconButton(Icons.Rounded.SkipNext, "Successivo", hazeState, colors.c1, beatState) { NowPlaying.next() }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            GlassIconButton(Icons.Rounded.Tune, "Impostazioni", hazeState, colors.c1, null) { showSettings = true }
        }

        AnimatedVisibility(
            visible = controlsVisible || !micGranted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val status = analyzer.status
                if (!micGranted) {
                    GlassChip("Tocca qui per attivare l'audio reattivo", hazeState, colors.c1, onRequestMic)
                    Spacer(Modifier.height(10.dp))
                } else if (status != null) {
                    GlassChip(status, hazeState, colors.c1) {}
                    Spacer(Modifier.height(10.dp))
                }
                if (controlsVisible) {
                    Text(
                        "Doppio tocco: cambia stile  •  Tieni premuto: impostazioni",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(store = store, s = settings, onDismiss = {
            showSettings = false
            poke()
        })
    }
}

@Composable
private fun GlassChip(text: String, hazeState: HazeState, accent: Color, onClick: () -> Unit) {
    LiquidGlass(hazeState, shape = RoundedCornerShape(50), accent = accent) {
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingCard(
    track: TrackInfo,
    art: ImageBitmap?,
    hazeState: HazeState,
    accent: Color,
    pulse: FloatState,
    frame: LongState,
    onOpen: () -> Unit,
) {
    LiquidGlass(hazeState, Modifier.width(300.dp), RoundedCornerShape(28.dp), accent, pulse) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onOpen),
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) {
                        Image(art, contentDescription = "Copertina", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val title = track.title.ifEmpty { if (track.hasSession) "Brano sconosciuto" else "Nessuna riproduzione" }
                    val subtitle = listOf(track.artist, track.album).filter { it.isNotEmpty() }.joinToString("  •  ")
                        .ifEmpty { if (!track.hasSession) "Avvia Spotify, SimpMusic o un altro player" else "" }
                    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, modifier = Modifier.basicMarquee())
                    }
                    if (track.sourceApp.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            track.sourceApp.uppercase(),
                            color = lerp(accent, Color.White, 0.3f),
                            fontSize = 10.sp,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (track.durationMs > 0) {
                Spacer(Modifier.height(12.dp))
                ProgressLine(track, accent, frame)
                val pos by produceState(track.currentPosition(), track) {
                    while (true) {
                        value = track.currentPosition()
                        delay(500)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(formatTime(pos), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(track.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(track: TrackInfo, accent: Color, frame: LongState) {
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        frame.longValue // ridisegna a ogni fotogramma
        val p = (track.currentPosition().toFloat() / track.durationMs).coerceIn(0f, 1f)
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r)
        drawRoundRect(Brush.horizontalGradient(listOf(accent, Color.White)), size = Size(size.width * p, size.height), cornerRadius = r)
        drawCircle(Color.White, size.height * 1.1f, Offset(size.width * p, size.height / 2f))
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
