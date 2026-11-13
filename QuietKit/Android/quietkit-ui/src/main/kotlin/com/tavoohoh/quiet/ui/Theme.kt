package com.tavoohoh.quiet.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tavoohoh.quiet.core.Contrast
import com.tavoohoh.quiet.core.Palette

/** Colors for a palette, by role, in light or dark. Port of QuietUI's `Theme`. */
@Immutable
data class QuietColors(
    val palette: Palette,
    val isDark: Boolean,
    /** Screen background. */
    val background: Color,
    /** Cards, list rows, round buttons. */
    val surface: Color,
    /** Secondary buttons, segment tracks, switches when off. */
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    /** Main buttons, selection, bar icons. */
    val primary: Color,
    val onPrimary: Color,
    /** Streaks, praise, destructive rows. */
    val accent: Color,
    /** Disabled and empty things, still faintly visible. */
    val ghost: Color,
    val separator: Color,
    val suitRed: Color,
) {
    val isHighContrast: Boolean get() = palette.isHighContrast

    /** Text on `accent` (the background reads well on every palette's accent). */
    val onAccent: Color get() = background

    companion object {
        fun of(palette: Palette, dark: Boolean): QuietColors {
            fun color(role: Palette.Role): Color {
                val hex = palette.hex(role)
                val (r, g, b) = Contrast.components(if (dark) hex.dark else hex.light)
                return Color(r.toFloat(), g.toFloat(), b.toFloat())
            }
            return QuietColors(
                palette = palette, isDark = dark,
                background = color(Palette.Role.BACKGROUND), surface = color(Palette.Role.SURFACE),
                surface2 = color(Palette.Role.SURFACE2), ink = color(Palette.Role.INK), muted = color(Palette.Role.MUTED),
                primary = color(Palette.Role.PRIMARY), onPrimary = color(Palette.Role.ON_PRIMARY),
                accent = color(Palette.Role.ACCENT), ghost = color(Palette.Role.GHOST),
                separator = color(Palette.Role.SEPARATOR), suitRed = color(Palette.Role.SUIT_RED),
            )
        }
    }
}

val LocalQuietColors = staticCompositionLocalOf { QuietColors.of(Palette.TEAL_CORAL, dark = false) }

/** True when the player turned animations off (Settings › Accessibility › Remove animations, or animator scale 0). */
val LocalReduceMotion = staticCompositionLocalOf { false }

object QuietTheme {
    val colors: QuietColors @Composable get() = LocalQuietColors.current
    val reduceMotion: Boolean @Composable get() = LocalReduceMotion.current
}

/**
 * The family's theme: the palette's roles in light or dark (following the system unless
 * `dark` pins one), Reduce Motion and haptics. Material 3 gets the same colors so its
 * sheets and text fields match.
 */
@Composable
fun QuietTheme(
    palette: Palette = Palette.TEAL_CORAL,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = remember(palette, dark) { QuietColors.of(palette, dark) }
    val reduceMotion = rememberReduceMotion()
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary, background = colors.background,
            onBackground = colors.ink, surface = colors.surface, onSurface = colors.ink,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            onSurfaceVariant = colors.muted, outline = colors.separator, secondary = colors.accent, error = colors.accent,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary, background = colors.background,
            onBackground = colors.ink, surface = colors.surface, onSurface = colors.ink,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            onSurfaceVariant = colors.muted, outline = colors.separator, secondary = colors.accent, error = colors.accent,
        )
    }
    CompositionLocalProvider(LocalQuietColors provides colors, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = QuietType.material, content = content)
    }
}

/** Reads the animator duration scale (0 = animations removed) and follows it while the app runs. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduce by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduce = read() }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduce
}
