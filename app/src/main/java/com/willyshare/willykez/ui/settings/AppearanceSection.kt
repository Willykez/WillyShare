package com.willyshare.willykez.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.willyshare.willykez.data.ColorSource
import com.willyshare.willykez.data.PaletteStyleOpt
import com.willyshare.willykez.data.ThemeMode
import com.willyshare.willykez.data.ThemePrefs
import com.willyshare.willykez.data.ThemeState
import com.willyshare.willykez.data.normalizeCustomSeed
import com.willyshare.willykez.data.normalizeHex
import com.willyshare.willykez.ui.common.HueColorSlider
import com.willyshare.willykez.ui.common.colorHexFromHue
import com.willyshare.willykez.ui.common.hueFromHexColor
import com.willyshare.willykez.ui.components.SettingsToggleSwitch
import com.willyshare.willykez.ui.components.SparkConfirmDialog
import com.willyshare.willykez.ui.GroupPosition
import com.willyshare.willykez.ui.GroupedListColumn
import com.willyshare.willykez.ui.GroupedListItem
import com.willyshare.willykez.ui.theme.colorSourcePaletteChipsEnabled
import com.willyshare.willykez.ui.theme.colorSourceSpecFor
import com.willyshare.willykez.ui.theme.contrastingTextColor
import com.willyshare.willykez.ui.theme.generateTripletForSeed
import com.willyshare.willykez.ui.theme.hexFromColor
import com.willyshare.willykez.ui.theme.parseCustomTriplet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppearanceSection(
    prefs: ThemePrefs,
    state: ThemeState,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var customColorPickerOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val blackThemeEffectsDisabled = state.themeMode == ThemeMode.BLACK
    val blackThemeEffectsDisabledMessage = "Not available in Black theme"

    Column(modifier = Modifier.fillMaxWidth()) {
        GroupedListColumn {
            GroupedListItem(position = GroupPosition.FIRST) {
                AppearanceStudioControls(
                    state = state,
                    onThemeModeChange = { mode -> scope.launch { prefs.setThemeMode(mode) } },
                    onSelectPreset = { source -> scope.launch { prefs.setColorSource(source) } },
                    onSelectCustomHex = { hex -> scope.launch { prefs.setActiveCustomSeed(hex) } },
                    onCustomHexLongPress = { hex -> pendingDelete = hex },
                    customColorPickerOpen = customColorPickerOpen,
                    onCustomColorPickerOpenChange = { customColorPickerOpen = it },
                    onPreviewCustomHex = { hex -> scope.launch { prefs.previewCustomSeed(hex) } },
                    onSaveCustomHex = { hex -> scope.launch { prefs.addCustomSeed(hex) } },
                    onPaletteStyleChange = { style -> scope.launch { prefs.setPaletteStyle(style) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                val enabled = !blackThemeEffectsDisabled
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !enabled) {
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(blackThemeEffectsDisabledMessage)
                                }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Shading intensity",
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                        Text(
                            text = "How strongly cards and surfaces pick up your accent color",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (enabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                        )
                    }
                    ShadingIntensitySlider(
                        intensity = state.shadingIntensity,
                        enabled = enabled,
                        onValueChange = { intensity -> scope.launch { prefs.setShadingIntensity(intensity) } },
                    )
                }
            }
            GroupedListItem(position = GroupPosition.LAST) {
                AppearanceSettingsToggleItem(
                    title = "Gradient background",
                    subtitle = "Soft accent wash behind screens",
                    checked = state.useGradient && !blackThemeEffectsDisabled,
                    enabled = !blackThemeEffectsDisabled,
                    onDisabledClick = {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(blackThemeEffectsDisabledMessage)
                        }
                    },
                    onCheckedChange = { scope.launch { prefs.setUseGradient(it) } },
                )
            }
        }
    }

    pendingDelete?.let { hex ->
        SparkConfirmDialog(
            title = "Remove custom color?",
            text = "This custom color will be removed from your saved colors.",
            confirmLabel = "Remove",
            onConfirm = {
                scope.launch { prefs.removeCustomSeed(hex) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun AppearanceStudioControls(
    state: ThemeState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSelectPreset: (ColorSource) -> Unit,
    onSelectCustomHex: (String) -> Unit,
    onCustomHexLongPress: (String) -> Unit,
    customColorPickerOpen: Boolean,
    onCustomColorPickerOpenChange: (Boolean) -> Unit,
    onPreviewCustomHex: (String) -> Unit,
    onSaveCustomHex: (String) -> Unit,
    onPaletteStyleChange: (PaletteStyleOpt) -> Unit,
) {
    var editingTarget by remember { mutableStateOf(ColorTarget.PRIMARY) }
    LaunchedEffect(customColorPickerOpen) {
        if (!customColorPickerOpen) editingTarget = ColorTarget.PRIMARY
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
        ) {
            ThemeModeSegmentedRow(selected = state.themeMode, onSelect = onThemeModeChange)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Palette style",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                ThemePaletteStyleRow(
                    selected = state.paletteStyle,
                    enabled = colorSourcePaletteChipsEnabled(state.colorSource),
                    onSelect = onPaletteStyleChange,
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                ThemeAccentRow(
                    colorSource = state.colorSource,
                    activeCustomSeedHex = state.activeCustomSeed,
                    savedCustomSeedHexes = state.customSeeds,
                    customColorPickerOpen = customColorPickerOpen,
                    onSelectPreset = { source ->
                        if (customColorPickerOpen) editingTarget = ColorTarget.PRIMARY
                        onSelectPreset(source)
                    },
                    onSelectCustomHex = { hex ->
                        if (customColorPickerOpen) editingTarget = ColorTarget.PRIMARY
                        onSelectCustomHex(hex)
                    },
                    onCustomHexLongPress = onCustomHexLongPress,
                    onAddCustomHexClick = { onCustomColorPickerOpenChange(!customColorPickerOpen) },
                )
                AnimatedVisibility(
                    visible = customColorPickerOpen,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
                    ) {
                        Spacer(Modifier.height(0.dp))
                        CustomColorSlider(
                            initialSeedHex = customSliderInitialSeedHex(state, MaterialTheme.colorScheme.primary),
                            editingTarget = editingTarget,
                            onPreviewColor = onPreviewCustomHex,
                            onSaveColor = onSaveCustomHex,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
            ThemePreviewPanel(
                colorSource = state.colorSource,
                isInteractive = state.colorSource == ColorSource.CUSTOM,
                selectedTarget = editingTarget,
                onTargetSelect = { target ->
                    editingTarget = target
                    onCustomColorPickerOpenChange(true)
                },
            )
        }
    }
}

private val themePickerOrder = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.BLACK)

private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.BLACK -> "Black"
    }

@Composable
private fun ThemeModeSegmentedRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        themePickerOrder.forEach { mode ->
            val isSelected = selected == mode
            if (isSelected) {
                Button(
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.ContentPadding.let { androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp) },
                ) {
                    Text(themeModeLabel(mode), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Clip)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(themeModeLabel(mode), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val subtitleColor =
        if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    if (enabled) onCheckedChange(!checked) else onDisabledClick?.invoke()
                }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(16.dp))
        SettingsToggleSwitch(
            checked = checked,
            enabled = enabled,
            onDisabledInteraction = onDisabledClick,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun CustomColorSlider(
    initialSeedHex: String,
    editingTarget: ColorTarget,
    onPreviewColor: (String) -> Unit,
    onSaveColor: (String) -> Unit,
) {
    var currentSeedHex by remember(initialSeedHex) { mutableStateOf(initialSeedHex) }
    val targetHex = remember(currentSeedHex, editingTarget) { extractTargetHex(currentSeedHex, editingTarget) }

    val normalizedTargetHex = normalizeHex(targetHex) ?: colorHexFromHue(DEFAULT_CUSTOM_HUE)
    var hexEditing by rememberSaveable { mutableStateOf(false) }
    var hexDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(normalizedTargetHex.toHexFieldValue())
    }
    val hexFocusRequester = remember { FocusRequester() }
    var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hexEditorBoundsInRoot by remember { mutableStateOf<Rect?>(null) }

    fun commitHexEditing(): String {
        val draftHex = if (hexDraft.text.length == 6) normalizeHex("#${hexDraft.text}") else null
        val committedTargetHex = draftHex ?: normalizedTargetHex
        hexDraft = committedTargetHex.toHexFieldValue()

        val nextSeedHex = updateTargetHex(currentSeedHex, committedTargetHex, editingTarget)
        currentSeedHex = nextSeedHex

        if (hexEditing && draftHex != null) onPreviewColor(nextSeedHex)
        hexEditing = false
        return nextSeedHex
    }
    LaunchedEffect(normalizedTargetHex) { hexDraft = normalizedTargetHex.toHexFieldValue() }
    LaunchedEffect(hexEditing) { if (hexEditing) hexFocusRequester.requestFocus() }
    LaunchedEffect(hexDraft, hexEditing) {
        if (!hexEditing || hexDraft.text.length != 6) return@LaunchedEffect
        delay(HEX_INPUT_DEBOUNCE_MILLIS)
        val normalized = "#${hexDraft.text.uppercase(Locale.US)}"
        if (hueFromHexColor(normalized) != null) {
            val nextSeedHex = updateTargetHex(currentSeedHex, normalized, editingTarget)
            currentSeedHex = nextSeedHex
            onPreviewColor(nextSeedHex)
        }
    }
    val panelShape = MaterialTheme.shapes.extraLarge
    val sliderPanelColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        color = sliderPanelColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = panelShape,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { panelCoordinates = it }
                    .pointerInput(hexEditing, hexDraft, currentSeedHex, editingTarget) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val wasEditingAtDown = hexEditing
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                            if (!wasEditingAtDown || !hexEditing) return@awaitEachGesture
                            val tapInRoot = panelCoordinates?.localToRoot(up.position) ?: return@awaitEachGesture
                            val editorBounds = hexEditorBoundsInRoot
                            if (editorBounds == null || !editorBounds.contains(tapInRoot)) commitHexEditing()
                        }
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val rawString =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> "Select primary color"
                        ColorTarget.SECONDARY -> "Select secondary color"
                        ColorTarget.TERTIARY -> "Select tertiary color"
                    }
                val targetWord =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> "primary"
                        ColorTarget.SECONDARY -> "secondary"
                        ColorTarget.TERTIARY -> "tertiary"
                    }
                val parsedTriplet = remember(currentSeedHex) { parseCustomTriplet(currentSeedHex) }
                val primaryColor = parsedTriplet?.primary ?: MaterialTheme.colorScheme.primary
                val secondaryColor = parsedTriplet?.secondary ?: MaterialTheme.colorScheme.secondary
                val tertiaryColor = parsedTriplet?.tertiary ?: MaterialTheme.colorScheme.tertiary
                val targetColor =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> primaryColor
                        ColorTarget.SECONDARY -> secondaryColor
                        ColorTarget.TERTIARY -> tertiaryColor
                    }
                val annotatedTitle =
                    remember(rawString, targetWord, targetColor) {
                        val index = rawString.indexOf(targetWord, ignoreCase = true)
                        buildAnnotatedString {
                            if (index != -1) {
                                append(rawString.substring(0, index))
                                withStyle(SpanStyle(color = targetColor)) {
                                    append(rawString.substring(index, index + targetWord.length))
                                }
                                append(rawString.substring(index + targetWord.length))
                            } else {
                                append(rawString)
                            }
                        }
                    }
                Text(
                    text = annotatedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                EditableHexValue(
                    hex = normalizedTargetHex,
                    editing = hexEditing,
                    draft = hexDraft,
                    focusRequester = hexFocusRequester,
                    onStartEditing = {
                        hexDraft = normalizedTargetHex.toHexFieldValue()
                        hexEditing = true
                    },
                    onDraftChange = { hexDraft = it },
                    onStopEditing = { commitHexEditing() },
                    onBoundsChange = { hexEditorBoundsInRoot = it },
                )
                Surface(
                    onClick = { onSaveColor(commitHexEditing()) },
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Save color",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            HueColorSlider(
                selectedHex = normalizedTargetHex,
                onSelect = { newHex ->
                    val nextSeedHex = updateTargetHex(currentSeedHex, newHex, editingTarget)
                    currentSeedHex = nextSeedHex
                    hexDraft = newHex.toHexFieldValue()
                },
                modifier = Modifier.fillMaxWidth(),
                fallbackHue = DEFAULT_CUSTOM_HUE,
                sliderPanelColor = sliderPanelColor,
                onValueChangeFinished = { newHex ->
                    val nextSeedHex = updateTargetHex(currentSeedHex, newHex, editingTarget)
                    currentSeedHex = nextSeedHex
                    onPreviewColor(nextSeedHex)
                },
            )
        }
    }
}

@Composable
private fun EditableHexValue(
    hex: String,
    editing: Boolean,
    draft: TextFieldValue,
    focusRequester: FocusRequester,
    onStartEditing: () -> Unit,
    onDraftChange: (TextFieldValue) -> Unit,
    onStopEditing: () -> Unit,
    onBoundsChange: (Rect?) -> Unit,
) {
    val shape = CircleShape
    val haptic = LocalHapticFeedback.current
    val textStyle =
        MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
    var hadFocus by remember(editing) { mutableStateOf(false) }
    LaunchedEffect(editing) { if (!editing) onBoundsChange(null) }
    if (!editing) {
        Box(
            modifier =
                Modifier
                    .width(HexValueWidth)
                    .height(HexValueHeight)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = shape)
                    .clickable(onClick = onStartEditing)
                    .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = hex, style = textStyle, maxLines = 1, overflow = TextOverflow.Clip)
        }
        return
    }

    Box(
        modifier =
            Modifier
                .width(HexValueWidth)
                .height(HexValueHeight)
                .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = shape)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = draft.toPrefixedHexFieldValue(),
            onValueChange = { value ->
                val acceptedValue = value.acceptPrefixedHexInput()
                if (acceptedValue != null) {
                    onDraftChange(acceptedValue)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) hadFocus = true else if (hadFocus) onStopEditing()
                    },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onStopEditing() }),
        )
    }
}

private const val DEFAULT_CUSTOM_HUE = 270f
private const val HEX_INPUT_DEBOUNCE_MILLIS = 450L
private val HexValueWidth = 84.dp
private val HexValueHeight = 40.dp

private fun String.dropHexPrefix(): String = removePrefix("#").take(6).uppercase(Locale.US)

private fun String.toHexFieldValue(): TextFieldValue {
    val text = dropHexPrefix()
    return TextFieldValue(text = text, selection = TextRange(text.length))
}

private fun TextFieldValue.toPrefixedHexFieldValue(): TextFieldValue {
    val prefixedSelection =
        TextRange(
            start = (selection.start + 1).coerceIn(1, text.length + 1),
            end = (selection.end + 1).coerceIn(1, text.length + 1),
        )
    return copy(text = "#$text", selection = prefixedSelection)
}

private fun TextFieldValue.acceptPrefixedHexInput(): TextFieldValue? {
    val hasPrefix = text.startsWith("#")
    val rawHexText = text.removePrefix("#")
    if (rawHexText.length > 6) return null
    val hexText = rawHexText.uppercase(Locale.US)
    if (hexText.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    val prefixOffset = if (hasPrefix) 1 else 0
    return TextFieldValue(
        text = hexText,
        selection =
            TextRange(
                start = (selection.start - prefixOffset).coerceIn(0, hexText.length),
                end = (selection.end - prefixOffset).coerceIn(0, hexText.length),
            ),
    )
}

private fun customSliderInitialSeedHex(
    state: ThemeState,
    currentPrimary: Color,
): String {
    val activeCustomSeed = normalizeCustomSeed(state.activeCustomSeed)
    if (state.colorSource == ColorSource.CUSTOM && activeCustomSeed != null) return activeCustomSeed
    if (state.colorSource == ColorSource.MATERIAL_YOU) return hexFromColor(currentPrimary)
    return hexFromColor(colorSourceSpecFor(state.colorSource).representativeColor)
}

@Composable
private fun ShadingIntensitySlider(
    intensity: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember { mutableFloatStateOf(intensity.roundToShadingStep()) }

    LaunchedEffect(intensity) {
        sliderValue = intensity.roundToShadingStep()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { rawValue ->
                if (enabled) {
                    val steppedValue = rawValue.roundToShadingStep()
                    if (steppedValue != sliderValue) {
                        sliderValue = steppedValue
                        onValueChange(steppedValue)
                    }
                }
            },
            valueRange = 0f..2f,
            steps = 19,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = getShadingLabel(sliderValue),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun getShadingLabel(value: Float): String {
    val percentage = (value * 100f).roundToInt()
    return "$percentage%"
}

private fun Float.roundToShadingStep(): Float = (this * 10f).roundToInt().coerceIn(0, 20) / 10f

enum class ColorTarget { PRIMARY, SECONDARY, TERTIARY }

fun extractTargetHex(
    seedHex: String,
    target: ColorTarget,
): String {
    val parts = seedHex.split("|")
    return when (target) {
        ColorTarget.PRIMARY -> parts.getOrNull(0) ?: seedHex
        ColorTarget.SECONDARY -> parts.getOrNull(1) ?: seedHex
        ColorTarget.TERTIARY -> parts.getOrNull(2) ?: seedHex
    }
}

fun updateTargetHex(
    seedHex: String,
    newHex: String,
    target: ColorTarget,
): String {
    val parts = seedHex.split("|").toMutableList()
    while (parts.size < 3) {
        val primaryHex = parts.getOrNull(0) ?: "#0EA5E9"
        val primaryColor = Color(primaryHex.toColorInt())
        val generatedColors = generateTripletForSeed(primaryColor)
        val defaultColors =
            listOf(primaryHex, hexFromColor(generatedColors.secondary), hexFromColor(generatedColors.tertiary))
        parts.add(defaultColors[parts.size])
    }
    parts[target.ordinal] = newHex

    if (target == ColorTarget.PRIMARY) {
        val newPrimaryColor = Color(newHex.toColorInt())
        val generated = generateTripletForSeed(newPrimaryColor)
        parts[1] = hexFromColor(generated.secondary)
        parts[2] = hexFromColor(generated.tertiary)
    }

    return parts.joinToString("|")
}

@Composable
private fun ThemePreviewPanel(
    colorSource: ColorSource,
    isInteractive: Boolean,
    selectedTarget: ColorTarget,
    onTargetSelect: (ColorTarget) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val title =
        if (colorSource == ColorSource.CUSTOM) "Customize" else "${colorSourceDisplayName(colorSource)} preview"

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
        AccentContainersStrip(
            scheme = scheme,
            isInteractive = isInteractive,
            selectedTarget = selectedTarget,
            onTargetSelect = onTargetSelect,
        )
        SurfaceLadderStrip(scheme = scheme)
    }
}

@Composable
private fun SurfaceLadderStrip(scheme: ColorScheme) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        val swatches =
            listOf(
                scheme.surfaceContainerLowest to if (compact) "Lo-" else "Lowest",
                scheme.surface to "Surface",
                scheme.surfaceContainerLow to if (compact) "Low" else "Low",
                scheme.surfaceContainer to if (compact) "Base" else "Base",
                scheme.surfaceContainerHigh to if (compact) "High" else "High",
                scheme.surfaceContainerHighest to if (compact) "Hi+" else "Highest",
            )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 32.dp else 36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        ) {
            swatches.forEach { (color, label) ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeightCompat().background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp) else MaterialTheme.typography.labelSmall,
                        color = contrastingTextColor(color),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun Modifier.fillMaxHeightCompat() = this.then(Modifier.fillMaxHeight())

@Composable
private fun AccentContainersStrip(
    scheme: ColorScheme,
    isInteractive: Boolean = false,
    selectedTarget: ColorTarget = ColorTarget.PRIMARY,
    onTargetSelect: (ColorTarget) -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer,
            label = "Primary",
            isSelected = isInteractive && selectedTarget == ColorTarget.PRIMARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.PRIMARY) },
        )
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer,
            label = "Secondary",
            isSelected = isInteractive && selectedTarget == ColorTarget.SECONDARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.SECONDARY) },
        )
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.tertiaryContainer,
            onContainer = scheme.onTertiaryContainer,
            label = "Tertiary",
            isSelected = isInteractive && selectedTarget == ColorTarget.TERTIARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.TERTIARY) },
        )
    }
}

@Composable
private fun AccentChip(
    modifier: Modifier,
    container: Color,
    onContainer: Color,
    label: String,
    isSelected: Boolean = false,
    isInteractive: Boolean = false,
    onClick: () -> Unit = {},
) {
    val outlineColor = MaterialTheme.colorScheme.primary
    val chipModifier = if (isInteractive) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier =
            chipModifier.then(
                if (isSelected) Modifier.border(1.dp, outlineColor, MaterialTheme.shapes.medium) else Modifier,
            ),
        shape = MaterialTheme.shapes.medium,
        color = container,
        tonalElevation = if (isSelected) 4.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Aa",
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
            )
        }
    }
}
