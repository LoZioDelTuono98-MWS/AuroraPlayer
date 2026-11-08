package com.aurora.player.settings

private fun c(v: Long) = v.toInt()

/** Scene pronte: un tocco e cambia tutto l'aspetto, senza perdersi nelle impostazioni. */
data class ScenePreset(val name: String, val preview: List<Int>, val apply: (VisualSettings) -> VisualSettings)

val ScenePresets = listOf(
    ScenePreset("Notte neon", listOf(c(0xFF00E5FF), c(0xFFFF00E5), c(0xFF120A2E))) {
        it.copy(
            style = VisualizerStyle.CIRCLE_BARS, colorMode = ColorMode.NEON, glow = true,
            background = BackgroundMode.GRADIENT, bgColors = listOf(c(0xFF0A0420), c(0xFF1A0B4A), c(0xFF02101F)),
            bgRadial = true, bgAnimated = true, backgroundDim = 0.1f,
            particles = true, particleStyle = ParticleStyle.STARDUST, particleDensity = 0.7f,
            fog = true, fogIntensity = 0.5f, rays = true, raysIntensity = 0.5f, shockwaves = true, beatFlash = true,
        )
    },
    ScenePreset("Tramonto", listOf(c(0xFFFF6B35), c(0xFFFF2E63), c(0xFFFFC93C))) {
        it.copy(
            style = VisualizerStyle.CIRCLE_WAVE, colorMode = ColorMode.SUNSET, glow = true,
            background = BackgroundMode.GRADIENT, bgColors = listOf(c(0xFF2B0A1E), c(0xFF5A1A1A), c(0xFF1A0A2B)),
            bgRadial = false, bgAnimated = true, backgroundDim = 0.15f,
            particles = true, particleStyle = ParticleStyle.EMBERS, particleDensity = 0.6f,
            fog = true, fogIntensity = 0.7f, rays = false, shockwaves = true,
        )
    },
    ScenePreset("Album", listOf(c(0xFF8A5CFF), c(0xFF00E5FF), c(0xFFFF4FA3))) {
        it.copy(
            style = VisualizerStyle.CIRCLE_BARS, colorMode = ColorMode.ALBUM, glow = true,
            background = BackgroundMode.ALBUM, backgroundDim = 0.3f, backgroundPulse = true,
            particles = true, particleStyle = ParticleStyle.STARDUST, particleDensity = 0.5f,
            fog = true, fogIntensity = 0.5f, rays = false, shockwaves = true,
        )
    },
    ScenePreset("Galassia", listOf(c(0xFF7C4DFF), c(0xFF00E5A0), c(0xFFFF4081))) {
        it.copy(
            style = VisualizerStyle.CIRCLE_WAVE, colorMode = ColorMode.RAINBOW, glow = true,
            background = BackgroundMode.SOLID, bgColors = listOf(c(0xFF020208), it.bgColors[1], it.bgColors[2]),
            backgroundDim = 0f,
            particles = true, particleStyle = ParticleStyle.STARDUST, particleDensity = 1f,
            fog = true, fogIntensity = 0.9f, rays = true, raysIntensity = 0.4f, shockwaves = true,
        )
    },
    ScenePreset("Oceano", listOf(c(0xFF00B4D8), c(0xFF4361EE), c(0xFF90E0EF))) {
        it.copy(
            style = VisualizerStyle.WAVEFORM, colorMode = ColorMode.OCEAN, glow = true,
            background = BackgroundMode.GRADIENT, bgColors = listOf(c(0xFF001428), c(0xFF003049), c(0xFF00121F)),
            bgRadial = true, bgAnimated = true, backgroundDim = 0.1f,
            particles = true, particleStyle = ParticleStyle.BUBBLES, particleDensity = 0.7f,
            fog = true, fogIntensity = 0.5f, rays = true, raysIntensity = 0.3f, shockwaves = false,
        )
    },
    ScenePreset("Minimal", listOf(c(0xFFFFFFFF), c(0xFF888888), c(0xFF000000))) {
        it.copy(
            style = VisualizerStyle.BARS, colorMode = ColorMode.MONO, glow = false,
            background = BackgroundMode.SOLID, bgColors = listOf(c(0xFF000000), it.bgColors[1], it.bgColors[2]),
            backgroundDim = 0f, particles = false, fog = false, rays = false, shockwaves = false,
            beatFlash = false, bassShake = false,
        )
    },
)
