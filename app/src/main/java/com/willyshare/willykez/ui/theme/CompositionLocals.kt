package com.willyshare.willykez.ui.theme

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.willyshare.willykez.data.ThemeState

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

val LocalUseGradient = compositionLocalOf { false }


val LocalUseEnhancedShading = compositionLocalOf { false }

val LocalReducedMotion = compositionLocalOf { false }

val LocalIsDark = staticCompositionLocalOf { false }

/**
 * The full, already-collected [ThemeState]. Provided once from SparkTheme so downstream
 * screens (Settings, etc.) can read it synchronously on first composition — no flash from
 * default values -> DataStore-backed values.
 */
val LocalThemeState = compositionLocalOf { ThemeState() }
