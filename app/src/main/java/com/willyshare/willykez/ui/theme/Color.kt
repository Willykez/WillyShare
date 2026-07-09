package com.willyshare.willykez.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SleekPalette(
    val bg: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val card: Color
)

val LightSleekPalette = SleekPalette(
    bg = Color(0xFFFEF7FF),
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DEF8),
    onPrimaryContainer = Color(0xFF1D192B),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF21005D),
    surface = Color(0xFFFEF7FF),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFCAC4D0),
    card = Color(0xFFFFFFFF)
)

val DarkSleekPalette = SleekPalette(
    bg = Color(0xFF141218),
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    surface = Color(0xFF141218),
    surfaceContainer = Color(0xFF211F26),
    surfaceVariant = Color(0xFF49454F),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    card = Color(0xFF2B2930)
)

val LocalSleekPalette = staticCompositionLocalOf { LightSleekPalette }

val SleekBg: Color @Composable get() = LocalSleekPalette.current.bg
val SleekPrimary: Color @Composable get() = LocalSleekPalette.current.primary
val SleekOnPrimary: Color @Composable get() = LocalSleekPalette.current.onPrimary
val SleekPrimaryContainer: Color @Composable get() = LocalSleekPalette.current.primaryContainer
val SleekOnPrimaryContainer: Color @Composable get() = LocalSleekPalette.current.onPrimaryContainer
val SleekSecondary: Color @Composable get() = LocalSleekPalette.current.secondary
val SleekOnSecondary: Color @Composable get() = LocalSleekPalette.current.onSecondary
val SleekSecondaryContainer: Color @Composable get() = LocalSleekPalette.current.secondaryContainer
val SleekOnSecondaryContainer: Color @Composable get() = LocalSleekPalette.current.onSecondaryContainer
val SleekSurface: Color @Composable get() = LocalSleekPalette.current.surface
val SleekSurfaceContainer: Color @Composable get() = LocalSleekPalette.current.surfaceContainer
val SleekSurfaceVariant: Color @Composable get() = LocalSleekPalette.current.surfaceVariant
val SleekOnSurface: Color @Composable get() = LocalSleekPalette.current.onSurface
val SleekOnSurfaceVariant: Color @Composable get() = LocalSleekPalette.current.onSurfaceVariant
val SleekOutline: Color @Composable get() = LocalSleekPalette.current.outline

val SleekCard: Color @Composable get() = LocalSleekPalette.current.card

val CyanBright = Color(0xFF00DBE7)
val CyanGlow = Color(0xFF74F5FF)
val VioletAccent = Color(0xFF7000FF)
val PinkThumb = Color(0xFFFFD8E4)
val BlueThumb = Color(0xFFD3E3FD)
val GreenThumb = Color(0xFFC4EED0)
