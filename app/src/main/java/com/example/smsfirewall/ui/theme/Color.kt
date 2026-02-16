package com.example.smsfirewall.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val BrandPrimary = Color(0xFF13579B)
val BrandSecondary = Color(0xFF0D9488)
val BrandTertiary = Color(0xFFE76F51)

val AppBackground = Color(0xFFF2F6FB)
val AppSurface = Color(0xFFFFFFFF)
val AppSurfaceVariant = Color(0xFFE4EAF4)
val AppOnSurface = Color(0xFF162033)
val AppOnSurfaceVariant = Color(0xFF6A788F)

val WarningContainer = Color(0xFFFFF1EE)
val WarningContainerStrong = Color(0xFFFFDDD5)
val WarningOnContainer = Color(0xFF8F1D21)

val ChatSentBubble = Color(0xFF2F6FED)
val ChatReceivedBubble = Color(0xFFE5ECF8)

val AvatarBlueStart = Color(0xFF5A7EFA)
val AvatarBlueEnd = Color(0xFF4BC6E8)
val AvatarRedStart = Color(0xFFFB7185)
val AvatarRedEnd = Color(0xFFEF4444)

val AppColorScheme = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    tertiary = BrandTertiary,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceVariant,
    onSurface = AppOnSurface,
    onSurfaceVariant = AppOnSurfaceVariant,
    error = WarningOnContainer
)
