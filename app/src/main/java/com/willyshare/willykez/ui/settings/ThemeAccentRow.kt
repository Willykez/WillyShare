package com.willyshare.willykez.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.willyshare.willykez.data.ColorSource
import com.willyshare.willykez.data.PaletteStyleOpt
import com.willyshare.willykez.data.normalizeCustomSeed
import com.willyshare.willykez.data.normalizeHex
import com.willyshare.willykez.ui.theme.ColorSourceSpec
import com.willyshare.willykez.ui.theme.ColorSourceSwatchType
import com.willyshare.willykez.ui.theme.colorSourceSpecsInPickerOrder
import com.willyshare.willykez.ui.theme.parseCustomTriplet

private val accentPresetSpecs: List<ColorSourceSpec> = colorSourceSpecsInPickerOrder
private val paletteStyleOrder: List<PaletteStyleOpt> = PaletteStyleOpt.entries.toList()

private fun customHexSwatchSelected(
    colorSource: ColorSource,
    activeCustomSeedHex: String,
    storedHex: String,
): Boolean {
    if (colorSource != ColorSource.CUSTOM) return false
    val activeNorm = normalizeCustomSeed(activeCustomSeedHex)
    val storedNorm = normalizeCustomSeed(storedHex)
    return when {
        activeNorm != null && storedNorm != null -> activeNorm == storedNorm
        else -> activeCustomSeedHex.trim() == storedHex.trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeAccentRow(
    colorSource: ColorSource,
    activeCustomSeedHex: String,
    savedCustomSeedHexes: List<String>,
    customColorPickerOpen: Boolean,
    onSelectPreset: (ColorSource) -> Unit,
    onSelectCustomHex: (String) -> Unit,
    onCustomHexLongPress: (String) -> Unit,
    onAddCustomHexClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val swatchSize =
            when {
                maxWidth < 300.dp -> 42.dp
                maxWidth < 360.dp -> 46.dp
                else -> 52.dp
            }
        val innerSwatchSize = swatchSize - 8.dp
        val swatchGap = if (maxWidth < 340.dp) 8.dp else 12.dp
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(swatchGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(accentPresetSpecs, key = { "preset_${it.source.name}" }) { spec ->
                val isSelected = colorSource == spec.source
                val borderColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    }
                Box(
                    modifier =
                        Modifier
                            .size(swatchSize)
                            .clip(CircleShape)
                            .border(width = if (isSelected) 3.dp else 1.dp, color = borderColor, shape = CircleShape)
                            .clickable(
                                onClick = { onSelectPreset(spec.source) },
                                indication = ripple(bounded = true),
                                interactionSource = remember { MutableInteractionSource() },
                            ).semantics { role = Role.RadioButton },
                    contentAlignment = Alignment.Center,
                ) {
                    ThemeAccentCircleContent(spec = spec, size = innerSwatchSize)
                }
            }
            items(savedCustomSeedHexes, key = { "hex_$it" }) { storedHex ->
                val isSelected = customHexSwatchSelected(colorSource, activeCustomSeedHex, storedHex)
                val borderColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    }
                val triplet = runCatching { parseCustomTriplet(storedHex) }.getOrNull()
                val fillColor =
                    runCatching {
                        val primaryHex = storedHex.split("|").first()
                        Color((normalizeHex(primaryHex) ?: primaryHex).toColorInt())
                    }.getOrDefault(MaterialTheme.colorScheme.surfaceVariant)
                Box(
                    modifier =
                        Modifier
                            .size(swatchSize)
                            .clip(CircleShape)
                            .border(width = if (isSelected) 3.dp else 1.dp, color = borderColor, shape = CircleShape)
                            .combinedClickable(
                                onClick = { onSelectCustomHex(storedHex) },
                                onLongClick = { onCustomHexLongPress(storedHex) },
                                indication = ripple(bounded = true),
                                interactionSource = remember { MutableInteractionSource() },
                            ).semantics { role = Role.RadioButton },
                    contentAlignment = Alignment.Center,
                ) {
                    if (triplet != null) {
                        CuratedTripletSwatch(
                            primary = triplet.primary,
                            secondary = triplet.secondary,
                            tertiary = triplet.tertiary,
                            size = innerSwatchSize,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .size(innerSwatchSize)
                                    .clip(CircleShape)
                                    .background(fillColor),
                        )
                    }
                }
            }
            item(key = "add_custom_seed") {
                val addBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                Box(
                    modifier =
                        Modifier
                            .size(swatchSize)
                            .clip(CircleShape)
                            .border(width = 1.dp, color = addBorder, shape = CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .clickable(
                                onClick = onAddCustomHexClick,
                                indication = ripple(bounded = true),
                                interactionSource = remember { MutableInteractionSource() },
                            ).semantics {
                                role = Role.Button
                                contentDescription = "Add custom color"
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (customColorPickerOpen) Icons.Filled.ChevronRight else Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .size(if (customColorPickerOpen) swatchSize * 0.42f else swatchSize * 0.50f)
                                .graphicsLayer { rotationZ = if (customColorPickerOpen) 90f else 0f },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeAccentCircleContent(
    spec: ColorSourceSpec,
    size: Dp,
) {
    when (spec.swatchType) {
        ColorSourceSwatchType.MATERIAL_YOU ->
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260)),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(size * 0.50f),
                )
            }
        ColorSourceSwatchType.TRIPLET -> {
            val triplet = requireNotNull(spec.triplet)
            CuratedTripletSwatch(
                primary = triplet.primary,
                secondary = triplet.secondary,
                tertiary = triplet.tertiary,
                size = size,
            )
        }
        ColorSourceSwatchType.SOLID ->
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(spec.representativeColor),
            )
    }
}

/**
 * Renders a circle with the primary filling the top half and the secondary/tertiary
 * splitting the bottom half, mirroring the stock Android Material You wallpaper-color picker.
 */
@Composable
private fun CuratedTripletSwatch(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    size: Dp,
) {
    Column(
        modifier = Modifier.size(size).clip(CircleShape),
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(primary))
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(secondary))
            Box(Modifier.weight(1f).fillMaxHeight().background(tertiary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePaletteStyleRow(
    selected: PaletteStyleOpt,
    enabled: Boolean,
    onSelect: (PaletteStyleOpt) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            items(paletteStyleOrder, key = { it.name }) { style ->
                FilterChip(
                    selected = selected == style,
                    onClick = { if (enabled) onSelect(style) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = paletteStyleLabel(style, compact),
                            style =
                                if (compact) {
                                    MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                } else {
                                    MaterialTheme.typography.labelMedium
                                },
                            maxLines = 1,
                        )
                    },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        ),
                )
            }
        }
    }
}

private fun paletteStyleLabel(
    style: PaletteStyleOpt,
    compact: Boolean,
): String =
    when (style) {
        PaletteStyleOpt.TONAL_SPOT -> if (compact) "Tonal" else "Tonal Spot"
        PaletteStyleOpt.NEUTRAL -> "Neutral"
        PaletteStyleOpt.VIBRANT -> "Vibrant"
        PaletteStyleOpt.EXPRESSIVE -> if (compact) "Express" else "Expressive"
        PaletteStyleOpt.RAINBOW -> "Rainbow"
        PaletteStyleOpt.FRUIT_SALAD -> if (compact) "Fruit" else "Fruit Salad"
        PaletteStyleOpt.MONOCHROME -> if (compact) "Mono" else "Monochrome"
        PaletteStyleOpt.FIDELITY -> "Fidelity"
        PaletteStyleOpt.CONTENT -> "Content"
    }

/**
 * User-facing name for a color source - shown in the preview card title and accessibility
 * labels.
 */
fun colorSourceDisplayName(source: ColorSource): String =
    when (source) {
        ColorSource.MATERIAL_YOU -> "Material You"
        ColorSource.DEFAULT -> "Default"
        ColorSource.CURATED_EMBER -> "Ember"
        ColorSource.CURATED_GROVE -> "Grove"
        ColorSource.CURATED_HONEY -> "Honey"
        ColorSource.CURATED_OCEAN -> "Ocean"
        ColorSource.CURATED_IRIS -> "Iris"
        ColorSource.CURATED_DUSK -> "Dusk"
        ColorSource.CURATED_BERRY -> "Berry"
        ColorSource.CUSTOM -> "Custom"
    }
