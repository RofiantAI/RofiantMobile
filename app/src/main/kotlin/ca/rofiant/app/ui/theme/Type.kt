package ca.rofiant.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Uses the platform system font (Roboto) rather than desktop's Inter web
// font — matching platform typography reads as native, and avoids pulling
// in a Google Fonts download dependency for a single family swap.
val RofiantTypography = Typography()

val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 19.sp,
)
