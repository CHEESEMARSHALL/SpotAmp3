package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Curated customizable dark themes
private val DefaultDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8B5CF6), // Violet
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0F0F15),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF161622),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155)
)

private val EmeraldAuroraColorScheme = darkColorScheme(
    primary = Color(0xFF10B981), // Emerald
    onPrimary = Color(0xFF022C22),
    secondary = Color(0xFF06B6D4), // Cyan
    onSecondary = Color(0xFF083344),
    background = Color(0xFF022C22),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF064E3B),
    onSurface = Color(0xFFF0FDF4),
    surfaceVariant = Color(0xFF047857),
    onSurfaceVariant = Color(0xFFD1FAE5),
    outline = Color(0xFF065F46)
)

private val CyberpunkNeonColorScheme = darkColorScheme(
    primary = Color(0xFFEC4899), // Neon Pink
    onPrimary = Color(0xFF500724),
    secondary = Color(0xFFA855F7), // Neon Purple
    onSecondary = Color(0xFF3B0764),
    background = Color(0xFF03000A),
    onBackground = Color(0xFFFDF2F8),
    surface = Color(0xFF130026),
    onSurface = Color(0xFFFDF2F8),
    surfaceVariant = Color(0xFF26004C),
    onSurfaceVariant = Color(0xFFF5D0FE),
    outline = Color(0xFF581C87)
)

private val SunsetGlowColorScheme = darkColorScheme(
    primary = Color(0xFFF97316), // Orange
    onPrimary = Color(0xFF431407),
    secondary = Color(0xFFEF4444), // Red
    onSecondary = Color(0xFF450A0A),
    background = Color(0xFF180C0B),
    onBackground = Color(0xFFFFF7ED),
    surface = Color(0xFF2E1513),
    onSurface = Color(0xFFFFF7ED),
    surfaceVariant = Color(0xFF4D1D19),
    onSurfaceVariant = Color(0xFFFFEDD5),
    outline = Color(0xFF7C2D12)
)

private val CosmicObsidianColorScheme = darkColorScheme(
    primary = Color(0xFFF8FAFC), // Pure White-Silver
    onPrimary = Color(0xFF020617),
    secondary = Color(0xFF94A3B8), // Cool Gray
    onSecondary = Color(0xFF020617),
    background = Color(0xFF000000), // Pitch Black
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFF262626)
)

@Composable
fun MyApplicationTheme(
    activeTheme: String = "Default Dark",
    content: @Composable () -> Unit
) {
    val colorScheme = when (activeTheme) {
        "Emerald Aurora" -> EmeraldAuroraColorScheme
        "Cyberpunk Neon" -> CyberpunkNeonColorScheme
        "Sunset Glow" -> SunsetGlowColorScheme
        "Cosmic Obsidian" -> CosmicObsidianColorScheme
        "Midnight Blue" -> MidnightBlueColorScheme
        "Deep Amber" -> DeepAmberColorScheme
        "Rose Gold" -> RoseGoldColorScheme
        "Matrix Green" -> MatrixGreenColorScheme
        "Nordic Frost" -> NordicFrostColorScheme
        "Solarized Dark" -> SolarizedDarkColorScheme
        "Warm Light" -> WarmLightColorScheme
        else -> DefaultDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private val MidnightBlueColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6), // Blue-500
    onPrimary = Color(0xFFEFF6FF),
    secondary = Color(0xFF60A5FA), // Blue-400
    onSecondary = Color(0xFF1E3A8A),
    background = Color(0xFF0F172A), // Slate-900
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B), // Slate-800
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155), // Slate-700
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569)
)

private val DeepAmberColorScheme = darkColorScheme(
    primary = Color(0xFFD97706), // Amber-600
    onPrimary = Color(0xFFFFFBEB),
    secondary = Color(0xFFF59E0B), // Amber-500
    onSecondary = Color(0xFF451A03),
    background = Color(0xFF171717), // Neutral-900
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF262626), // Neutral-800
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF404040), // Neutral-700
    onSurfaceVariant = Color(0xFFD4D4D4),
    outline = Color(0xFF525252)
)

private val RoseGoldColorScheme = darkColorScheme(
    primary = Color(0xFFF43F5E), // Rose
    onPrimary = Color(0xFFFFF1F2),
    secondary = Color(0xFFFB7185), // Light rose
    onSecondary = Color(0xFF4C0519),
    background = Color(0xFF0F0508), // Very dark rose-gray
    onBackground = Color(0xFFFFE4E6),
    surface = Color(0xFF1C0A10),
    onSurface = Color(0xFFFFE4E6),
    surfaceVariant = Color(0xFF2E121C),
    onSurfaceVariant = Color(0xFFFCA5A5),
    outline = Color(0xFF4C0519)
)

private val MatrixGreenColorScheme = darkColorScheme(
    primary = Color(0xFF10B981), // Emerald green
    onPrimary = Color(0xFF022C22),
    secondary = Color(0xFF22C55E), // Bright green
    onSecondary = Color(0xFF052E16),
    background = Color(0xFF020604), // Near pitch-black green
    onBackground = Color(0xFFD1FAE5),
    surface = Color(0xFF06140C),
    onSurface = Color(0xFFD1FAE5),
    surfaceVariant = Color(0xFF0B2416),
    onSurfaceVariant = Color(0xFFA7F3D0),
    outline = Color(0xFF064E3B)
)

private val NordicFrostColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8), // Ice blue
    onPrimary = Color(0xFF0369A1),
    secondary = Color(0xFF22D3EE), // Cyan
    onSecondary = Color(0xFF0891B2),
    background = Color(0xFF080E1A), // Cold dark blue
    onBackground = Color(0xFFE0F2FE),
    surface = Color(0xFF0F1B2E),
    onSurface = Color(0xFFE0F2FE),
    surfaceVariant = Color(0xFF1E2E4A),
    onSurfaceVariant = Color(0xFFBAE6FD),
    outline = Color(0xFF0369A1)
)

private val SolarizedDarkColorScheme = darkColorScheme(
    primary = Color(0xFF268BD2), // Blue
    onPrimary = Color(0xFFFDF6E3),
    secondary = Color(0xFF2AA198), // Teal
    onSecondary = Color(0xFFFDF6E3),
    background = Color(0xFF002B36),
    onBackground = Color(0xFF93A1A1),
    surface = Color(0xFF073642),
    onSurface = Color(0xFF93A1A1),
    surfaceVariant = Color(0xFF586E75),
    onSurfaceVariant = Color(0xFF93A1A1),
    outline = Color(0xFF586E75)
)

private val WarmLightColorScheme = lightColorScheme(
    primary = Color(0xFFD97706), // Warm amber
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF78350F), // Warm brown
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFDFBF7), // Soft cream
    onBackground = Color(0xFF1E1B18),
    surface = Color(0xFFF5F0E6),
    onSurface = Color(0xFF1E1B18),
    surfaceVariant = Color(0xFFEDE4D3),
    onSurfaceVariant = Color(0xFF451A03),
    outline = Color(0xFF78350F)
)
