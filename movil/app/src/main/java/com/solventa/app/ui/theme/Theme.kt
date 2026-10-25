package com.solventa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Tokens de prototipos/movil/styles.css
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5474),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0B5474),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF090D14),
    primaryContainer = Color(0xFF0C2738),
    onPrimaryContainer = Color(0xFF7DD3FC),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF090D14),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF131924),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1A2230),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF243042),
    error = Color(0xFFF87171),
)

/** Colores del mockup que Material no tiene: estados, aviso de sin conexión y degradados de marca. */
@Immutable
data class SolventaColors(
    val ok: Color,
    val okSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val errorSoft: Color,
    val neutralSoft: Color,
    val brand: Brush,
    val cardFront: Brush,
    val cardBack: Brush,
)

private val brand = Brush.linearGradient(listOf(Color(0xFF0F6D94), Color(0xFF0D9488)))
// Degradado del carné del mockup, oscurecido para que el texto blanco cumpla 4.5:1.
private val cardFront = Brush.linearGradient(listOf(Color(0xFF0B5474), Color(0xFF0F766E), Color(0xFF047857)))
private val cardBack = Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))

private val LightExtra = SolventaColors(
    ok = Color(0xFF15803D),
    okSoft = Color(0xFFDCFCE7),
    warning = Color(0xFFB45309),
    warningSoft = Color(0xFFFEF3C7),
    errorSoft = Color(0xFFFEE2E2),
    neutralSoft = Color(0xFFF1F5F9),
    brand = brand,
    cardFront = cardFront,
    cardBack = cardBack,
)

private val DarkExtra = SolventaColors(
    ok = Color(0xFF4ADE80),
    okSoft = Color(0xFF062A14),
    warning = Color(0xFFFBBF24),
    warningSoft = Color(0xFF332005),
    errorSoft = Color(0xFF330D0D),
    neutralSoft = Color(0xFF1A2230),
    brand = brand,
    cardFront = cardFront,
    cardBack = cardBack,
)

private val LocalSolventaColors = staticCompositionLocalOf { LightExtra }

// Radios --r-sm, --r-md y --r-lg del mockup.
private val SolventaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

object SolventaTheme {
    val colors: SolventaColors
        @Composable get() = LocalSolventaColors.current
}

@Composable
fun SolventaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSolventaColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = SolventaShapes,
            content = content,
        )
    }
}
