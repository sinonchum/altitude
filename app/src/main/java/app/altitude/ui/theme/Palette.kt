package app.altitude.ui.theme

import androidx.compose.ui.graphics.Color
import app.altitude.domain.DisplayTheme
import app.altitude.domain.FixQuality

data class Palette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
)

fun DisplayTheme.palette(): Palette = when (this) {
    DisplayTheme.NIGHT -> Palette(Color(0xFF090A0C), Color(0xFFF3F0E8), Color(0xFF9A958C), Color(0xFFE10600))
    DisplayTheme.VOID -> Palette(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFC8C8C8), Color(0xFFE10600))
    DisplayTheme.GLACIER -> Palette(Color(0xFF071018), Color(0xFFEAF6FF), Color(0xFF8AA4B5), Color(0xFF7ED0FF))
    DisplayTheme.SAFFRON -> Palette(Color(0xFF100E0A), Color(0xFFF6C453), Color(0xFFCDB892), Color(0xFFE10600))
    DisplayTheme.SNOW -> Palette(Color(0xFFF4F2EC), Color(0xFF141210), Color(0xFF5C574E), Color(0xFFE10600))
}

fun FixQuality.dot(): Color = when (this) {
    FixQuality.EXCELLENT -> Color(0xFF7DDEA0)
    FixQuality.GOOD -> Color(0xFFC5D67A)
    FixQuality.FAIR -> Color(0xFFE6B15A)
    FixQuality.POOR -> Color(0xFFE07A5F)
    FixQuality.SEARCHING, FixQuality.NONE -> Color(0xFF9A958C)
}

fun DisplayTheme.label(): String = when (this) {
    DisplayTheme.NIGHT -> "夜色"
    DisplayTheme.VOID -> "纯黑"
    DisplayTheme.GLACIER -> "冰川"
    DisplayTheme.SAFFRON -> "暖金"
    DisplayTheme.SNOW -> "雪地"
}
