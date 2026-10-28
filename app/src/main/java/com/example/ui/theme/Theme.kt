package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BitCallDarkColorScheme = darkColorScheme(
    primary = BitCallPrimaryCyan,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = BitCallSecondaryBlue,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = BitCallTertiaryPurple,
    background = BitCallBackgroundDark,
    onBackground = BitCallTextPrimaryDark,
    surface = BitCallSurfaceDark,
    onSurface = BitCallTextPrimaryDark,
    surfaceVariant = BitCallSurfaceVariantDark,
    onSurfaceVariant = BitCallTextSecondaryDark,
    error = BitCallErrorRed
)

private val BitCallLightColorScheme = lightColorScheme(
    primary = BitCallPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = BitCallSecondaryLight,
    onSecondary = Color.White,
    background = BitCallBackgroundLight,
    onBackground = BitCallTextPrimaryLight,
    surface = BitCallSurfaceLight,
    onSurface = BitCallTextPrimaryLight,
    surfaceVariant = BitCallSurfaceVariantLight,
    onSurfaceVariant = BitCallTextSecondaryLight,
    error = BitCallErrorRed
)

@Composable
fun BitCallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve BitCall branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BitCallDarkColorScheme
        else -> BitCallLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Retain alias for template compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = BitCallTheme(darkTheme, dynamicColor, content)
