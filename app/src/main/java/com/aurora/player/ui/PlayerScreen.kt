package com.aurora.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.player.audio.AudioAnalyzer
import com.aurora.player.logo.LogoAssets
import com.aurora.player.logo.LogoFactory
import com.aurora.player.media.NowPlaying
import com.aurora.player.media.TrackInfo
import com.aurora.player.settings.BackgroundMode
import com.aurora.player.settings.FpsLimit
import com.aurora.player.settings.ecoActive
import com.aurora.player.settings.effective
import com.aurora.player.settings.surprise
import com.aurora.player.settings.InfoMode
import com.aurora.player.settings.SettingsStore
import com.aurora.player.settings.VisualizerStyle
import com.aurora.player.ui.glass.GlassIconButton
import com.aurora.player.ui.glass.LiquidGlass
import com.aurora.player.ui.glass.LocalLightGlass
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun VisualizerStyle.next(): VisualizerStyle {
    val list = VisualizerStyle.entries.filter { it != VisualizerStyle.NONE }
    return list[(list.indexOf(this) + 1) % list.size]
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerScreen(store: SettingsStore, analyzer: AudioAnalyzer, micGranted: Boolean, onRequestMic: () -> Unit) {
    val context = LocalContext.current
    val settings by store.settings.collectAsStateWithLifecycle()
    val track by NowPlaying.track.collectAsStateWithLifecycle()

    // ---------- Energia ----------
    val lowBattery by produceState(false) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            value = level in 1..20 && !bm.isCharging
            delay(60_000)
        }
    }
    val eff = remember(settings, lowBattery) { settings.effective(lowBattery) }
    val ecoOn = settings.ecoActive(lowBattery)

    // Schermo acceso: sempre, a meno che non si sia scelto di lasciarlo spegnere in pausa
    val view = LocalView.current
    val keepOn by produceState(true, track.isPlaying, eff.sleepWhenPaused, eff.sleepMinutes) {
        value = true
        if (!track.isPlaying && eff.sleepWhenPaused) {
            delay(eff.sleepMinutes * 60_000L)
            value = false
        }
    }
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    // Luminosità ridotta e frequenza del display
    LaunchedEffect(eff.dimScreen, eff.dimLevel, eff.fpsLimit) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = if (eff.dimScreen) eff.dimLevel.coerceIn(0.01f, 1f) else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        lp.preferredRefreshRate = if (eff.fpsLimit == FpsLimit.MAX) 0f else 60f
        window.attributes = lp
    }

    // Parallasse: sensore attivo solo se serve e solo con l'app in primo piano
    val tilt = remember { FloatArray(2) }
    LifecycleResumeEffect(eff.parallax) {
        if (!eff.parallax) {
            tilt[0] = 0f; tilt[1] = 0f
            return@LifecycleResumeEffect onPauseOrDispose { }
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val tx = (e.values[0] / 9.81f).coerceIn(-1f, 1f)
                val ty = (e.values[1] / 9.81f - 0.5f).coerceIn(-1f, 1f)
                tilt[0] += (tx - tilt[0]) * 0.12f
                tilt[1] += (ty - tilt[1]) * 0.12f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose { sm.unregisterListener(listener) }
    }

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
    val logo by produceState<LogoAssets?>(null, settings.logoSource, settings.logoText, settings.logoVersion, settings.logoHalo) {
        delay(250) // evita ricalcoli mentre si digita
        value = withContext(Dispatchers.Default) {
            runCatching { LogoFactory.build(context, settings.logoSource, settings.logoText, settings.logoHalo) }.getOrNull()
        }
    }

    val target = paletteFor(eff.colorMode, art?.colors, eff.customColors)
    val c1 by animateColorAsState(target.c1, tween(1200), label = "c1")
    val c2 by animateColorAsState(target.c2, tween(1200), label = "c2")
    val c3 by animateColorAsState(target.c3, tween(1200), label = "c3")
    val colors = SceneColors(c1, c2, c3)

    val frame = remember { mutableLongStateOf(0L) }
    val beatState = remember { mutableFloatStateOf(0f) }
    val effState = rememberUpdatedState(eff)
    val playingState = rememberUpdatedState(track.isPlaying)
    LaunchedEffect(Unit) {
        var last = 0L
        var loudAt = 0L
        while (true) {
            val s = effState.value
            val now = SystemClock.uptimeMillis()
            if (analyzer.bass + analyzer.mid + analyzer.treble > 0.03f) loudAt = now
            val idle = s.idleSlowdown && (!playingState.value || now - loudAt > 3000)
            val fps = if (idle) 10 else s.fpsLimit.fps
            if (fps in 1..15) delay(1000L / fps - 12)
            withFrameNanos { t ->
                val interval = if (fps > 0) 1_000_000_000L / fps else 0L
                if (t - last >= interval - 4_000_000L) {
                    last = t
                    frame.longValue = t
                    beatState.floatValue = analyzer.beat
                }
            }
        }
    }

    val renderer = remember { SceneRenderer() }
    val hazeState = remember { HazeState() }

    // Comandi secondari (ingranaggio e suggerimenti): compaiono al tocco
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(interaction, showSettings) {
        if (!showSettings) {
            delay(4500)
            controlsVisible = false
        }
    }

    // Info brano: entra al cambio canzone o al tocco, poi esce da sola
    var infoVisible by remember { mutableStateOf(true) }
    var infoPoke by remember { mutableIntStateOf(0) }
    val trackKey = track.title + "|" + track.artist
    var firstTrack by remember { mutableStateOf(true) }
    LaunchedEffect(trackKey) {
        infoVisible = true
        infoPoke++
        if (!firstTrack && settings.autoSurprise && track.hasSession) store.update { surprise(it) }
        firstTrack = false
    }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }
    LaunchedEffect(infoPoke, eff.infoMode) {
        if (eff.infoMode == InfoMode.AUTO) {
            delay(6000)
            infoVisible = false
        }
    }
    val showInfo = when (eff.infoMode) {
        InfoMode.ALWAYS -> true
        InfoMode.NEVER -> false
        InfoMode.AUTO -> infoVisible
    }

    fun poke() {
        controlsVisible = true
        interaction++
        infoVisible = true
        infoPoke++
    }

    // Feedback dello swipe
    var skipIcon by remember { mutableStateOf(Icons.Rounded.SkipNext) }
    var skipVisible by remember { mutableStateOf(false) }
    var skipCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(skipCount) {
        if (skipCount > 0) {
            skipVisible = true
            delay(650)
            skipVisible = false
        }
    }

    CompositionLocalProvider(LocalLightGlass provides eff.lightGlass) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(if (eff.lightGlass) Modifier else Modifier.hazeSource(hazeState))
                .pointerInput(eff.swipeToSkip) {
                    if (!eff.swipeToSkip) return@pointerInput
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            val th = 80.dp.toPx()
                            if (total < -th) {
                                NowPlaying.next(); skipIcon = Icons.Rounded.SkipNext; skipCount++
                            } else if (total > th) {
                                NowPlaying.previous(); skipIcon = Icons.Rounded.SkipPrevious; skipCount++
                            }
                        },
                    ) { change, amount ->
                        total += amount
                        change.consume()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { p ->
                            if (effState.value.touchFx) renderer.touch(p)
                            if (controlsVisible) controlsVisible = false else poke()
                        },
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
                    settings = eff,
                    colors = colors,
                    art = art,
                    customBg = customBg,
                    logo = logo,
                    playing = track.isPlaying,
                    tiltX = tilt[0],
                    tiltY = tilt[1],
                    eco = ecoOn,
                ),
            )
        }

        // ---- Orologio ----
        if (eff.clock) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(top = 18.dp)
            ) { ClockOverlay() }
        }

        // ---- Info brano (in alto a sinistra) ----
        AnimatedVisibility(
            visible = showInfo,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(16.dp),
            enter = slideInHorizontally(tween(550, easing = FastOutSlowInEasing)) { -it - 60 } +
                fadeIn(tween(450)) + scaleIn(tween(550), initialScale = 0.85f),
            exit = slideOutHorizontally(tween(450, easing = FastOutSlowInEasing)) { -it - 60 } + fadeOut(tween(350)),
        ) {
            InfoPill(
                track = track,
                art = art?.image,
                hazeState = hazeState,
                accent = colors.c1,
                pulse = beatState,
                analyzer = analyzer,
                frame = frame,
                onOpen = {
                    NowPlaying.launchIntentForSource()?.let {
                        runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                },
            )
        }

        // ---- Ingranaggio impostazioni ----
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(
                    Icons.Rounded.Eco, "Risparmio di emergenza", hazeState,
                    if (ecoOn) Color(0xFF00E676) else colors.c1, null, size = 42.dp,
                ) {
                    val on = !settings.emergency
                    store.update { it.copy(emergency = on) }
                    toast = if (on) "Risparmio di emergenza attivo" else "Risparmio di emergenza disattivato"
                    poke()
                }
                GlassIconButton(Icons.Rounded.Tune, "Impostazioni", hazeState, colors.c1, null, size = 42.dp) { showSettings = true }
            }
        }

        // ---- Indicatore eco sempre visibile (piccolo) ----
        AnimatedVisibility(
            visible = ecoOn && !controlsVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(22.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Icon(Icons.Rounded.Eco, null, tint = Color(0xFF00E676).copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }

        // ---- Messaggio temporaneo ----
        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut(),
        ) {
            GlassChip(toast ?: "", hazeState, Color(0xFF00E676)) {}
        }

        // ---- Feedback swipe al centro ----
        AnimatedVisibility(
            visible = skipVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.6f) + fadeIn(),
            exit = scaleOut(targetScale = 1.3f) + fadeOut(),
        ) {
            LiquidGlass(hazeState, Modifier.size(76.dp), CircleShape, colors.c1, beatState) {
                Icon(skipIcon, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp))
            }
        }

        // ---- In basso al centro: avvisi + controlli ----
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val status = analyzer.status
            AnimatedVisibility(visible = !micGranted || (status != null && controlsVisible), enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!micGranted) GlassChip("Tocca qui per attivare l'audio reattivo", hazeState, colors.c1, onRequestMic)
                    else if (status != null) GlassChip(status, hazeState, colors.c1) {}
                    Spacer(Modifier.height(12.dp))
                }
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Text(
                    "Doppio tocco: stile  •  Scorri: brano  •  Tieni premuto: impostazioni",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            AnimatedVisibility(
                visible = eff.showControls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                ControlsBar(track, hazeState, colors.c1, beatState, frame)
            }
        }
    }

    }

    if (showSettings) {
        SettingsSheet(store = store, s = settings, lowBattery = lowBattery, onDismiss = {
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
            fontSize = 12.sp,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** Pillola minimale: copertina tonda, titolo, artista e un mini equalizzatore vivo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoPill(
    track: TrackInfo,
    art: ImageBitmap?,
    hazeState: HazeState,
    accent: Color,
    pulse: FloatState,
    analyzer: AudioAnalyzer,
    frame: LongState,
    onOpen: () -> Unit,
) {
    LiquidGlass(hazeState, Modifier.widthIn(max = 250.dp).height(46.dp), RoundedCornerShape(50), accent, pulse) {
        Row(
            Modifier
                .fillMaxHeight()
                .clickable(onClick = onOpen)
                .padding(start = 5.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                val title = track.title.ifEmpty { if (track.hasSession) "Brano sconosciuto" else "Nessuna riproduzione" }
                val subtitle = track.artist.ifEmpty { if (!track.hasSession) "Avvia la musica" else "" }
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee())
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, modifier = Modifier.basicMarquee())
                }
            }
            Spacer(Modifier.width(10.dp))
            MiniEq(analyzer, frame, track.isPlaying)
        }
    }
}

@Composable
private fun MiniEq(analyzer: AudioAnalyzer, frame: LongState, playing: Boolean) {
    Canvas(Modifier.size(width = 13.dp, height = 14.dp)) {
        frame.longValue
        val bw = size.width / 5f
        val idx = intArrayOf(3, 16, 34)
        for (k in 0..2) {
            val v = if (playing) analyzer.bands[idx[k]] else 0f
            val hh = size.height * (0.18f + 0.82f * v.coerceIn(0f, 1f))
            drawRoundRect(
                Color.White.copy(alpha = 0.85f),
                topLeft = Offset(k * 2f * bw, size.height - hh),
                size = Size(bw, hh),
                cornerRadius = CornerRadius(bw / 2f),
            )
        }
    }
}

/** Capsula di vetro centrata in basso: indietro, play/pausa, avanti, con un filo di avanzamento. */
@Composable
private fun ControlsBar(track: TrackInfo, hazeState: HazeState, accent: Color, pulse: FloatState, frame: LongState) {
    LiquidGlass(hazeState, Modifier.height(56.dp), RoundedCornerShape(50), accent, pulse) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PressIcon(Icons.Rounded.SkipPrevious, "Precedente", 44.dp, filled = false) { NowPlaying.previous() }
            PressIcon(if (track.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play o pausa", 42.dp, filled = true) { NowPlaying.playPause() }
            PressIcon(Icons.Rounded.SkipNext, "Successivo", 44.dp, filled = false) { NowPlaying.next() }
        }
        if (track.durationMs > 0) {
            Canvas(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 26.dp, vertical = 5.dp)
                    .width(110.dp)
                    .height(2.dp)
            ) {
                frame.longValue
                val p = (track.currentPosition().toFloat() / track.durationMs).coerceIn(0f, 1f)
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.15f), cornerRadius = r)
                drawRoundRect(Brush.horizontalGradient(listOf(accent, Color.White)), size = Size(size.width * p, size.height), cornerRadius = r)
            }
        }
    }
}

@Composable
private fun PressIcon(icon: ImageVector, desc: String, size: Dp, filled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, label = "press")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(size * if (filled) 0.55f else 0.62f))
    }
}

@Composable
private fun ClockOverlay() {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) {
        SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now)).replaceFirstChar { it.uppercase() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = Color.White.copy(alpha = 0.92f), fontSize = 64.sp, fontWeight = FontWeight.Thin, letterSpacing = 2.sp)
        Text(date, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, letterSpacing = 1.sp)
    }
}
