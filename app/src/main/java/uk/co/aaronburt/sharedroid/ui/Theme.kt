package uk.co.aaronburt.sharedroid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import uk.co.aaronburt.sharedroid.data.AppSettings

/** The app's own colour roles, named as in the design mockup. */
@Immutable
data class SdColors(
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val chip: Color,
    val icon: Color,
    val nav: Color,
    val ok: Color,
    val warn: Color,
    val dark: Boolean,
)

class AccentPreset(val name: String, val label: String, val light: Color, val dark: Color, val softLight: Color, val softDark: Color)

val AccentPresets = listOf(
    AccentPreset("teal", "Teal", Color(0xFF0D7F74), Color(0xFF4FD1C0), Color(0xFFDCEFEC), Color(0xFF16332F)),
    AccentPreset("cobalt", "Cobalt", Color(0xFF2C4FC7), Color(0xFF8AA4FF), Color(0xFFE3E9FB), Color(0xFF1C2443)),
    AccentPreset("amber", "Amber", Color(0xFFA5620A), Color(0xFFF0B04F), Color(0xFFF7ECDA), Color(0xFF352915)),
    AccentPreset("graphite", "Graphite", Color(0xFF262A35), Color(0xFFE4E6EC), Color(0xFFE7E8ED), Color(0xFF2A2C35)),
)

private val DarkSurface = Color(0xFF1C1D24)
private val DarkInk = Color(0xFF121318)

private val LocalSdColors = staticCompositionLocalOf<SdColors> { error("ShareDroidTheme is missing") }

object Sd {
    val colors: SdColors
        @Composable @ReadOnlyComposable get() = LocalSdColors.current
}

@Composable
fun ShareDroidTheme(settings: AppSettings = AppSettings(), dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val (accent, soft) = when {
        settings.matchWallpaper -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context).let { it.primary to it.secondaryContainer }
            else dynamicLightColorScheme(context).let { it.primary to it.primaryContainer }
        }
        settings.accent == AppSettings.ACCENT_CUSTOM -> {
            val custom = Color(settings.customColor)
            if (dark) lerp(Color.White, custom, 0.55f) to lerp(DarkSurface, custom, 0.25f)
            else custom to lerp(Color.White, custom, 0.14f)
        }
        else -> {
            val preset = AccentPresets.firstOrNull { it.name == settings.accent } ?: AccentPresets.first()
            if (dark) preset.dark to preset.softDark else preset.light to preset.softLight
        }
    }
    val onAccent = if (accent.luminance() > 0.4f) DarkInk else Color.White

    val colors = when {
        !dark -> SdColors(
            accent, soft, onAccent,
            bg = Color(0xFFF6F7FA), surface = Color.White, ink = Color(0xFF161821), muted = Color(0xFF6B7083),
            line = Color(0xFFE6E8EE), chip = Color(0xFFF0F1F5), icon = Color(0xFF2A2D3A), nav = Color.White,
            ok = Color(0xFF12895A), warn = Color(0xFFB4610A), dark = false,
        )
        settings.trueBlack -> SdColors(
            accent, soft, onAccent,
            bg = Color.Black, surface = Color(0xFF101116), ink = Color(0xFFF1F2F6), muted = Color(0xFF9A9FB1),
            line = Color(0xFF23252E), chip = Color(0xFF1B1D25), icon = Color(0xFFD9DBE4), nav = Color.Black,
            ok = Color(0xFF4FD39A), warn = Color(0xFFF0A254), dark = true,
        )
        else -> SdColors(
            accent, soft, onAccent,
            bg = DarkInk, surface = DarkSurface, ink = Color(0xFFF1F2F6), muted = Color(0xFF9A9FB1),
            line = Color(0xFF2A2C35), chip = Color(0xFF262832), icon = Color(0xFFD9DBE4), nav = DarkSurface,
            ok = Color(0xFF4FD39A), warn = Color(0xFFF0A254), dark = true,
        )
    }

    // Stock Material components (sheets, dialogs, sliders) read these, so they match the custom roles.
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentSoft,
        onPrimaryContainer = colors.accent,
        secondaryContainer = colors.accentSoft,
        onSecondaryContainer = colors.accent,
        background = colors.bg,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.chip,
        onSurfaceVariant = colors.muted,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.chip,
        outline = colors.line,
        outlineVariant = colors.line,
        error = colors.warn,
    )

    CompositionLocalProvider(LocalSdColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
