package com.aurora.player.settings

import android.content.Context
import com.aurora.player.logo.LogoFactory
import com.aurora.player.util.ImageFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class UserPreset(val id: String, val name: String, val preview: List<Int>)

/**
 * I preset salvati dall'utente. Ognuno conserva l'aspetto completo
 * (centro, livelli, colori, sfondo, logo, effetti) più una copia delle immagini di sfondo e logo.
 * Le impostazioni di energia NON fanno parte del preset: restano quelle scelte nella scheda Energia.
 */
class PresetStore(private val context: Context, private val settings: SettingsStore) {
    private val index = context.getSharedPreferences("aurora_presets", Context.MODE_PRIVATE)
    private val _presets = MutableStateFlow(loadIndex())
    val presets: StateFlow<List<UserPreset>> = _presets.asStateFlow()

    private fun prefsName(id: String) = "aurora_preset_$id"
    private fun bgFile(id: String) = File(context.filesDir, "preset_${id}_bg.img")
    private fun logoFile(id: String) = File(context.filesDir, "preset_${id}_logo.img")

    private fun loadIndex(): List<UserPreset> = runCatching {
        val arr = JSONArray(index.getString("index", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val c = o.getJSONArray("c")
            UserPreset(o.getString("id"), o.getString("name"), (0 until c.length()).map { c.getInt(it) })
        }
    }.getOrDefault(emptyList())

    private fun saveIndex(list: List<UserPreset>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("c", JSONArray(p.preview)))
        }
        index.edit().putString("index", arr.toString()).apply()
        _presets.value = list
    }

    fun nextDefaultName(): String = "Preset ${_presets.value.size + 1}"

    /** Salva l'aspetto attuale. Con [overwriteId] sostituisce un preset esistente mantenendone il nome. */
    suspend fun saveCurrent(name: String, overwriteId: String? = null) {
        val s = settings.settings.value
        val id = overwriteId ?: System.currentTimeMillis().toString(36)
        withContext(Dispatchers.IO) {
            context.getSharedPreferences(prefsName(id), Context.MODE_PRIVATE).writeSettings(s)
            copy(File(context.filesDir, ImageFiles.BG_FILE), bgFile(id))
            copy(File(context.filesDir, LogoFactory.LOGO_FILE), logoFile(id))
        }
        val list = _presets.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val finalName = name.trim().ifEmpty { nextDefaultName() }
        if (i >= 0) list[i] = UserPreset(id, list[i].name, previewOf(s))
        else list.add(UserPreset(id, finalName, previewOf(s)))
        saveIndex(list)
    }

    suspend fun apply(id: String) {
        val loaded = withContext(Dispatchers.IO) {
            context.getSharedPreferences(prefsName(id), Context.MODE_PRIVATE).readSettings()
        }
        val bgRestored = withContext(Dispatchers.IO) { copy(bgFile(id), File(context.filesDir, ImageFiles.BG_FILE)) }
        val logoRestored = withContext(Dispatchers.IO) { copy(logoFile(id), File(context.filesDir, LogoFactory.LOGO_FILE)) }
        settings.update { cur ->
            loaded.copy(
                customBgVersion = if (bgRestored) cur.customBgVersion + 1 else cur.customBgVersion,
                logoVersion = if (logoRestored) cur.logoVersion + 1 else cur.logoVersion,
                autoSurprise = cur.autoSurprise,
                swipePresets = cur.swipePresets,
                fpsLimit = cur.fpsLimit,
                idleSlowdown = cur.idleSlowdown,
                sleepWhenPaused = cur.sleepWhenPaused,
                sleepMinutes = cur.sleepMinutes,
                lightGlass = cur.lightGlass,
                ecoMode = cur.ecoMode,
                autoEcoBattery = cur.autoEcoBattery,
                dimScreen = cur.dimScreen,
                dimLevel = cur.dimLevel,
                emergency = cur.emergency,
            )
        }
    }

    fun rename(id: String, name: String) {
        val n = name.trim().ifEmpty { return }
        saveIndex(_presets.value.map { if (it.id == id) it.copy(name = n) else it })
    }

    fun move(id: String, delta: Int) {
        val list = _presets.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        saveIndex(list)
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            context.deleteSharedPreferences(prefsName(id))
            bgFile(id).delete()
            logoFile(id).delete()
        }
        saveIndex(_presets.value.filterNot { it.id == id })
    }

    private fun copy(from: File, to: File): Boolean = try {
        if (from.exists()) {
            from.copyTo(to, overwrite = true)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }

    private fun previewOf(s: VisualSettings): List<Int> {
        val pal = when (s.colorMode) {
            ColorMode.CUSTOM -> s.customColors
            ColorMode.NEON -> listOf(0xFF00E5FF, 0xFFFF00E5, 0xFF7C4DFF).map { it.toInt() }
            ColorMode.SUNSET -> listOf(0xFFFF6B35, 0xFFFF2E63, 0xFFFFC93C).map { it.toInt() }
            ColorMode.OCEAN -> listOf(0xFF00B4D8, 0xFF4361EE, 0xFF90E0EF).map { it.toInt() }
            ColorMode.MONO -> listOf(0xFFFFFFFF, 0xFFD0D0E0, 0xFF888888).map { it.toInt() }
            ColorMode.RAINBOW -> listOf(0xFFFF1744, 0xFF00E676, 0xFF3D5AFE).map { it.toInt() }
            ColorMode.ALBUM -> listOf(0xFF8A5CFF, 0xFF00E5FF, 0xFFFF4FA3).map { it.toInt() }
        }
        val bg = when (s.background) {
            BackgroundMode.GRADIENT, BackgroundMode.SOLID -> s.bgColors[0]
            else -> 0xFF101018.toInt()
        }
        return listOf(pal[0], pal[1], bg)
    }
}
