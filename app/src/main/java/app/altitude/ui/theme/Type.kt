package app.altitude.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.altitude.R

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

object AltitudeTypefaces {
    var ready: Boolean = false
        private set

    lateinit var regular: Typeface
        private set
    lateinit var medium: Typeface
        private set
    lateinit var semibold: Typeface
        private set
    lateinit var extraBold: Typeface
        private set

    val cjk: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }

    fun load(context: Context) {
        regular = context.resources.getFont(R.font.outfit_regular)
        medium = context.resources.getFont(R.font.outfit_medium)
        semibold = context.resources.getFont(R.font.outfit_semibold)
        extraBold = context.resources.getFont(R.font.outfit_extrabold)
        ready = true
    }

    fun mediumOrDefault(): Typeface = if (ready) medium else Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun semiboldOrDefault(): Typeface = if (ready) semibold else Typeface.create("sans-serif-medium", Typeface.BOLD)

    fun regularOrDefault(): Typeface = if (ready) regular else Typeface.SANS_SERIF
}
