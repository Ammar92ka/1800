package com.ottoman.empire1800.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

object AppColors {
    val Gold = Color(0xFFC9A227)
    val Navy = Color(0xFF0F1F3D)
    val Card = Color(0xFF182B4F)
    val CardBorder = Color(0xFF2E4673)
    val Muted = Color(0xFFB8C2D6)
    val Good = Color(0xFF6FCF97)
    val Bad = Color(0xFFF0868A)
    val GoodBg = Color(0x336FCF97)
    val BadBg = Color(0x33F0868A)
    val SelectedBg = Color(0x1FC9A227)
    val White = Color.White
}

private val OttomanColorScheme = darkColorScheme(
    primary = AppColors.Gold,
    onPrimary = AppColors.Navy,
    secondary = AppColors.Gold,
    onSecondary = AppColors.Navy,
    secondaryContainer = AppColors.CardBorder,
    onSecondaryContainer = AppColors.White,
    background = AppColors.Navy,
    onBackground = AppColors.White,
    surface = AppColors.Card,
    onSurface = AppColors.White,
    surfaceVariant = AppColors.Card,
    onSurfaceVariant = AppColors.Muted,
    outline = AppColors.CardBorder,
    error = AppColors.Bad,
    onError = AppColors.Navy,
)

@Composable
fun OttomanTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = OttomanColorScheme,
            content = content,
        )
    }
}
