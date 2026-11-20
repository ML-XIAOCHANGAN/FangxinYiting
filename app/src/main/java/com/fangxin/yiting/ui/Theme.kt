package com.fangxin.yiting.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF14100B)
val BgSoft = Color(0xFF1D1610)
val Cream = Color(0xFFF6E7CF)
val CreamSoft = Color(0xFFD9C5A6)
val Muted = Color(0xFFA8927A)
val RiskRed = Color(0xFFFF6B4A)
val RiskAmber = Color(0xFFFFC15E)
val RiskGreen = Color(0xFFA8DDB4)
val Ink = Color(0xFF231608)

private val scheme = darkColorScheme(
    primary = Cream,
    onPrimary = Ink,
    secondary = RiskAmber,
    background = Bg,
    onBackground = Cream,
    surface = BgSoft,
    onSurface = Cream,
    error = RiskRed,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 21.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun YitingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) scheme else scheme,
        typography = typography,
        content = content,
    )
}
