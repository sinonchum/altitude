package app.altitude.domain

/**
 * Sizes for the camera chrome. Landscape keeps the live preview on the long
 * axis and parks controls in side rails, so the portrait column cannot eat
 * the short side of the screen.
 *
 * The shutter rail sits on the phone's physical bottom edge: [Surface.ROTATION_90]
 * puts that edge on the right, [Surface.ROTATION_270] puts it on the left.
 */
object CameraStageMetrics {
    const val SHUTTER_RAIL = 96f
    const val ZOOM_RAIL = 64f
    const val UTILITY_RAIL = 120f
    const val EDGE_INSET = 4f
    const val MIN_LANDSCAPE_PREVIEW_FRACTION = 0.70f
    const val ROTATION_270 = 3

    fun isLandscape(width: Float, height: Float): Boolean = width > height && height > 0f

    fun shutterOnEnd(surfaceRotation: Int): Boolean = surfaceRotation != ROTATION_270

    fun landscapePhotoHeight(
        screenWidth: Float,
        screenHeight: Float,
        zoomVisible: Boolean,
        photoAspect: Float,
        style: WatermarkStyle,
        hasPlace: Boolean,
    ): Float {
        val zoom = if (zoomVisible) ZOOM_RAIL else 0f
        val availW = (screenWidth - SHUTTER_RAIL - UTILITY_RAIL - zoom - EDGE_INSET * 2f).coerceAtLeast(1f)
        val availH = (screenHeight - EDGE_INSET * 2f).coerceAtLeast(1f)
        return WatermarkMetrics.fitFrame(
            availWidth = availW,
            availHeight = availH,
            photoAspect = photoAspect,
            style = style,
            hasPlace = hasPlace,
        ).photoHeight
    }

    fun landscapePreviewFraction(
        screenWidth: Float,
        screenHeight: Float,
        zoomVisible: Boolean,
        photoAspect: Float,
        style: WatermarkStyle,
        hasPlace: Boolean,
    ): Float {
        if (screenHeight <= 0f) return 0f
        return landscapePhotoHeight(
            screenWidth,
            screenHeight,
            zoomVisible,
            photoAspect,
            style,
            hasPlace,
        ) / screenHeight
    }
}
