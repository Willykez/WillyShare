package com.willyshare.willykez.ui.theme

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.willyshare.willykez.data.ThemeMode
import com.willyshare.willykez.data.ThemePrefs
import com.willyshare.willykez.data.ThemeState

/** The [ThemePrefs] instance created once in [MyApplicationTheme], for Settings/Appearance to reuse. */
val LocalThemePrefs = compositionLocalOf<ThemePrefs> { error("No ThemePrefs provided") }

/**
 * Wraps the whole app. Sets up the dynamic Material3 color scheme (Material You, curated
 * palettes, or a custom seed - see [ThemePrefs]/Appearance settings), and derives
 * [LocalSleekPalette] from the resolved scheme so every `Sleek*` color token used throughout
 * the app (SleekBg, SleekOnSurface, SleekCard, etc.) automatically follows the active theme -
 * no need to thread theme state through each individual screen.
 */
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themePrefs = remember(context) { ThemePrefs(context) }
    val themeState by themePrefs.state.collectAsState(initial = ThemeState())

    val systemDark = isSystemInDarkTheme()
    val darkTheme =
        when (themeState.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.BLACK -> true
        }
    val black = themeState.themeMode == ThemeMode.BLACK
    val effectiveUseGradient = themeState.useGradient && !black
    val reducedMotion = rememberSystemReducedMotionEnabled()

    val colorResolution =
        rememberResolvedColorScheme(
            context = context,
            themeState = themeState,
            darkTheme = darkTheme,
            black = black,
        )
    val colorScheme = colorResolution.colorScheme
    val backgroundScheme = colorResolution.backgroundScheme
    val sleekPalette = colorScheme.toSleekPalette()

    val view = LocalView.current
    DisposableEffect(view, darkTheme) {
        if (!view.isInEditMode) {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
        onDispose {}
    }

    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalSleekPalette provides sleekPalette,
        LocalIsDark provides darkTheme,
        LocalUseGradient provides effectiveUseGradient,
        LocalUseEnhancedShading provides themeState.useEnhancedShading,
        LocalThemeState provides themeState,
        LocalReducedMotion provides reducedMotion,
        LocalSnackbarHostState provides snackbarHostState,
        LocalThemePrefs provides themePrefs,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = AppShapes) {
            Box(modifier = Modifier.fillMaxSize()) {
                GradientBackdrop(
                    enabled = effectiveUseGradient,
                    base = backgroundScheme.surface,
                    top = backgroundScheme.primaryContainer,
                    flat = backgroundScheme.background,
                )
                content()
            }
        }
    }
}

/** Derive the legacy [SleekPalette] token set from a resolved dynamic [ColorScheme]. */
private fun ColorScheme.toSleekPalette(): SleekPalette =
    SleekPalette(
        bg = background,
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        surface = surface,
        surfaceContainer = surfaceContainer,
        surfaceVariant = surfaceVariant,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        card = surfaceContainerHigh,
    )

/**
 * Soft vertical wash from primaryContainer into surface, fading out by about
 * 55% of the screen height, so screens read as having depth rather than a
 * flat single-color page.
 */
@Composable
private fun GradientBackdrop(
    enabled: Boolean,
    base: Color,
    top: Color,
    flat: Color,
) {
    if (!enabled) {
        Box(Modifier.fillMaxSize().background(flat))
        return
    }
    val brush =
        remember(base, top) {
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to top.copy(alpha = 0.35f),
                        0.55f to base.copy(alpha = 0f),
                    ),
            )
        }
    Box(
        Modifier
            .fillMaxSize()
            .background(base)
            .background(brush),
    )
}

@Composable
private fun rememberSystemReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readReducedMotion(): Boolean {
        val animationScale =
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        return animationScale == 0f
    }

    var reducedMotion by remember(contentResolver) { mutableStateOf(readReducedMotion()) }
    DisposableEffect(contentResolver) {
        val observer =
            object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reducedMotion = readReducedMotion()
                }
            }
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { contentResolver.unregisterContentObserver(observer) }
    }
    return reducedMotion
}
