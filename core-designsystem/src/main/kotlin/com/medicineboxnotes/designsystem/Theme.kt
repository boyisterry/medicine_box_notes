package com.medicineboxnotes.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MBColor {
    val Paper = Color(0xFFFAF7F0)
    val Surface = Color(0xFFFFFEFC)
    val Ink = Color(0xFF24211E)
    val Ink2 = Ink.copy(alpha = .72f)
    val Ink3 = Ink.copy(alpha = .52f)
    val Hairline = Ink.copy(alpha = .10f)
    val Primary = Color(0xFFB85C3A)
    val PrimarySoft = Color(0xFFF4E3DC)
    val Success = Color(0xFF6F8A5B)
    val SuccessSoft = Color(0xFFE5EBDD)
    val Warning = Color(0xFFC97928)
    val WarningSoft = Color(0xFFF7E8D3)
    val AI = Color(0xFF7C6CA8)
    val AISoft = Color(0xFFEAE6F3)
}

object MBDimens {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 28.dp
    val cardRadius = 26.dp; val controlRadius = 22.dp; val chipRadius = 18.dp
}

private val colors = lightColorScheme(
    primary = MBColor.Primary, onPrimary = Color.White,
    primaryContainer = MBColor.PrimarySoft, onPrimaryContainer = MBColor.Ink,
    secondary = MBColor.Success, secondaryContainer = MBColor.SuccessSoft,
    tertiary = MBColor.AI, tertiaryContainer = MBColor.AISoft,
    background = MBColor.Paper, onBackground = MBColor.Ink,
    surface = MBColor.Surface, onSurface = MBColor.Ink,
    outline = MBColor.Hairline,
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp),
)

@Composable fun MedicineBoxTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ignored = isSystemInDarkTheme()
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

val MemberPalette = listOf(
    Color(0xFFE8D5C4) to Color(0xFF8B5A3C),
    Color(0xFFEBC9CC) to Color(0xFF9C5560),
    Color(0xFFD4D8C8) to Color(0xFF5C7058),
    Color(0xFFF2DDB0) to Color(0xFFA07B2F),
)
