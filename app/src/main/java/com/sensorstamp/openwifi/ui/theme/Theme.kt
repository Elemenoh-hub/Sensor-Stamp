package com.sensorstamp.openwifi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = SignalBlueLight,
    onPrimary = SignalBlueDark,
    primaryContainer = SignalBlue,
    onPrimaryContainer = Color.White,
    secondary = LiveTealLight,
    onSecondary = LiveTealDark,
    secondaryContainer = LiveTealDark,
    onSecondaryContainer = LiveTealLight,
    tertiary = AlertAmber,
    onTertiary = AlertAmberDark,
    tertiaryContainer = AlertAmberDark,
    onTertiaryContainer = AlertAmber,
    background = InkBlack,
    onBackground = Color(0xFFE4E8F0),
    surface = InkBlack,
    onSurface = Color(0xFFE4E8F0),
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Color(0xFFA8B2C1),
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerLow = Color(0xFF10151C),
    outline = InkOutline,
    outlineVariant = Color(0xFF262E3A),
    error = DangerRed,
)

private val LightColors = lightColorScheme(
    primary = SignalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = SignalBlueDark,
    secondary = Color(0xFF00806D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F5E9),
    onSecondaryContainer = LiveTealDark,
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2BC),
    onTertiaryContainer = AlertAmberDark,
    background = PaperWhite,
    onBackground = Color(0xFF12161D),
    surface = PaperWhite,
    onSurface = Color(0xFF12161D),
    surfaceVariant = PaperSurfaceHigh,
    onSurfaceVariant = Color(0xFF4A5260),
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceHigh,
    surfaceContainerLow = Color(0xFFF7F9FD),
    outline = PaperOutline,
    outlineVariant = Color(0xFFDDE2EC),
    error = Color(0xFFC0272C),
)

@Composable
fun SensorStampTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = SensorStampTypography,
        shapes = SensorStampShapes,
        content = content,
    )
}
