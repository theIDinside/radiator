package com.app.radiator.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

object RadiatorTheme {
    val colors: RadiatorThemeColors
        @Composable
        @ReadOnlyComposable
        get() = radiatorThemeColorsSetting.current
}

val radiatorThemeColorsSetting = staticCompositionLocalOf { lightRadiatorTheme }

@Stable
class RadiatorThemeColors(
    buttonIconTint: Color,
    messageFromMeBackground: Color,
    messageFromOtherBackground: Color,
    messageHighlightedBackground: Color,
    quaternary: Color,
    quinary: Color,
    textActionCritical: Color,
    isLight: Boolean,
) {

    var buttonIconTint by mutableStateOf(buttonIconTint)
        private set
    var messageFromMeBackground by mutableStateOf(messageFromMeBackground)
        private set
    var messageFromOtherBackground by mutableStateOf(messageFromOtherBackground)
        private set
    var messageHighlightedBackground by mutableStateOf(messageHighlightedBackground)
        private set

    var quaternary by mutableStateOf(quaternary)
        private set

    var quinary by mutableStateOf(quinary)
        private set

    var textActionCritical by mutableStateOf(textActionCritical)
        private set

    var isLight by mutableStateOf(isLight)
        private set

    fun updateColorsFrom(other: RadiatorThemeColors) {
        messageFromMeBackground = other.messageFromMeBackground
        messageFromOtherBackground = other.messageFromOtherBackground
        messageHighlightedBackground = other.messageHighlightedBackground
        quaternary = other.quaternary
        quinary = other.quinary
        textActionCritical = other.textActionCritical
        isLight = other.isLight
    }
}

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val SystemGreyLight = Color(0xFF8E8E93)
val SystemGreyDark = Color(0xFF8E8E93)
val SystemGrey2Light = Color(0xFFAEAEB2)
val SystemGrey2Dark = Color(0xFF636366)
val SystemGrey3Light = Color(0xFFC7C7CC)
val SystemGrey3Dark = Color(0xFF48484A)
val SystemGrey4Light = Color(0xFFD1D1D6)
val SystemGrey4Dark = Color(0xFF3A3A3C)
val SystemGrey5Light = Color(0xFFE5E5EA)
val SystemGrey5Dark = Color(0xFF2C2C2E)
val SystemGrey6Light = Color(0xFFF2F2F7)
val SystemGrey6Dark = Color(0xFF1C1C1E)

// For light themes
val Gray_25 = Color(0xFFF4F6FA)
val Gray_50 = Color(0xFFE3E8F0)
val Gray_100 = Color(0xFFC1C6CD)
val Gray_150 = Color(0xFF8D97A5)
val Gray_200 = Color(0xFF737D8C)
val Black_900 = Color(0xFF17191C)

// For dark themes
val Gray_250 = Color(0xFFA9B2BC)
val Gray_300 = Color(0xFF8E99A4)
val Gray_400 = Color(0xFF6F7882)
val Gray_450 = Color(0xFF394049)
val Black_800 = Color(0xFF15191E)
val Black_950 = Color(0xFF21262C)

val Azure = Color(0xFF368BD6)
val Kiwi = Color(0xFF74D12C)
val Grape = Color(0xFFAC3BA8)
val Verde = Color(0xFF03B381)
val Polly = Color(0xFFE64F7A)
val Melon = Color(0xFFFF812D)

val ElementGreen = Color(0xFF0DBD8B)
val ElementOrange = Color(0xFFD9B072)
val Vermilion = Color(0xFFFF5B55)

val LinkColor = Color(0xFF0086E6)

val TextColorCriticalLight = Color(0xFFD51928)
val TextColorCriticalDark = Color(0xfffd3e3c)

val lightRadiatorTheme = RadiatorThemeColors(
    messageFromMeBackground = SystemGrey5Light,
    messageFromOtherBackground = SystemGrey6Light,
    messageHighlightedBackground = Azure,
    quaternary = Gray_100,
    quinary = Gray_50,
    textActionCritical = TextColorCriticalLight,
    isLight = true,
    buttonIconTint = Gray_250
)
