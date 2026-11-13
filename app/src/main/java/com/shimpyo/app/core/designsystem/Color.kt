package com.shimpyo.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Hyundai brand colors (public brand identity values). Hyundai Blue is Shimpyo's only brand color. */
object HyundaiBrand {
    val HyundaiBlue = Color(0xFF002C5F)
    val LightSand = Color(0xFFE4DCD3)
}

/**
 * Semantic color roles. Screens use these roles, never raw hex values.
 *
 * Neutrals follow the Pleos Connect host UI (app area #F6F8FC, white panels, near-black text, measured on the
 * v2.1.0 emulator) so Shimpyo looks native next to Navigation in split view. Day is dark text on light and
 * night is light text on dark (Android for Cars driving guidelines). Every text pair is at least 4.5:1.
 */
@Immutable
data class ShimpyoColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val brand: Color,
    val onBrand: Color,
    val brandTint: Color,
    val brandText: Color,
    val positive: Color,
    val caution: Color,
    val negative: Color,
)

val LightColors = ShimpyoColors(
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEBEEF3),
    outline = Color(0xFFDCE1E8),
    textPrimary = Color(0xFF101318),
    textSecondary = Color(0xFF4E5562),
    textTertiary = Color(0xFF6B7280),
    brand = HyundaiBrand.HyundaiBlue,
    onBrand = Color(0xFFFFFFFF),
    brandTint = Color(0xFFE2E9F2),
    brandText = HyundaiBrand.HyundaiBlue,
    positive = Color(0xFF0B7A4B),
    caution = Color(0xFFA35200),
    negative = Color(0xFFC62828),
)

val DarkColors = ShimpyoColors(
    background = Color(0xFF0E1014),
    surface = Color(0xFF181B21),
    surfaceMuted = Color(0xFF242831),
    outline = Color(0xFF2F343E),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFFB3B9C4),
    textTertiary = Color(0xFF8C93A0),
    brand = Color(0xFF3B6BAF), // Hyundai Blue lifted so buttons stay visible at night
    onBrand = Color(0xFFFFFFFF),
    brandTint = Color(0xFF1B2738),
    brandText = Color(0xFFA9C3E8),
    positive = Color(0xFF5BCB93),
    caution = Color(0xFFF2B45C),
    negative = Color(0xFFFF7A6B),
)

/** The nap screen ignores day/night: it dims with the cabin, then brightens to Light Sand like a sunrise. */
object NapScene {
    val Night = Color(0xFF0A0B0D)
    val NightText = Color(0xFFE9E1D6)
    val NightTextDim = Color(0xFF8F887F)
    val NightButton = Color(0xFF1C1D21)
    val Sunrise = HyundaiBrand.LightSand
    val SunriseText = Color(0xFF2B2118)
    val SunriseTextDim = Color(0xFF5E5145)
    val SunriseButton = Color(0xFFD3C8BB)
}

/** Cabin ambient light colors used by the rest choreography (CabinOrchestrator). */
object SceneColor {
    val Dusk = Color(0xFFFF8A3D) // warm, low light for falling asleep
    val Sunrise = Color(0xFFFFC27A)
    val Daylight = Color(0xFFE8F1FF) // cool white for waking up
}

internal val LocalShimpyoColors = staticCompositionLocalOf { LightColors }
