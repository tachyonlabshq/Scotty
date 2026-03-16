package org.localsend.localsend_app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── Scotty Brand Palette ─────────────────────────────────────────────
// Teal/Cyan identity — distinct from Material default purple
private val Teal10  = Color(0xFF001F24)
private val Teal20  = Color(0xFF003640)
private val Teal40  = Color(0xFF006874)
private val Teal80  = Color(0xFF4DD0E1)
private val Teal90  = Color(0xFFB2EBF2)
private val Teal99  = Color(0xFFF0FDFF)

private val Cyan30  = Color(0xFF004F58)
private val Cyan60  = Color(0xFF00ACC1)
private val Cyan80  = Color(0xFF80DEEA)

private val TealGrey30 = Color(0xFF1A3A3F)
private val TealGrey80 = Color(0xFFB2CDD2)
private val TealGrey90 = Color(0xFFCFE6EA)

private val ScottyLightColorScheme = lightColorScheme(
    primary              = Teal40,
    onPrimary            = Color.White,
    primaryContainer     = Teal90,
    onPrimaryContainer   = Teal10,
    secondary            = Color(0xFF4A6268),
    onSecondary          = Color.White,
    secondaryContainer   = TealGrey90,
    onSecondaryContainer = Color(0xFF051F23),
    tertiary             = Color(0xFF00796B),
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFB2DFDB),
    onTertiaryContainer  = Color(0xFF00201C),
    background           = Color(0xFFE0F7FA),
    onBackground         = Teal10,
    surface              = Color(0xFFE0F7FA),
    onSurface            = Teal10,
    surfaceVariant       = Color(0xFFB2EBF2),
    onSurfaceVariant     = Color(0xFF3F4F52),
    surfaceContainer     = Color(0xFFCCF0F4),
    surfaceContainerLow  = Color(0xFFD6F3F7),
    surfaceContainerHigh = Color(0xFFA8E6EF),
    outline              = Color(0xFF6F7F82),
    error                = Color(0xFFBA1A1A),
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),
)

private val ScottyDarkColorScheme = darkColorScheme(
    primary              = Teal80,
    onPrimary            = Teal20,
    primaryContainer     = Cyan30,
    onPrimaryContainer   = Teal90,
    secondary            = TealGrey80,
    onSecondary          = Color(0xFF1F3438),
    secondaryContainer   = Color(0xFF354D51),
    onSecondaryContainer = TealGrey90,
    tertiary             = Color(0xFF80CBC4),
    onTertiary           = Color(0xFF00352F),
    tertiaryContainer    = Color(0xFF004D46),
    onTertiaryContainer  = Color(0xFFB2DFDB),
    background           = Color(0xFF0A1D1F),
    onBackground         = Teal90,
    surface              = Color(0xFF0A1D1F),
    onSurface            = Teal90,
    surfaceVariant       = TealGrey30,
    onSurfaceVariant     = TealGrey80,
    surfaceContainer     = Color(0xFF152E33),
    surfaceContainerLow  = Color(0xFF111F23),
    surfaceContainerHigh = Color(0xFF1A3A3F),
    outline              = Color(0xFF899EA1),
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),
)

// ── Scotty Typography ─────────────────────────────────────────────────
val ScottyTypography = Typography(
    displayLarge   = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Black,     letterSpacing = (-0.25).sp),
    displayMedium  = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
    displaySmall   = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold,      letterSpacing = 0.sp),
    headlineLarge  = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold,      letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.sp),
    headlineSmall  = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.sp),
    titleLarge     = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.sp),
    titleMedium    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.15.sp),
    titleSmall     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.1.sp),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold,  letterSpacing = 0.5.sp),
    labelSmall     = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium,    letterSpacing = 0.5.sp),
    bodyLarge      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal,    letterSpacing = 0.5.sp),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal,    letterSpacing = 0.25.sp),
    bodySmall      = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal,    letterSpacing = 0.4.sp),
)

// ── Scotty Shapes ─────────────────────────────────────────────────────
val ScottyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ScottyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ScottyDarkColorScheme
        else -> ScottyLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ScottyTypography,
        shapes      = ScottyShapes,
        content     = content
    )
}

// Keep alias so any leftover references compile
@Composable
fun LocalSendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = ScottyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
