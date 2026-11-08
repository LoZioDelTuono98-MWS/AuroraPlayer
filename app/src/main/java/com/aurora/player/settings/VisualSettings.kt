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
    CUSTOM("I miei colori"),
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
    SOLID("Tinta unita"),
}

enum class ParticleStyle(val label: String) {
    STARDUST("Polvere stellare"),
    EMBERS("Braci"),
    BUBBLES("Bolle"),
}

enum class LogoSource(val label: String) { TEXT("Testo"), IMAGE("Immagine") }

enum class LogoEdge(val label: String) { BARS("Barre"), WAVE("Onda"), AURA("Aura"), DOTS("Punti") }

/** Forma unica che avvolge il logo: da lì partono le frequenze. */
enum class LogoHalo(val label: String, val factor: Float) {
    TIGHT("Aderente", 0.04f),
    SOFT("Morbida", 0.10f),
    CIRCLE("Cerchio", 0f),
}

enum class InfoMode(val label: String) { AUTO("Automatico"), ALWAYS("Sempre"), NEVER("Mai") }

private fun argb(v: Long) = v.toInt()

data class VisualSettings(
    val style: VisualizerStyle = VisualizerStyle.CIRCLE_BARS,
    val colorMode: ColorMode = ColorMode.ALBUM,
    val customColors: List<Int> = listOf(argb(0xFF8A5CFF), argb(0xFF00E5FF), argb(0xFFFF4FA3)),
    val sensitivity: Float = 1f,
    val glow: Boolean = true,
    val spin: Boolean = true,

    val background: BackgroundMode = BackgroundMode.ALBUM,
    val bgColors: List<Int> = listOf(argb(0xFF1B0B3A), argb(0xFF0A2A5E), argb(0xFF3A0B2E)),
    val bgRadial: Boolean = false,
    val bgAnimated: Boolean = true,
    val backgroundDim: Float = 0.3f,
    val backgroundPulse: Boolean = true,
    val customBgVersion: Int = 0,

    val logoSource: LogoSource = LogoSource.TEXT,
    val logoText: String = "AURORA",
    val logoEdge: LogoEdge = LogoEdge.BARS,
    val logoHalo: LogoHalo = LogoHalo.SOFT,
    val logoScale: Float = 0.5f,
    val logoTinted: Boolean = false,
    val logoVersion: Int = 0,

    val particles: Boolean = true,
    val particleStyle: ParticleStyle = ParticleStyle.STARDUST,
    val particleDensity: Float = 0.6f,
    val fog: Boolean = true,
    val fogIntensity: Float = 0.6f,
    val rays: Boolean = false,
    val raysIntensity: Float = 0.6f,
    val shockwaves: Boolean = true,
    val beatFlash: Boolean = true,
    val bassShake: Boolean = false,

    val infoMode: InfoMode = InfoMode.AUTO,
    val showControls: Boolean = true,
    val swipeToSkip: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("aurora_settings_v2", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<VisualSettings> = _settings.asStateFlow()

    fun update(transform: (VisualSettings) -> VisualSettings) {
        val n = transform(_settings.value)
        _settings.value = n
        save(n)
    }

    private inline fun <reified E : Enum<E>> enumPref(key: String, def: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: def

    private fun intList(key: String, def: List<Int>): List<Int> =
        prefs.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == def.size } ?: def

    private fun load(): VisualSettings {
        val d = VisualSettings()
        return VisualSettings(
            style = enumPref("style", d.style),
            colorMode = enumPref("colorMode", d.colorMode),
            customColors = intList("customColors", d.customColors),
            sensitivity = prefs.getFloat("sensitivity", d.sensitivity),
            glow = prefs.getBoolean("glow", d.glow),
            spin = prefs.getBoolean("spin", d.spin),
            background = enumPref("background", d.background),
            bgColors = intList("bgColors", d.bgColors),
            bgRadial = prefs.getBoolean("bgRadial", d.bgRadial),
            bgAnimated = prefs.getBoolean("bgAnimated", d.bgAnimated),
            backgroundDim = prefs.getFloat("backgroundDim", d.backgroundDim),
            backgroundPulse = prefs.getBoolean("backgroundPulse", d.backgroundPulse),
            customBgVersion = prefs.getInt("customBgVersion", d.customBgVersion),
            logoSource = enumPref("logoSource", d.logoSource),
            logoText = prefs.getString("logoText", d.logoText) ?: d.logoText,
            logoEdge = enumPref("logoEdge", d.logoEdge),
            logoHalo = enumPref("logoHalo", d.logoHalo),
            logoScale = prefs.getFloat("logoScale", d.logoScale),
            logoTinted = prefs.getBoolean("logoTinted", d.logoTinted),
            logoVersion = prefs.getInt("logoVersion", d.logoVersion),
            particles = prefs.getBoolean("particles", d.particles),
            particleStyle = enumPref("particleStyle", d.particleStyle),
            particleDensity = prefs.getFloat("particleDensity", d.particleDensity),
            fog = prefs.getBoolean("fog", d.fog),
            fogIntensity = prefs.getFloat("fogIntensity", d.fogIntensity),
            rays = prefs.getBoolean("rays", d.rays),
            raysIntensity = prefs.getFloat("raysIntensity", d.raysIntensity),
            shockwaves = prefs.getBoolean("shockwaves", d.shockwaves),
            beatFlash = prefs.getBoolean("beatFlash", d.beatFlash),
            bassShake = prefs.getBoolean("bassShake", d.bassShake),
            infoMode = enumPref("infoMode", d.infoMode),
            showControls = prefs.getBoolean("showControls", d.showControls),
            swipeToSkip = prefs.getBoolean("swipeToSkip", d.swipeToSkip),
        )
    }

    private fun save(s: VisualSettings) {
        prefs.edit()
            .putString("style", s.style.name)
            .putString("colorMode", s.colorMode.name)
            .putString("customColors", s.customColors.joinToString(","))
            .putFloat("sensitivity", s.sensitivity)
            .putBoolean("glow", s.glow)
            .putBoolean("spin", s.spin)
            .putString("background", s.background.name)
            .putString("bgColors", s.bgColors.joinToString(","))
            .putBoolean("bgRadial", s.bgRadial)
            .putBoolean("bgAnimated", s.bgAnimated)
            .putFloat("backgroundDim", s.backgroundDim)
            .putBoolean("backgroundPulse", s.backgroundPulse)
            .putInt("customBgVersion", s.customBgVersion)
            .putString("logoSource", s.logoSource.name)
            .putString("logoText", s.logoText)
            .putString("logoEdge", s.logoEdge.name)
            .putString("logoHalo", s.logoHalo.name)
            .putFloat("logoScale", s.logoScale)
            .putBoolean("logoTinted", s.logoTinted)
            .putInt("logoVersion", s.logoVersion)
            .putBoolean("particles", s.particles)
            .putString("particleStyle", s.particleStyle.name)
            .putFloat("particleDensity", s.particleDensity)
            .putBoolean("fog", s.fog)
            .putFloat("fogIntensity", s.fogIntensity)
            .putBoolean("rays", s.rays)
            .putFloat("raysIntensity", s.raysIntensity)
            .putBoolean("shockwaves", s.shockwaves)
            .putBoolean("beatFlash", s.beatFlash)
            .putBoolean("bassShake", s.bassShake)
            .putString("infoMode", s.infoMode.name)
            .putBoolean("showControls", s.showControls)
            .putBoolean("swipeToSkip", s.swipeToSkip)
            .apply()
    }
}
