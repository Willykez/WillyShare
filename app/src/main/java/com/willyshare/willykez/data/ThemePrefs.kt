package com.willyshare.willykez.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.themePrefsDataStore by preferencesDataStore(name = "spark_theme_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

enum class ColorSource {
    DEFAULT,
    MATERIAL_YOU,
    CUSTOM,

    /**
     * Curated three-color palette sets. Each entry is a hand-tuned primary/secondary/tertiary
     * triplet that renders as visually distinct accents across all palette styles.
     */
    CURATED_EMBER,
    CURATED_GROVE,
    CURATED_HONEY,
    CURATED_OCEAN,
    CURATED_IRIS,
    CURATED_DUSK,
    CURATED_BERRY,
}

enum class PaletteStyleOpt {
    TONAL_SPOT,
    NEUTRAL,
    VIBRANT,
    EXPRESSIVE,
    RAINBOW,
    FRUIT_SALAD,
    MONOCHROME,
    FIDELITY,
    CONTENT,
}

/** Surface-shading intensity used when nothing is stored yet. 1.0 == the slider's "medium" notch. */
const val DEFAULT_SHADING_INTENSITY = 1.0f

data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.MATERIAL_YOU,
    val paletteStyle: PaletteStyleOpt = PaletteStyleOpt.TONAL_SPOT,
    val customSeeds: List<String> = emptyList(),
    val activeCustomSeed: String = "",
    val useGradient: Boolean = true,
    val shadingIntensity: Float = DEFAULT_SHADING_INTENSITY,
) {
    val useEnhancedShading: Boolean
        get() = shadingIntensity > 0.0f
}

class ThemePrefs(
    private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_SOURCE = stringPreferencesKey("color_source")
        val PALETTE_STYLE = stringPreferencesKey("palette_style")
        val CUSTOM_SEEDS = stringPreferencesKey("custom_seeds")
        val ACTIVE_CUSTOM_SEED = stringPreferencesKey("active_custom_seed")
        val USE_GRADIENT = booleanPreferencesKey("use_gradient")
        val SHADING_INTENSITY_FACTOR = floatPreferencesKey("shading_intensity_factor")
    }

    val state: Flow<ThemeState> =
        context.themePrefsDataStore.data.map { p ->
            ThemeState(
                themeMode =
                    runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "") }
                        .getOrDefault(ThemeMode.SYSTEM),
                colorSource =
                    runCatching { ColorSource.valueOf(p[Keys.COLOR_SOURCE] ?: "") }
                        .getOrDefault(ColorSource.MATERIAL_YOU),
                paletteStyle =
                    runCatching { PaletteStyleOpt.valueOf(p[Keys.PALETTE_STYLE] ?: "") }
                        .getOrDefault(PaletteStyleOpt.TONAL_SPOT),
                customSeeds = decodeSeeds(p[Keys.CUSTOM_SEEDS].orEmpty()),
                activeCustomSeed = normalizeCustomSeed(p[Keys.ACTIVE_CUSTOM_SEED].orEmpty()).orEmpty(),
                useGradient = p[Keys.USE_GRADIENT] ?: true,
                shadingIntensity = p[Keys.SHADING_INTENSITY_FACTOR] ?: DEFAULT_SHADING_INTENSITY,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themePrefsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setColorSource(source: ColorSource) {
        context.themePrefsDataStore.edit { it[Keys.COLOR_SOURCE] = source.name }
    }

    /** Preview a custom hex/triplet without persisting it as a saved seed yet. */
    suspend fun previewCustomSeed(hex: String) {
        val normalized = normalizeCustomSeed(hex) ?: return
        context.themePrefsDataStore.edit { p ->
            p[Keys.ACTIVE_CUSTOM_SEED] = normalized
            p[Keys.COLOR_SOURCE] = ColorSource.CUSTOM.name
        }
    }

    suspend fun setActiveCustomSeed(hex: String) {
        val normalized = normalizeCustomSeed(hex) ?: return
        context.themePrefsDataStore.edit { p ->
            p[Keys.ACTIVE_CUSTOM_SEED] = normalized
            p[Keys.COLOR_SOURCE] = ColorSource.CUSTOM.name
        }
    }

    suspend fun addCustomSeed(hex: String) {
        val normalized = normalizeCustomSeed(hex) ?: return
        context.themePrefsDataStore.edit { p ->
            val current = decodeSeeds(p[Keys.CUSTOM_SEEDS].orEmpty())
            if (!current.contains(normalized)) {
                p[Keys.CUSTOM_SEEDS] = encodeSeeds(current + normalized)
                p[Keys.ACTIVE_CUSTOM_SEED] = normalized
            }
            p[Keys.COLOR_SOURCE] = ColorSource.CUSTOM.name
        }
    }

    suspend fun removeCustomSeed(hex: String) {
        val normalized = normalizeCustomSeed(hex) ?: return
        context.themePrefsDataStore.edit { p ->
            val current = decodeSeeds(p[Keys.CUSTOM_SEEDS].orEmpty())
            val next = current.filterNot { it.equals(normalized, ignoreCase = true) }
            p[Keys.CUSTOM_SEEDS] = encodeSeeds(next)
            if ((p[Keys.ACTIVE_CUSTOM_SEED] ?: "").equals(normalized, ignoreCase = true)) {
                p[Keys.ACTIVE_CUSTOM_SEED] = ""
                p[Keys.COLOR_SOURCE] = ColorSource.DEFAULT.name
            }
        }
    }

    suspend fun setPaletteStyle(style: PaletteStyleOpt) {
        context.themePrefsDataStore.edit { it[Keys.PALETTE_STYLE] = style.name }
    }

    suspend fun setUseGradient(value: Boolean) {
        context.themePrefsDataStore.edit { it[Keys.USE_GRADIENT] = value }
    }

    suspend fun setShadingIntensity(intensity: Float) {
        context.themePrefsDataStore.edit { it[Keys.SHADING_INTENSITY_FACTOR] = intensity }
    }

    suspend fun reset() {
        context.themePrefsDataStore.edit { it.clear() }
    }

    private fun decodeSeeds(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            val jsonArray = JSONArray(value)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val normalized = normalizeCustomSeed(jsonArray.getString(index)) ?: continue
                    if (!contains(normalized)) add(normalized)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSeeds(seeds: List<String>): String {
        val jsonArray = JSONArray()
        seeds.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }
}

/**
 * Normalize a user-entered hex color string to canonical uppercase `#RRGGBB` form.
 * Returns null if the input is not a valid 3- or 6-digit hex color.
 */
fun normalizeHex(raw: String): String? {
    val stripped = raw.trim().removePrefix("#")
    val hex =
        when (stripped.length) {
            3 -> stripped.map { "$it$it" }.joinToString("")
            6 -> stripped
            else -> return null
        }
    if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + hex.uppercase()
}

/**
 * Normalize a custom seed, which can be a single hex or a pipe-separated triplet of hexes.
 */
fun normalizeCustomSeed(raw: String): String? {
    if (raw.contains("|")) {
        val parts = raw.split("|")
        val normalizedParts =
            parts.map { part ->
                normalizeHex(part) ?: return null
            }
        return normalizedParts.joinToString("|")
    }
    return normalizeHex(raw)
}
