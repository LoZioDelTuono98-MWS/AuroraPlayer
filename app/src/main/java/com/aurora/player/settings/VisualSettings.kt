package com.aurora.player.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VisualizerStyle(val label: String) {
    CIRCLE_BARS("Anello"),
    CIRCLE_WAVE("Onda circolare"),
    BARS("Barre"),
    WAVEFORM("Oscilloscopio"),
    LOGO("Logo"),
}

enum class ColorMode(val label: String) {
    ALBUM("Dall'album"),
    RAINBOW("Arcobaleno"),
    NEON("Neon"),
    SUNSET("Tramonto"),
    OCEAN("Oceano"),
    MONO("Bianco"),
}

enum class BackgroundMode(val label: String) {
    ALBUM("Copertina"),
    CUSTOM("Immagine mia"),
    GRADIENT("Gradiente"),
}

enum class ParticleStyle(val label: String) {
    STARDUST("Polvere stellare"),
    EMBERS("Braci"),
    BUBBLES("Bolle"),
}

enum class LogoSource(val label: String) { TEXT("Testo"), IMAGE("Immagine") }

enum class LogoEdge(val label: String) { BARS("Barre"), WAVE("Onda"), DOTS("Punti") }

data class VisualSettings(
    val style: VisualizerStyle = VisualizerStyle.CIRCLE_BARS,
    val colorMode: ColorMode = ColorMode.ALBUM,
    val sensitivity: Float = 1f,
    val glow: Boolean = true,
    val spin: Boolean = true,

    val background: BackgroundMode = BackgroundMode.ALBUM,
    val backgroundDim: Float = 0.35f,
    val backgroundPulse: Boolean = true,
    val customBgVersion: Int = 0,

    val logoSource: LogoSource = LogoSource.TEXT,
    val logoText: String = "AURORA",
    val logoEdge: LogoEdge = LogoEdge.BARS,
    val logoScale: Float = 0.6f,
    val logoVersion: Int = 0,

    val particles: Boolean = true,
    val particleStyle: ParticleStyle = ParticleStyle.STARDUST,
    val particleDensity: Float = 0.6f,
    val fog: Boolean = true,
    val fogIntensity: Float = 0.6f,
    val beatFlash: Boolean = true,
    val bassShake: Boolean = false,
    val showInfoPanel: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("aurora_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<VisualSettings> = _settings.asStateFlow()

    fun update(transform: (VisualSettings) -> VisualSettings) {
        val n = transform(_settings.value)
        _settings.value = n
        save(n)
    }

    private inline fun <reified E : Enum<E>> enumPref(key: String, def: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: def

    private fun load(): VisualSettings {
        val d = VisualSettings()
        return VisualSettings(
            style = enumPref("style", d.style),
            colorMode = enumPref("colorMode", d.colorMode),
            sensitivity = prefs.getFloat("sensitivity", d.sensitivity),
            glow = prefs.getBoolean("glow", d.glow),
            spin = prefs.getBoolean("spin", d.spin),
            background = enumPref("background", d.background),
            backgroundDim = prefs.getFloat("backgroundDim", d.backgroundDim),
            backgroundPulse = prefs.getBoolean("backgroundPulse", d.backgroundPulse),
            customBgVersion = prefs.getInt("customBgVersion", d.customBgVersion),
            logoSource = enumPref("logoSource", d.logoSource),
            logoText = prefs.getString("logoText", d.logoText) ?: d.logoText,
            logoEdge = enumPref("logoEdge", d.logoEdge),
            logoScale = prefs.getFloat("logoScale", d.logoScale),
            logoVersion = prefs.getInt("logoVersion", d.logoVersion),
            particles = prefs.getBoolean("particles", d.particles),
            particleStyle = enumPref("particleStyle", d.particleStyle),
            particleDensity = prefs.getFloat("particleDensity", d.particleDensity),
            fog = prefs.getBoolean("fog", d.fog),
            fogIntensity = prefs.getFloat("fogIntensity", d.fogIntensity),
            beatFlash = prefs.getBoolean("beatFlash", d.beatFlash),
            bassShake = prefs.getBoolean("bassShake", d.bassShake),
            showInfoPanel = prefs.getBoolean("showInfoPanel", d.showInfoPanel),
        )
    }

    private fun save(s: VisualSettings) {
        prefs.edit()
            .putString("style", s.style.name)
            .putString("colorMode", s.colorMode.name)
            .putFloat("sensitivity", s.sensitivity)
            .putBoolean("glow", s.glow)
            .putBoolean("spin", s.spin)
            .putString("background", s.background.name)
            .putFloat("backgroundDim", s.backgroundDim)
            .putBoolean("backgroundPulse", s.backgroundPulse)
            .putInt("customBgVersion", s.customBgVersion)
            .putString("logoSource", s.logoSource.name)
            .putString("logoText", s.logoText)
            .putString("logoEdge", s.logoEdge.name)
            .putFloat("logoScale", s.logoScale)
            .putInt("logoVersion", s.logoVersion)
            .putBoolean("particles", s.particles)
            .putString("particleStyle", s.particleStyle.name)
            .putFloat("particleDensity", s.particleDensity)
            .putBoolean("fog", s.fog)
            .putFloat("fogIntensity", s.fogIntensity)
            .putBoolean("beatFlash", s.beatFlash)
            .putBoolean("bassShake", s.bassShake)
            .putBoolean("showInfoPanel", s.showInfoPanel)
            .apply()
    }
}
