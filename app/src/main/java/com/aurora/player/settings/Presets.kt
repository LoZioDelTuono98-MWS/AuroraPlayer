package com.aurora.player.settings

import kotlin.random.Random

private fun c(v: Long) = v.toInt()

/** Scene pronte: cambiano solo l'aspetto, mai le impostazioni di energia. */
data class ScenePreset(val name: String, val preview: List<Int>, val apply: (VisualSettings) -> VisualSettings)

private fun VisualSettings.clean() = copy(
    layers = emptySet(), particles = true, fog = false, rays = false, shockwaves = false,
    beatFlash = true, bassShake = false, cameraMotion = false, glow = true, clock = false,
)

val ScenePresets = listOf(
    ScenePreset("Notte neon", listOf(c(0xFF00E5FF), c(0xFFFF00E5), c(0xFF120A2E))) {
        it.clean().copy(
            style = VisualizerStyle.RING, layers = setOf(Layer.WARP), colorMode = ColorMode.NEON,
            background = BackgroundMode.GRADIENT, bgColors = listOf(c(0xFF0A0420), c(0xFF1A0B4A), c(0xFF02101F)),
            bgRadial = true, bgAnimated = true, backgroundDim = 0.1f,
            particleStyles = setOf(ParticleStyle.STARDUST), particleDensity = 0.5f,
            fog = true, fogIntensity = 0.5f, rays = true, raysIntensity = 0.4f, shockwaves = true,
        )
    },
    ScenePreset("Synthwave", listOf(c(0xFFFF2E97), c(0xFF7B2FFF), c(0xFF00E5FF))) {
        it.clean().copy(
            style = VisualizerStyle.ORB, layers = setOf(Layer.GRID, Layer.AURORA),
            colorMode = ColorMode.CUSTOM, customColors = listOf(c(0xFFFF2E97), c(0xFF00E5FF), c(0xFFFFB830)),
            background = BackgroundMode.GRADIENT, bgColors = listOf(c(0xFF1A0530), c(0xFF2D0A4E), c(0xFF0A0418)),
            bgRadial = false, bgAnimated = true, backgroundDim = 0f,
            particleStyles = setOf(ParticleStyle.FIREFLIES), particleDensity = 0.5f, cameraMotion = true,
        )
    },
    ScenePreset("Tempesta", listOf(c(0xFF4FC3FF), c(0xFFE0F7FF), c(0xFF0A1630))) {
        it.clean().copy(
            style = VisualizerStyle.RING, layers = setOf(Layer.TUNNEL, Layer.LIGHTNING),
            colorMode = ColorMode.CUSTOM, customColors = listOf(c(0xFF4FC3FF), c(0xFFB388FF), c(0xFFE0F7FF)),
            background = BackgroundMode.SOLID, bgColors = listOf(c(0xFF03060F), it.bgColors[1], it.bgColors[2]),
            backgroundDim = 0f, particleStyles = setOf(ParticleStyle.SNOW), particleDensity = 0.6f,
            shockwaves = true, bassShake = true,
        )
    },
    ScenePreset("Galassia", listOf(c(0xFF7C4DFF), c(0xFF00E5A0), c(0xFFFF4081))) {
        it.clean().copy(
            style = VisualizerStyle.MANDALA, layers = setOf(Layer.WARP, Layer.ORBITS), colorMode = ColorMode.RAINBOW,
            background = BackgroundMode.SOLID, bgColors = listOf(c(0xFF020208), it.bgColors[1], it.bgColors[2]),
            backgroundDim = 0f, particleStyles = setOf(ParticleStyle.FIREFLIES), particleDensity = 0.7f,
            fog = true, fogIntensity = 0.8f, cameraMotion = true,
        )
    },
    ScenePreset("Pulsar", listOf(c(0xFFFFFFFF), c(0xFF666666), c(0xFF000000))) {
        it.clean().copy(
            style = VisualizerStyle.NONE, layers = setOf(Layer.RIDGES), colorMode = ColorMode.MONO, glow = false,
            background = BackgroundMode.SOLID, bgColors = listOf(c(0xFF050508), it.bgColors[1], it.bgColors[2]),
            backgroundDim = 0f, particles = false, beatFlash = false,
        )
    },
    ScenePreset("Tramonto", listOf(c(0xFFFF6B35), c(0xFFFF2E63), c(0xFFFFC93C))) {
        it.clean().copy(
            style = VisualizerStyle.WAVE, colorMode = ColorMode.SUNSET,
            background = BackgroundMode.GRADIENT, bgColors = listOf(c(0xFF2B0A1E), c(0xFF5A1A1A), c(0xFF1A0A2B)),
            bgRadial = false, bgAnimated = true, backgroundDim = 0.15f,
            particleStyles = setOf(ParticleStyle.EMBERS), particleDensity = 0.6f,
            fog = true, fogIntensity = 0.7f, shockwaves = true,
        )
    },
    ScenePreset("Oceano", listOf(c(0xFF00B4D8), c(0xFF4361EE), c(0xFF90E0EF))) {
        it.clean().copy(
            style = VisualizerStyle.ORB, layers = setOf(Layer.SCOPE), colorMode = ColorMode.OCEAN,
            background = BackgroundMode.GRADIENT, bgColors = listOf(c(0xFF001428), c(0xFF003049), c(0xFF00121F)),
            bgRadial = true, bgAnimated = true, backgroundDim = 0.1f,
            particleStyles = setOf(ParticleStyle.BUBBLES), particleDensity = 0.7f,
            fog = true, fogIntensity = 0.5f, rays = true, raysIntensity = 0.3f,
        )
    },
    ScenePreset("Festa", listOf(c(0xFFFFEA00), c(0xFFFF1744), c(0xFF00E676))) {
        it.clean().copy(
            style = VisualizerStyle.ART, layers = setOf(Layer.BARS, Layer.LIGHTNING), colorMode = ColorMode.RAINBOW,
            background = BackgroundMode.ALBUM, backgroundDim = 0.35f,
            particleStyles = setOf(ParticleStyle.CONFETTI, ParticleStyle.STARDUST), particleDensity = 0.8f,
            shockwaves = true, cameraMotion = true, rays = true, raysIntensity = 0.5f,
        )
    },
    ScenePreset("Album", listOf(c(0xFF8A5CFF), c(0xFF00E5FF), c(0xFFFF4FA3))) {
        it.clean().copy(
            style = VisualizerStyle.RING, colorMode = ColorMode.ALBUM,
            background = BackgroundMode.ALBUM, backgroundDim = 0.3f, backgroundPulse = true,
            particleStyles = setOf(ParticleStyle.STARDUST), particleDensity = 0.5f,
            fog = true, fogIntensity = 0.5f, shockwaves = true,
        )
    },
    ScenePreset("Comodino", listOf(c(0xFF1A1A2E), c(0xFF3A3A5A), c(0xFF000000))) {
        it.clean().copy(
            style = VisualizerStyle.ART, colorMode = ColorMode.ALBUM, glow = false,
            background = BackgroundMode.ALBUM, backgroundDim = 0.6f, backgroundPulse = false,
            particleStyles = setOf(ParticleStyle.FIREFLIES), particleDensity = 0.25f,
            beatFlash = false, clock = true, infoMode = InfoMode.AUTO,
        )
    },
    ScenePreset("Minimal", listOf(c(0xFFFFFFFF), c(0xFF888888), c(0xFF000000))) {
        it.clean().copy(
            style = VisualizerStyle.ART, layers = setOf(Layer.BARS), colorMode = ColorMode.MONO, glow = false,
            background = BackgroundMode.SOLID, bgColors = listOf(c(0xFF000000), it.bgColors[1], it.bgColors[2]),
            backgroundDim = 0f, particles = false, beatFlash = false,
        )
    },
)

/** "Sorprendimi": una combinazione casuale ma sensata di centro, livelli, particelle ed effetti. */
fun surprise(s: VisualSettings, rnd: Random = Random.Default): VisualSettings {
    val center = if (s.style == VisualizerStyle.LOGO) VisualizerStyle.LOGO
    else listOf(VisualizerStyle.RING, VisualizerStyle.WAVE, VisualizerStyle.ORB, VisualizerStyle.MANDALA, VisualizerStyle.ART).random(rnd)
    val layers = mutableSetOf<Layer>()
    if (rnd.nextFloat() < 0.6f) layers += listOf(Layer.BARS, Layer.RIDGES, Layer.GRID).random(rnd)
    listOf(Layer.WARP, Layer.TUNNEL, Layer.AURORA, Layer.ORBITS, Layer.LIGHTNING, Layer.VECTOR)
        .shuffled(rnd).take(rnd.nextInt(0, 3)).forEach { layers += it }
    val parts = ParticleStyle.entries.shuffled(rnd).take(rnd.nextInt(1, 3)).toSet()
    val modes = listOf(ColorMode.ALBUM, ColorMode.ALBUM, ColorMode.RAINBOW, ColorMode.NEON, ColorMode.SUNSET, ColorMode.OCEAN)
    return s.copy(
        style = center,
        layers = layers,
        particles = true,
        particleStyles = parts,
        colorMode = modes.random(rnd),
        fog = rnd.nextBoolean(),
        rays = rnd.nextFloat() < 0.35f,
        shockwaves = rnd.nextFloat() < 0.6f,
        cameraMotion = rnd.nextBoolean(),
        glow = true,
    )
}
