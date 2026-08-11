package ca.rofiant.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Roles Material3's ColorScheme has no slot for (success/warning/syntax
// highlight) but the desktop app defines as first-class brand colors.
data class RofiantExtraColors(
    val success: Color,
    val warning: Color,
    val orange: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxComment: Color,
    val syntaxNumber: Color,
    val syntaxFunction: Color,
)

private val LightExtraColors = RofiantExtraColors(
    success = RofiantColors.LightSuccess,
    warning = RofiantColors.LightWarning,
    orange = RofiantColors.LightOrange,
    syntaxKeyword = RofiantColors.SyntaxKeywordLight,
    syntaxString = RofiantColors.SyntaxStringLight,
    syntaxComment = RofiantColors.SyntaxCommentLight,
    syntaxNumber = RofiantColors.SyntaxNumberLight,
    syntaxFunction = RofiantColors.SyntaxFunctionLight,
)

private val DarkExtraColors = RofiantExtraColors(
    success = RofiantColors.DarkSuccess,
    warning = RofiantColors.DarkWarning,
    orange = RofiantColors.DarkOrange,
    syntaxKeyword = RofiantColors.SyntaxKeywordDark,
    syntaxString = RofiantColors.SyntaxStringDark,
    syntaxComment = RofiantColors.SyntaxCommentDark,
    syntaxNumber = RofiantColors.SyntaxNumberDark,
    syntaxFunction = RofiantColors.SyntaxFunctionDark,
)

val LocalRofiantExtraColors = staticCompositionLocalOf { LightExtraColors }

private val LightColors = lightColorScheme(
    primary = RofiantColors.LightAccentBlue,
    onPrimary = Color.White,
    secondary = RofiantColors.LightAccentGold,
    onSecondary = Color.White,
    tertiary = RofiantColors.LightOrange,
    onTertiary = Color.White,
    background = RofiantColors.LightBackground,
    onBackground = RofiantColors.LightForeground,
    surface = RofiantColors.LightBackground,
    onSurface = RofiantColors.LightForeground,
    surfaceVariant = Color(0xFFEFEDEB),
    onSurfaceVariant = RofiantColors.LightForeground,
    outline = Color(0xFFD8D4D0),
)

private val DarkColors = darkColorScheme(
    primary = RofiantColors.DarkAccentBlue,
    onPrimary = Color(0xFF141414),
    secondary = RofiantColors.DarkAccentGold,
    onSecondary = Color(0xFF141414),
    tertiary = RofiantColors.DarkOrange,
    onTertiary = Color(0xFF141414),
    background = RofiantColors.DarkBackground,
    onBackground = RofiantColors.DarkForeground,
    surface = RofiantColors.DarkBackground,
    onSurface = RofiantColors.DarkForeground,
    surfaceVariant = Color(0xFF232323),
    onSurfaceVariant = RofiantColors.DarkForeground,
    outline = Color(0xFF3A3A3A),
)

@Composable
fun RofiantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val extra = if (darkTheme) DarkExtraColors else LightExtraColors
    CompositionLocalProvider(LocalRofiantExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colors,
            typography = RofiantTypography,
            content = content,
        )
    }
}
