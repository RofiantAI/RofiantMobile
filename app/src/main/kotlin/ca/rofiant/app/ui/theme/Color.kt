package ca.rofiant.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette lifted from rofiant-desktop's src/index.css @theme block.
object RofiantColors {
    val LightBackground = Color(0xFFFAF9F8)
    val LightForeground = Color(0xFF2C2826)
    val LightAccentBlue = Color(0xFF4D6BC6)
    val LightAccentGold = Color(0xFFB8934A)
    val LightSuccess = Color(0xFF2F9E6E)
    val LightWarning = Color(0xFFC17F2E)
    val LightOrange = Color(0xFFC2632A)

    val DarkBackground = Color(0xFF141414)
    val DarkForeground = Color(0xFFF5F5F4)
    val DarkAccentBlue = Color(0xFF6B8AFD)
    val DarkAccentGold = Color(0xFFD1A752)
    val DarkSuccess = Color(0xFF45B787)
    val DarkWarning = Color(0xFFE0A24D)
    val DarkOrange = Color(0xFFE0793E)

    // Syntax highlight tokens (light values match desktop's --syntax-* vars;
    // dark values reuse the matching brand dark tones for the same role).
    val SyntaxKeywordLight = Color(0xFFA3477B)
    val SyntaxKeywordDark = Color(0xFFC96B9E)
    val SyntaxStringLight = LightSuccess
    val SyntaxStringDark = DarkSuccess
    val SyntaxCommentLight = Color(0xFF9C9591)
    val SyntaxCommentDark = Color(0xFF7D7772)
    val SyntaxNumberLight = LightAccentGold
    val SyntaxNumberDark = DarkAccentGold
    val SyntaxFunctionLight = LightAccentBlue
    val SyntaxFunctionDark = DarkAccentBlue
}
