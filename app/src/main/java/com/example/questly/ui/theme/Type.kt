package com.example.questly.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val default = FontFamily.Default

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = default, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
)
