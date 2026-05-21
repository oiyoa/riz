package com.riz.app.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.TextUtilsCompat
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = ThemePrimary,
        onPrimary = Color.White,
        primaryContainer = ThemeAccent,
        onPrimaryContainer = Color.White,
        secondary = ThemeTertiary,
        onSecondary = Color(0xFF111111),
        secondaryContainer = ThemeTertiary.copy(alpha = 0.2f),
        onSecondaryContainer = ThemeTertiary,
        tertiary = ThemeSecondary,
        onTertiary = Color(0xFF111111),
        tertiaryContainer = ThemeSecondary.copy(alpha = 0.2f),
        onTertiaryContainer = ThemeSecondary,
        background = ThemeBackground,
        onBackground = ThemeText,
        surface = ThemeSurface,
        onSurface = ThemeText,
        surfaceVariant = ThemeSurfaceVariant,
        onSurfaceVariant = ThemeTextMuted,
        outline = ThemeBorder,
        error = ThemeError,
        errorContainer = ThemeErrorContainerDark,
        onError = Color.White,
        onErrorContainer = ThemeOnErrorContainerDark,
        scrim = Color.Black,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = ThemePrimary,
        onPrimary = Color.White,
        primaryContainer = ThemeAccent,
        onPrimaryContainer = Color.White,
        secondary = ThemeSecondaryLight,
        onSecondary = Color(0xFF111111),
        secondaryContainer = ThemeSecondaryLight.copy(alpha = 0.2f),
        onSecondaryContainer = ThemeSecondaryLight,
        tertiary = ThemeTertiaryLight,
        onTertiary = Color(0xFF111111),
        tertiaryContainer = ThemeTertiaryLight.copy(alpha = 0.2f),
        onTertiaryContainer = ThemeTertiaryLight,
        background = ThemeBackgroundLight,
        onBackground = ThemeTextLight,
        surface = ThemeSurfaceLight,
        onSurface = ThemeTextLight,
        surfaceVariant = ThemeSurfaceVariantLight,
        onSurfaceVariant = ThemeTextMutedLight,
        outline = ThemeBorderLight,
        error = ThemeError,
        errorContainer = ThemeErrorContainerLight,
        onError = Color.White,
        onErrorContainer = ThemeOnErrorContainerLight,
        scrim = Color.Black.copy(alpha = 0.5f),
    )

@Composable
fun RizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val viewContext = view.context
            var activity: Activity? = null
            var currentContext = viewContext
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is Activity) {
                    activity = currentContext
                    break
                }
                currentContext = currentContext.baseContext
            }

            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val customTextSelectionColors =
        TextSelectionColors(
            handleColor = colorScheme.primary,
            backgroundColor = colorScheme.primary.copy(alpha = 0.4f),
        )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RizShapes,
    ) {
        val locale = LocalConfiguration.current.locales[0]
        val isRtl = TextUtilsCompat.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
        val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

        CompositionLocalProvider(
            LocalLayoutDirection provides layoutDirection,
            LocalTextSelectionColors provides customTextSelectionColors,
        ) {
            content()
        }
    }
}
