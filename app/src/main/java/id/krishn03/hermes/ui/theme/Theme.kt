package id.krishn03.hermes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Claude-inspired warm palette: paper backgrounds, terracotta accent.
private val Clay = Color(0xFFD97757)
private val ClayDim = Color(0xFFC96442)

/** Loading / progress accent — a soft purple, per request. */
val HermesPurple = Color(0xFF6C5CE7)

private val LightColors = lightColorScheme(
    primary = ClayDim,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E6DE),
    onPrimaryContainer = Color(0xFF3A1C10),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF1F1E1D),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF1F1E1D),
    surfaceVariant = Color(0xFFEDE9E0),
    onSurfaceVariant = Color(0xFF5C5A54),
    surfaceContainer = Color(0xFFF0EEE6),
    surfaceContainerHigh = Color(0xFFEAE7DD),
    outline = Color(0xFFD6D2C7),
    outlineVariant = Color(0xFFE3DFD4),
    secondaryContainer = Color(0xFFEDE9E0),
    onSecondaryContainer = Color(0xFF1F1E1D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Clay,
    onPrimary = Color(0xFF231007),
    primaryContainer = Color(0xFF4A2A1B),
    onPrimaryContainer = Color(0xFFF3E6DE),
    background = Color(0xFF1F1E1D),
    onBackground = Color(0xFFF5F1EC),
    surface = Color(0xFF1F1E1D),
    onSurface = Color(0xFFF5F1EC),
    surfaceVariant = Color(0xFF3A3835),
    onSurfaceVariant = Color(0xFF8C8A86),
    surfaceContainer = Color(0xFF2B2A28),
    surfaceContainerHigh = Color(0xFF3A3835),
    outline = Color(0xFF4A4842),
    outlineVariant = Color(0xFF3A3833),
    secondaryContainer = Color(0xFF3A3835),
    onSecondaryContainer = Color(0xFFF5F1EC),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

private val HermesTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 30.sp,
        ),
        headlineSmall = headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 25.sp,
        ),
    )
}

@Composable
fun HermesTheme(
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = HermesTypography,
        content = content,
    )
}
