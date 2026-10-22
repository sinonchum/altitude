package app.altitude.domain

import kotlin.math.min

data class PixelSize(val width: Int, val height: Int)

data class FrameFit(
    val photoWidth: Float,
    val photoHeight: Float,
    val side: Float,
    val top: Float,
    val bottom: Float,
    val outerWidth: Float,
    val outerHeight: Float,
)

/**
 * Proportions shared by the live viewfinder and the full-resolution still.
 * Plate and mount heights are fractions of the photo width, the way a print margin is.
 */
object WatermarkMetrics {
    const val PLATE_THOUSANDTHS = 162
    const val PLATE_WITH_PLACE_THOUSANDTHS = 210
    const val MOUNT_SIDE_THOUSANDTHS = 46
    const val MOUNT_TOP_THOUSANDTHS = 46
    const val MOUNT_BOTTOM_THOUSANDTHS = 200

    fun scaledPx(photoWidth: Int, thousandths: Int): Int {
        return ((photoWidth.toLong() * thousandths + 500L) / 1000L).toInt().coerceAtLeast(1)
    }

    fun plateHeightPx(photoWidth: Int, hasPlace: Boolean): Int {
        val thousandths = if (hasPlace) PLATE_WITH_PLACE_THOUSANDTHS else PLATE_THOUSANDTHS
        return scaledPx(photoWidth, thousandths)
    }

    fun outputSize(photoWidth: Int, photoHeight: Int, style: WatermarkStyle, hasPlace: Boolean): PixelSize {
        return when (style) {
            WatermarkStyle.CAPTION -> PixelSize(photoWidth, photoHeight)
            WatermarkStyle.PLATE_LIGHT, WatermarkStyle.PLATE_DARK -> {
                PixelSize(photoWidth, photoHeight + plateHeightPx(photoWidth, hasPlace))
            }
            WatermarkStyle.MOUNT -> {
                val side = scaledPx(photoWidth, MOUNT_SIDE_THOUSANDTHS)
                val top = scaledPx(photoWidth, MOUNT_TOP_THOUSANDTHS)
                val bottom = scaledPx(photoWidth, MOUNT_BOTTOM_THOUSANDTHS)
                PixelSize(photoWidth + side * 2, photoHeight + top + bottom)
            }
        }
    }

    fun fitFrame(
        availWidth: Float,
        availHeight: Float,
        photoAspect: Float,
        style: WatermarkStyle,
        hasPlace: Boolean,
    ): FrameFit {
        require(photoAspect > 0f)
        require(availWidth > 0f && availHeight > 0f)
        val sideRatio = if (style == WatermarkStyle.MOUNT) MOUNT_SIDE_THOUSANDTHS / 1000f else 0f
        val topRatio = if (style == WatermarkStyle.MOUNT) MOUNT_TOP_THOUSANDTHS / 1000f else 0f
        val bottomRatio = when (style) {
            WatermarkStyle.MOUNT -> MOUNT_BOTTOM_THOUSANDTHS / 1000f
            WatermarkStyle.PLATE_LIGHT, WatermarkStyle.PLATE_DARK -> {
                (if (hasPlace) PLATE_WITH_PLACE_THOUSANDTHS else PLATE_THOUSANDTHS) / 1000f
            }
            WatermarkStyle.CAPTION -> 0f
        }
        val widthFactor = 1f + 2f * sideRatio
        val heightFactor = (1f / photoAspect) + topRatio + bottomRatio
        val photoW = min(availWidth / widthFactor, availHeight / heightFactor)
        val photoH = photoW / photoAspect
        val side = photoW * sideRatio
        val top = photoW * topRatio
        val bottom = photoW * bottomRatio
        return FrameFit(
            photoWidth = photoW,
            photoHeight = photoH,
            side = side,
            top = top,
            bottom = bottom,
            outerWidth = photoW + side * 2f,
            outerHeight = photoH + top + bottom,
        )
    }
}
