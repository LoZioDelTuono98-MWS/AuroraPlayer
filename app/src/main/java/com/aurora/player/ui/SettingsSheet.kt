package com.aurora.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.player.logo.LogoFactory
import com.aurora.player.settings.BackgroundMode
import com.aurora.player.settings.ColorMode
import com.aurora.player.settings.LogoEdge
import com.aurora.player.settings.LogoSource
import com.aurora.player.settings.ParticleStyle
import com.aurora.player.settings.SettingsStore
import com.aurora.player.settings.VisualSettings
import com.aurora.player.settings.VisualizerStyle
import com.aurora.player.util.ImageFiles
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Impostazioni volutamente essenziali: 4 schede, solo ciò che cambia davvero il risultato.
 * Lo sfondo resta visibile dietro, così ogni modifica si vede dal vivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(store: SettingsStore, s: VisualSettings, onDismiss: () -> Unit) {
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
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color.White) {
                listOf("Stile", "Sfondo", "Logo", "Effetti").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    0 -> {
                        SectionTitle("Visualizzatore")
                        ChipGroup(VisualizerStyle.entries, s.style, { it.label }) { v -> store.update { it.copy(style = v) } }
                        SectionTitle("Colori")
                        ChipGroup(ColorMode.entries, s.colorMode, { it.label }) { v -> store.update { it.copy(colorMode = v) } }
                        SliderRow("Sensibilità", s.sensitivity, 0.4f..2.2f, { "x" + "%.1f".format(it) }) { v -> store.update { it.copy(sensitivity = v) } }
                        SwitchRow("Bagliore neon", s.glow) { v -> store.update { it.copy(glow = v) } }
                        SwitchRow("Copertina che ruota", s.spin) { v -> store.update { it.copy(spin = v) } }
                    }
                    1 -> {
                        SectionTitle("Sfondo")
                        ChipGroup(BackgroundMode.entries, s.background, { it.label }) { v ->
                            if (v == BackgroundMode.CUSTOM && s.customBgVersion == 0) pickBg.launch(imageOnly)
                            else store.update { it.copy(background = v) }
                        }
                        Button(onClick = { pickBg.launch(imageOnly) }, colors = whiteButton()) { Text("Scegli un'immagine dalla galleria") }
                        SliderRow("Oscuramento", s.backgroundDim, 0f..0.9f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(backgroundDim = v) } }
                        SwitchRow("Zoom a ritmo dei bassi", s.backgroundPulse) { v -> store.update { it.copy(backgroundPulse = v) } }
                    }
                    2 -> {
                        Text(
                            "Le frequenze nascono dal perimetro del tuo logo. Per un contorno preciso usa un PNG con sfondo trasparente (funziona anche un logo su sfondo a tinta unita).",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        if (s.style != VisualizerStyle.LOGO) {
                            Button(onClick = { store.update { it.copy(style = VisualizerStyle.LOGO) } }, colors = whiteButton()) { Text("Attiva lo stile Logo") }
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
                        SectionTitle("Bordo ritmato")
                        ChipGroup(LogoEdge.entries, s.logoEdge, { it.label }) { v -> store.update { it.copy(logoEdge = v) } }
                        SliderRow("Dimensione", s.logoScale, 0.3f..1f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(logoScale = v) } }
                    }
                    else -> {
                        SwitchRow("Particelle", s.particles) { v -> store.update { it.copy(particles = v) } }
                        if (s.particles) {
                            ChipGroup(ParticleStyle.entries, s.particleStyle, { it.label }) { v -> store.update { it.copy(particleStyle = v) } }
                            SliderRow("Quantità", s.particleDensity, 0.1f..1f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(particleDensity = v) } }
                        }
                        SwitchRow("Foschia colorata", s.fog) { v -> store.update { it.copy(fog = v) } }
                        if (s.fog) {
                            SliderRow("Intensità foschia", s.fogIntensity, 0.1f..1f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(fogIntensity = v) } }
                        }
                        SwitchRow("Flash sul battito", s.beatFlash) { v -> store.update { it.copy(beatFlash = v) } }
                        SwitchRow("Scossa sui bassi", s.bassShake) { v -> store.update { it.copy(bassShake = v) } }
                        SwitchRow("Pannello info brano", s.showInfoPanel) { v -> store.update { it.copy(showInfoPanel = v) } }
                        TextButton(onClick = { store.update { VisualSettings(customBgVersion = it.customBgVersion, logoVersion = it.logoVersion) } }) {
                            Text("Ripristina impostazioni predefinite", color = Color.White.copy(alpha = 0.7f))
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

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
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
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
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
