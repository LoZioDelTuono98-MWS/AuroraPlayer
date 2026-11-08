package com.aurora.player.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/** Il pezzo centrale della scena (uno alla volta). */
enum class VisualizerStyle(val label: String) {
    RING("Anello"),
    WAVE("Onda circolare"),
    ORB("Sfera liquida"),
    MANDALA("Mandala"),
    ART("Copertina"),
    LOGO("Logo"),
    NONE("Nessuno"),
}

/** Livelli da sovrapporre liberamente al centro. */
enum class Layer(val label: String) {
    BARS("Spettro"),
    SCOPE("Oscilloscopio"),
    RIDGES("Montagne"),
    GRID("Griglia synthwave"),
    TUNNEL("Tunnel"),
    WARP("Iperspazio"),
    AURORA("Aurora boreale"),
    ORBITS("Orbite"),
    LIGHTNING("Fulmini"),
    VECTOR("Lissajous"),
}

/** Livelli più pesanti, spenti in modalità eco. */
val HeavyLayers = setOf(Layer.WARP, Layer.AURORA, Layer.TUNNEL, Layer.RIDGES)

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
    CONFETTI("Coriandoli"),
    FIREFLIES("Lucciole"),
    SNOW("Neve"),
}

enum class LogoSource(val label: String) { TEXT("Testo"), IMAGE("Immagine") }

enum class LogoEdge(val label: String) { BARS("Barre"), WAVE("Onda"), AURA("Aura"), DOTS("Punti") }

enum class LogoHalo(val label: String, val factor: Float) {
    TIGHT("Aderente", 0.04f),
    SOFT("Morbida", 0.10f),
    CIRCLE("Cerchio", 0f),
}

enum class InfoMode(val label: String) { AUTO("Automatico"), ALWAYS("Sempre"), NEVER("Mai") }

enum class FpsLimit(val label: String, val fps: Int) { MAX("Massimo", 0), F60("60 fps", 60), F30("30 fps", 30) }

private fun argb(v: Long) = v.toInt()

data class VisualSettings(
    val style: VisualizerStyle = VisualizerStyle.RING,
    val layers: Set<Layer> = emptySet(),
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
    val particleStyles: Set<ParticleStyle> = setOf(ParticleStyle.STARDUST),
    val particleDensity: Float = 0.6f,
    val fog: Boolean = true,
    val fogIntensity: Float = 0.6f,
    val rays: Boolean = false,
    val raysIntensity: Float = 0.6f,
    val shockwaves: Boolean = true,
    val beatFlash: Boolean = true,
    val bassShake: Boolean = false,
    val cameraMotion: Boolean = false,
    val parallax: Boolean = false,
    val touchFx: Boolean = true,

    val infoMode: InfoMode = InfoMode.AUTO,
    val showControls: Boolean = true,
    val swipeToSkip: Boolean = true,
    val clock: Boolean = false,
    val autoSurprise: Boolean = false,

    // Energia (tutto spento di default)
    val fpsLimit: FpsLimit = FpsLimit.MAX,
    val idleSlowdown: Boolean = false,
    val sleepWhenPaused: Boolean = false,
    val sleepMinutes: Int = 5,
    val lightGlass: Boolean = false,
    val ecoMode: Boolean = false,
    val autoEcoBattery: Boolean = false,
    val dimScreen: Boolean = false,
    val dimLevel: Float = 0.2f,
    val emergency: Boolean = false,
)

fun VisualSettings.ecoActive(lowBattery: Boolean) = ecoMode || emergency || (autoEcoBattery && lowBattery)

/**
 * Le impostazioni realmente usate per disegnare: applica eco ed emergenza
 * senza toccare quelle salvate, così spegnendole torna tutto com'era.
 */
fun VisualSettings.effective(lowBattery: Boolean): VisualSettings {
    var s = this
    if (ecoActive(lowBattery)) {
        s = s.copy(
            fpsLimit = if (s.fpsLimit == FpsLimit.MAX) FpsLimit.F60 else s.fpsLimit,
            idleSlowdown = true,
            lightGlass = true,
            glow = false,
            particleDensity = s.particleDensity * 0.4f,
            fog = false,
            rays = false,
            parallax = false,
            layers = s.layers - HeavyLayers,
        )
    }
    if (emergency) {
        s = s.copy(
            fpsLimit = FpsLimit.F30,
            sleepWhenPaused = true,
            sleepMinutes = min(s.sleepMinutes, 2),
            dimScreen = true,
            dimLevel = min(s.dimLevel, 0.25f),
            particles = false,
            shockwaves = false,
            beatFlash = false,
            bassShake = false,
            cameraMotion = false,
            touchFx = false,
            layers = emptySet(),
            backgroundPulse = false,
            spin = false,
        )
    }
    return s
}

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

    private inline fun <reified E : Enum<E>> enumSet(key: String, def: Set<E>): Set<E> {
        val raw = prefs.getString(key, null) ?: return def
        if (raw.isEmpty()) return emptySet()
        return raw.split(',').mapNotNull { n -> enumValues<E>().firstOrNull { it.name == n } }.toSet()
    }

    private fun intList(key: String, def: List<Int>): List<Int> =
        prefs.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == def.size } ?: def

    private fun load(): VisualSettings {
        val d = VisualSettings()
        // Migrazione dalla v0.2: "Barre" e "Oscilloscopio" ora sono livelli
        var style = enumPref("style", d.style)
        var layers = enumSet("layers", d.layers)
        when (prefs.getString("style", null)) {
            "CIRCLE_BARS" -> style = VisualizerStyle.RING
            "CIRCLE_WAVE" -> style = VisualizerStyle.WAVE
            "BARS" -> { style = VisualizerStyle.ART; layers = layers + Layer.BARS }
            "WAVEFORM" -> { style = VisualizerStyle.ART; layers = layers + Layer.SCOPE }
        }
        val oldParticle = prefs.getString("particleStyle", null)
            ?.let { n -> ParticleStyle.entries.firstOrNull { it.name == n } }
        return VisualSettings(
            style = style,
            layers = layers,
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
            particleStyles = enumSet("particleStyles", oldParticle?.let { setOf(it) } ?: d.particleStyles),
            particleDensity = prefs.getFloat("particleDensity", d.particleDensity),
            fog = prefs.getBoolean("fog", d.fog),
            fogIntensity = prefs.getFloat("fogIntensity", d.fogIntensity),
            rays = prefs.getBoolean("rays", d.rays),
            raysIntensity = prefs.getFloat("raysIntensity", d.raysIntensity),
            shockwaves = prefs.getBoolean("shockwaves", d.shockwaves),
            beatFlash = prefs.getBoolean("beatFlash", d.beatFlash),
            bassShake = prefs.getBoolean("bassShake", d.bassShake),
            cameraMotion = prefs.getBoolean("cameraMotion", d.cameraMotion),
            parallax = prefs.getBoolean("parallax", d.parallax),
            touchFx = prefs.getBoolean("touchFx", d.touchFx),
            infoMode = enumPref("infoMode", d.infoMode),
            showControls = prefs.getBoolean("showControls", d.showControls),
            swipeToSkip = prefs.getBoolean("swipeToSkip", d.swipeToSkip),
            clock = prefs.getBoolean("clock", d.clock),
            autoSurprise = prefs.getBoolean("autoSurprise", d.autoSurprise),
            fpsLimit = enumPref("fpsLimit", d.fpsLimit),
            idleSlowdown = prefs.getBoolean("idleSlowdown", d.idleSlowdown),
            sleepWhenPaused = prefs.getBoolean("sleepWhenPaused", d.sleepWhenPaused),
            sleepMinutes = prefs.getInt("sleepMinutes", d.sleepMinutes),
            lightGlass = prefs.getBoolean("lightGlass", d.lightGlass),
            ecoMode = prefs.getBoolean("ecoMode", d.ecoMode),
            autoEcoBattery = prefs.getBoolean("autoEcoBattery", d.autoEcoBattery),
            dimScreen = prefs.getBoolean("dimScreen", d.dimScreen),
            dimLevel = prefs.getFloat("dimLevel", d.dimLevel),
            emergency = prefs.getBoolean("emergency", d.emergency),
        )
    }

    private fun save(s: VisualSettings) {
        prefs.edit()
            .putString("style", s.style.name)
            .putString("layers", s.layers.joinToString(",") { it.name })
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
            .putString("particleStyles", s.particleStyles.joinToString(",") { it.name })
            .remove("particleStyle")
            .putFloat("particleDensity", s.particleDensity)
            .putBoolean("fog", s.fog)
            .putFloat("fogIntensity", s.fogIntensity)
            .putBoolean("rays", s.rays)
            .putFloat("raysIntensity", s.raysIntensity)
            .putBoolean("shockwaves", s.shockwaves)
            .putBoolean("beatFlash", s.beatFlash)
            .putBoolean("bassShake", s.bassShake)
            .putBoolean("cameraMotion", s.cameraMotion)
            .putBoolean("parallax", s.parallax)
            .putBoolean("touchFx", s.touchFx)
            .putString("infoMode", s.infoMode.name)
            .putBoolean("showControls", s.showControls)
            .putBoolean("swipeToSkip", s.swipeToSkip)
            .putBoolean("clock", s.clock)
            .putBoolean("autoSurprise", s.autoSurprise)
            .putString("fpsLimit", s.fpsLimit.name)
            .putBoolean("idleSlowdown", s.idleSlowdown)
            .putBoolean("sleepWhenPaused", s.sleepWhenPaused)
            .putInt("sleepMinutes", s.sleepMinutes)
            .putBoolean("lightGlass", s.lightGlass)
            .putBoolean("ecoMode", s.ecoMode)
            .putBoolean("autoEcoBattery", s.autoEcoBattery)
            .putBoolean("dimScreen", s.dimScreen)
            .putFloat("dimLevel", s.dimLevel)
            .putBoolean("emergency", s.emergency)
            .apply()
    }
}
