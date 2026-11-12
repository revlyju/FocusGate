package com.example.myapplication.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════════════════
//  FocusGate — Warm Amber & Obsidian Material 3 Dark Scheme
//  Dynamic colour is intentionally disabled so our palette is always applied.
// ═══════════════════════════════════════════════════════════════════════════════

private val FocusGateColorScheme = darkColorScheme(
    primary                 = AmberPrimary,
    onPrimary               = ObsidianBackground,
    primaryContainer        = AmberContainer,
    onPrimaryContainer      = PeachSand,

    secondary               = PeachSand,
    onSecondary             = ObsidianBackground,
    secondaryContainer      = PeachSandContainer,
    onSecondaryContainer    = PeachSand,

    tertiary                = AmberDeep,
    onTertiary              = WarmWhite,
    tertiaryContainer       = AmberContainer,
    onTertiaryContainer     = PeachSand,

    background              = ObsidianBackground,
    onBackground            = WarmWhite,

    surface                 = CharcoalSurface,
    onSurface               = WarmWhite,
    surfaceVariant          = CharcoalSurfaceVar,
    onSurfaceVariant        = MutedTaupe,

    outline                 = SubtleBorder,
    outlineVariant          = SubtleBorder,

    error                   = CrimsonAlert,
    onError                 = WarmWhite,
    errorContainer          = CrimsonAlertContainer,
    onErrorContainer        = Color(0xFFFFB4AB),

    inverseSurface          = WarmWhite,
    inverseOnSurface        = ObsidianBackground,
    inversePrimary          = AmberDeep,

    scrim                   = ObsidianBackground,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color intentionally disabled — we always use Warm Amber & Obsidian
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = ObsidianBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = FocusGateColorScheme,
        typography  = Typography,
        content     = content
    )
}
