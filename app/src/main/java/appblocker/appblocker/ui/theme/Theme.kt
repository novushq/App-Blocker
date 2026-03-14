package appblocker.appblocker.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = FocusGreen,
    onPrimary = Color.Black,
    primaryContainer = FocusGreenContainer,
    onPrimaryContainer = FocusText,
    secondary = FocusGreen,
    onSecondary = Color.Black,
    background = FocusBg,
    onBackground = FocusText,
    surface = FocusSurface,
    onSurface = FocusText,
    surfaceVariant = FocusSurfaceAlt,
    onSurfaceVariant = FocusMutedText,
    outline = FocusBorder,
    outlineVariant = FocusBorder.copy(alpha = 0.7f),
    error = FocusError
)

private val LightColorScheme = lightColorScheme(
    primary = FocusGreen,
    onPrimary = Color.Black,
    background = Color(0xFFF7FBF7),
    onBackground = Color(0xFF151A16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A16),
    surfaceVariant = Color(0xFFE8EFE8),
    onSurfaceVariant = Color(0xFF3C4A3E),
    outline = Color(0xFFA4B3A6)
)

@Composable
fun AppBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}