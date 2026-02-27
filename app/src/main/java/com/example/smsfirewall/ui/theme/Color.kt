package com.example.smsfirewall.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val BrandPrimary = Color(0xFF0B2F5B)
val BrandSecondary = Color(0xFF0E7490)
val BrandTertiary = Color(0xFFD97706)

val AppBackground = Color(0xFFF5F7FB)
val AppSurface = Color(0xFFFFFFFF)
val AppSurfaceVariant = Color(0xFFE7EDF6)
val AppOnSurface = Color(0xFF0E1A2B)
val AppOnSurfaceVariant = Color(0xFF5C6A83)

val WarningContainer = Color(0xFFFFF2EC)
val WarningContainerStrong = Color(0xFFFFD8CB)
val WarningOnContainer = Color(0xFF8A2E0B)

val ChatSentBubble = Color(0xFF1D4ED8)
val ChatReceivedBubble = Color(0xFFEAF0FF)

val AvatarBlueStart = Color(0xFF3B82F6)
val AvatarBlueEnd = Color(0xFF22D3EE)
val AvatarRedStart = Color(0xFFF97316)
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
