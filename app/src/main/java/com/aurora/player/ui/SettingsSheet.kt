package com.aurora.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.player.logo.LogoFactory
import com.aurora.player.settings.BackgroundMode
import com.aurora.player.settings.ColorMode
import com.aurora.player.settings.FpsLimit
import com.aurora.player.settings.Layer
import com.aurora.player.settings.surprise
import com.aurora.player.settings.InfoMode
import com.aurora.player.settings.LogoEdge
import com.aurora.player.settings.LogoHalo
import com.aurora.player.settings.LogoSource
import com.aurora.player.settings.ParticleStyle
import com.aurora.player.settings.ScenePresets
import com.aurora.player.settings.SettingsStore
import com.aurora.player.settings.VisualSettings
import com.aurora.player.settings.VisualizerStyle
import com.aurora.player.util.ImageFiles
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Tabs = listOf("Scene", "Player", "Colori", "Sfondo", "Logo", "Effetti", "Schermo", "Energia")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(store: SettingsStore, s: VisualSettings, lowBattery: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (ImageFiles.copyFromUri(context, uri, ImageFiles.BG_FILE)) {
                store.update { it.copy(background = BackgroundMode.CUSTOM, customBgVersion = it.customBgVersion + 1) }
            }
        }
    }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (ImageFiles.copyFromUri(context, uri, LogoFactory.LOGO_FILE)) {
                store.update { it.copy(logoSource = LogoSource.IMAGE, style = VisualizerStyle.LOGO, logoVersion = it.logoVersion + 1) }
            }
        }
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    fun pct(v: Float) = "${(v * 100).roundToInt()}%"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF2121218),
        contentColor = Color.White,
        scrimColor = Color.Transparent,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Personalizza", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.padding(4.dp))
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color.White, edgePadding = 12.dp) {
                Tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    0 -> {
                        Button(
                            onClick = { store.update { surprise(it) } },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = whiteButton(),
                        ) { Text("✦  Sorprendimi", fontWeight = FontWeight.Bold) }
                        SwitchRow("Nuova scena a ogni brano", s.autoSurprise) { v -> store.update { it.copy(autoSurprise = v) } }
                        SectionTitle("Scene pronte")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScenePresets.forEach { p ->
                                Column(
                                    Modifier
                                        .width(92.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { store.update(p.apply) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier
                                            .size(92.dp, 64.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Brush.linearGradient(p.preview.map { Color(it) }))
                                    )
                                    Text(p.name, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                    1 -> {
                        SectionTitle("Centro")
                        ChipGroup(VisualizerStyle.entries, s.style, { it.label }) { v -> store.update { it.copy(style = v) } }
                        SectionTitle("Livelli da mischiare")
                        Text("Attivane quanti vuoi: si sovrappongono al centro.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        MultiChipGroup(Layer.entries, s.layers, { it.label }) { v ->
                            store.update { it.copy(layers = if (v in it.layers) it.layers - v else it.layers + v) }
                        }
                        SliderRow("Sensibilità", s.sensitivity, 0.4f..2.2f, { "x" + "%.1f".format(it) }) { v -> store.update { it.copy(sensitivity = v) } }
                        SwitchRow("Bagliore neon", s.glow) { v -> store.update { it.copy(glow = v) } }
                        SwitchRow("Copertina che ruota", s.spin) { v -> store.update { it.copy(spin = v) } }
                    }
                    2 -> {
                        SectionTitle("Colori del visualizzatore")
                        ChipGroup(ColorMode.entries, s.colorMode, { it.label }) { v -> store.update { it.copy(colorMode = v) } }
                        if (s.colorMode == ColorMode.CUSTOM) {
                            ColorStopsEditor("Tocca un cerchio per cambiarlo", s.customColors) { l -> store.update { it.copy(customColors = l) } }
                        } else {
                            TextButton(onClick = { store.update { it.copy(colorMode = ColorMode.CUSTOM) } }) {
                                Text("Scegli i tuoi colori →", color = Color.White)
                            }
                        }
                    }
                    3 -> {
                        SectionTitle("Sfondo")
                        ChipGroup(BackgroundMode.entries, s.background, { it.label }) { v ->
                            if (v == BackgroundMode.CUSTOM && s.customBgVersion == 0) pickBg.launch(imageOnly)
                            else store.update { it.copy(background = v) }
                        }
                        when (s.background) {
                            BackgroundMode.CUSTOM ->
                                Button(onClick = { pickBg.launch(imageOnly) }, colors = whiteButton()) { Text("Cambia immagine") }
                            BackgroundMode.GRADIENT -> {
                                ColorStopsEditor("Colori del gradiente", s.bgColors) { l -> store.update { it.copy(bgColors = l) } }
                                SwitchRow("Radiale (dal centro)", s.bgRadial) { v -> store.update { it.copy(bgRadial = v) } }
                                SwitchRow("In movimento", s.bgAnimated) { v -> store.update { it.copy(bgAnimated = v) } }
                            }
                            BackgroundMode.SOLID ->
                                ColorStopsEditor("Colore", listOf(s.bgColors[0])) { l ->
                                    store.update { it.copy(bgColors = listOf(l[0], it.bgColors[1], it.bgColors[2])) }
                                }
                            BackgroundMode.ALBUM -> Unit
                        }
                        SliderRow("Oscuramento", s.backgroundDim, 0f..0.9f, { pct(it) }) { v -> store.update { it.copy(backgroundDim = v) } }
                        SwitchRow("Zoom a ritmo dei bassi", s.backgroundPulse) { v -> store.update { it.copy(backgroundPulse = v) } }
                    }
                    4 -> {
                        Text(
                            "Il logo resta pulito al centro: attorno nasce una forma unica e morbida da cui escono le frequenze. Meglio un PNG con sfondo trasparente.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        if (s.style != VisualizerStyle.LOGO) {
                            Button(onClick = { store.update { it.copy(style = VisualizerStyle.LOGO) } }, colors = whiteButton()) { Text("Metti il logo al centro") }
                        }
                        SectionTitle("Origine")
                        ChipGroup(LogoSource.entries, s.logoSource, { it.label }) { v ->
                            if (v == LogoSource.IMAGE && s.logoVersion == 0) pickLogo.launch(imageOnly)
                            else store.update { it.copy(logoSource = v, style = VisualizerStyle.LOGO) }
                        }
                        if (s.logoSource == LogoSource.TEXT) {
                            var text by remember { mutableStateOf(s.logoText) }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { t ->
                                    text = t.take(24)
                                    store.update { it.copy(logoText = text, style = VisualizerStyle.LOGO) }
                                },
                                label = { Text("Testo del logo") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Button(onClick = { pickLogo.launch(imageOnly) }, colors = whiteButton()) { Text("Scegli il logo (PNG)") }
                        }
                        SectionTitle("Forma attorno al logo")
                        ChipGroup(LogoHalo.entries, s.logoHalo, { it.label }) { v -> store.update { it.copy(logoHalo = v) } }
                        SectionTitle("Effetto sul bordo")
                        ChipGroup(LogoEdge.entries, s.logoEdge, { it.label }) { v -> store.update { it.copy(logoEdge = v) } }
                        SliderRow("Dimensione", s.logoScale, 0.25f..0.9f, { pct(it) }) { v -> store.update { it.copy(logoScale = v) } }
                        SwitchRow("Colora il logo con la palette", s.logoTinted) { v -> store.update { it.copy(logoTinted = v) } }
                    }
                    5 -> {
                        SwitchRow("Particelle", s.particles) { v -> store.update { it.copy(particles = v) } }
                        if (s.particles) {
                            Text("Puoi combinarne più di una.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            MultiChipGroup(ParticleStyle.entries, s.particleStyles, { it.label }) { v ->
                                store.update { it.copy(particleStyles = if (v in it.particleStyles) it.particleStyles - v else it.particleStyles + v) }
                            }
                            SliderRow("Quantità", s.particleDensity, 0.1f..1f, { pct(it) }) { v -> store.update { it.copy(particleDensity = v) } }
                        }
                        SwitchRow("Foschia colorata", s.fog) { v -> store.update { it.copy(fog = v) } }
                        if (s.fog) SliderRow("Intensità foschia", s.fogIntensity, 0.1f..1f, { pct(it) }) { v -> store.update { it.copy(fogIntensity = v) } }
                        SwitchRow("Raggi di luce", s.rays) { v -> store.update { it.copy(rays = v) } }
                        if (s.rays) SliderRow("Intensità raggi", s.raysIntensity, 0.1f..1f, { pct(it) }) { v -> store.update { it.copy(raysIntensity = v) } }
                        SwitchRow("Onde d'urto sul battito", s.shockwaves) { v -> store.update { it.copy(shockwaves = v) } }
                        SwitchRow("Flash sul battito", s.beatFlash) { v -> store.update { it.copy(beatFlash = v) } }
                        SwitchRow("Camera a ritmo (zoom e oscillazione)", s.cameraMotion) { v -> store.update { it.copy(cameraMotion = v) } }
                        SwitchRow("Scossa sui bassi", s.bassShake) { v -> store.update { it.copy(bassShake = v) } }
                        SwitchRow("Parallasse inclinando il telefono", s.parallax) { v -> store.update { it.copy(parallax = v) } }
                        SwitchRow("Tocchi luminosi", s.touchFx) { v -> store.update { it.copy(touchFx = v) } }
                    }
                    6 -> {
                        SectionTitle("Info del brano")
                        ChipGroup(InfoMode.entries, s.infoMode, { it.label }) { v -> store.update { it.copy(infoMode = v) } }
                        Text(
                            "Automatico: appare al cambio canzone o quando tocchi lo schermo, poi scompare.",
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                        )
                        SwitchRow("Pulsanti di controllo", s.showControls) { v -> store.update { it.copy(showControls = v) } }
                        SwitchRow("Scorri per cambiare brano", s.swipeToSkip) { v -> store.update { it.copy(swipeToSkip = v) } }
                        SwitchRow("Orologio", s.clock) { v -> store.update { it.copy(clock = v) } }
                        TextButton(onClick = {
                            store.update {
                                VisualSettings(customBgVersion = it.customBgVersion, logoVersion = it.logoVersion, logoText = it.logoText)
                            }
                        }) { Text("Ripristina impostazioni predefinite", color = Color.White.copy(alpha = 0.7f)) }
                    }
                    else -> {
                        val on = s.emergency
                        Button(
                            onClick = { store.update { it.copy(emergency = !on) } },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (on) Color(0xFF00E676) else Color(0xFFFF3D3D),
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text(if (on) "Risparmio di emergenza ATTIVO · tocca per spegnere" else "⚡  Risparmio di emergenza", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Attiva tutto in un colpo: 30 fps, vetro leggero, niente effetti extra, schermo più scuro e spegnimento dopo 2 minuti di pausa. " +
                                "Le tue impostazioni restano salvate e tornano appena lo spegni. Lo trovi anche nella foglia in alto a destra.",
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f),
                        )
                        if (s.autoEcoBattery && lowBattery && !on) {
                            Text("Batteria sotto il 20%: modalità eco attiva in automatico.", fontSize = 13.sp, color = Color(0xFF00E676))
                        }
                        SectionTitle("Fotogrammi al secondo")
                        ChipGroup(FpsLimit.entries, s.fpsLimit, { it.label }) { v -> store.update { it.copy(fpsLimit = v) } }
                        Text("A 60 fps la differenza si nota appena, ma la grafica consuma molto meno.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        SwitchRow("Rallenta quando la musica è in pausa o in silenzio", s.idleSlowdown) { v -> store.update { it.copy(idleSlowdown = v) } }
                        SwitchRow("Lascia spegnere lo schermo in pausa", s.sleepWhenPaused) { v -> store.update { it.copy(sleepWhenPaused = v) } }
                        if (s.sleepWhenPaused) {
                            SliderRow("Dopo", s.sleepMinutes.toFloat(), 1f..30f, { "${it.roundToInt()} min" }, steps = 28) { v ->
                                store.update { it.copy(sleepMinutes = v.roundToInt()) }
                            }
                        }
                        SwitchRow("Vetro leggero (senza sfocatura)", s.lightGlass) { v -> store.update { it.copy(lightGlass = v) } }
                        SwitchRow("Modalità eco (meno effetti, stesso stile)", s.ecoMode) { v -> store.update { it.copy(ecoMode = v) } }
                        SwitchRow("Eco automatico con batteria sotto il 20%", s.autoEcoBattery) { v -> store.update { it.copy(autoEcoBattery = v) } }
                        SwitchRow("Luminosità ridotta", s.dimScreen) { v -> store.update { it.copy(dimScreen = v) } }
                        if (s.dimScreen) {
                            SliderRow("Livello", s.dimLevel, 0.02f..0.6f, { pct(it) }) { v -> store.update { it.copy(dimLevel = v) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun whiteButton() = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.06f),
                    labelColor = Color.White,
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChipGroup(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o in selected,
                onClick = { onToggle(o) },
                label = { Text(label(o)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.06f),
                    labelColor = Color.White,
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black,
                ),
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(format(value), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}
