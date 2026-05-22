package com.filestech.sos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.filestech.sos.data.local.datastore.Appearance
import com.filestech.sos.data.local.datastore.ThemeMode

@Composable
fun SosTechTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.DARK_TECH -> true
    }
    val ctx = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appearance.dynamicColors

    val scheme = when {
        dynamicAvailable && useDark -> dynamicDarkColorScheme(ctx)
            .copy(inverseSurface = SnackbarBg, inverseOnSurface = SnackbarOn)
            .let { if (appearance.amoledTrueBlack) it.copy(background = Color.Black, surface = Color.Black) else it }
        dynamicAvailable && !useDark -> dynamicLightColorScheme(ctx)
            .copy(inverseSurface = SnackbarBg, inverseOnSurface = SnackbarOn)
        useDark -> darkScheme(appearance.amoledTrueBlack)
        else -> lightScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
