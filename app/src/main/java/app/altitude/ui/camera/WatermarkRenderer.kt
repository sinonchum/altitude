package app.altitude.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.WatermarkContent
import app.altitude.domain.WatermarkMetrics
import app.altitude.domain.WatermarkStyle
import app.altitude.ui.theme.AltitudeTypefaces
import kotlin.math.max

private const val RED = 0xFFE10600.toInt()
private const val LIGHT_BG = 0xFFFFFFFF.toInt()
private const val DARK_BG = 0xFF0A0A0A.toInt()

object WatermarkRenderer {
    fun render(
        photo: Bitmap,
        content: WatermarkContent,
        style: WatermarkStyle,
        anchor: CaptionAnchor,
    ): Bitmap {
        val size = WatermarkMetrics.outputSize(photo.width, photo.height, style, content.place != null)
        val out = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        when (style) {
            WatermarkStyle.CAPTION -> {
                canvas.drawBitmap(photo, 0f, 0f, null)
                drawCaption(canvas, size.width.toFloat(), size.height.toFloat(), content, anchor)
            }
            WatermarkStyle.PLATE_LIGHT, WatermarkStyle.PLATE_DARK -> {
                canvas.drawBitmap(photo, 0f, 0f, null)
                val plate = RectF(0f, photo.height.toFloat(), photo.width.toFloat(), size.height.toFloat())
                drawPlate(canvas, plate, content, dark = style == WatermarkStyle.PLATE_DARK)
            }
            WatermarkStyle.MOUNT -> {
                canvas.drawColor(LIGHT_BG)
                val side = WatermarkMetrics.scaledPx(photo.width, WatermarkMetrics.MOUNT_SIDE_THOUSANDTHS).toFloat()
                val top = WatermarkMetrics.scaledPx(photo.width, WatermarkMetrics.MOUNT_TOP_THOUSANDTHS).toFloat()
                canvas.drawBitmap(photo, side, top, null)
                val bottom = RectF(side, top + photo.height, size.width - side, size.height.toFloat())
                drawBottomBand(canvas, bottom, photo.width.toFloat(), content, dark = false)
            }
        }
        return out
    }

    fun drawPlate(canvas: Canvas, rect: RectF, content: WatermarkContent, dark: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = if (dark) DARK_BG else LIGHT_BG
        canvas.drawRect(rect, paint)
        drawBottomBand(canvas, rect, rect.width(), content, dark)
    }

    fun drawCaption(
        canvas: Canvas,
        width: Float,
        height: Float,
        content: WatermarkContent,
        anchor: CaptionAnchor,
    ) {
        val band = height * 0.28f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = if (anchor == CaptionAnchor.TOP) {
            LinearGradient(0f, 0f, 0f, band, 0xE6101010.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, height - band, 0f, height, 0x00000000, 0xE6101010.toInt(), Shader.TileMode.CLAMP)
        }
        paint.shader = shader
        val top = if (anchor == CaptionAnchor.TOP) 0f else height - band
        canvas.drawRect(0f, top, width, top + band, paint)
        val textTop = if (anchor == CaptionAnchor.TOP) height * 0.035f else height - band * 0.82f
        val textBottom = if (anchor == CaptionAnchor.TOP) band * 0.92f else height - height * 0.03f
        val rect = RectF(width * 0.05f, textTop, width * 0.95f, textBottom)
        drawSignature(canvas, rect, width, content, dark = true)
    }

    private fun drawBottomBand(
        canvas: Canvas,
        rect: RectF,
        metricWidth: Float,
        content: WatermarkContent,
        dark: Boolean,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = if (dark) 0x24FFFFFF else 0x16000000
        val hairline = max(1f, metricWidth * 0.0013f)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + hairline, paint)
        val inset = metricWidth * 0.048f
        val text = RectF(rect.left + inset, rect.top, rect.right - inset, rect.bottom)
        drawSignature(canvas, text, metricWidth, content, dark)
    }

    fun drawSignature(
        canvas: Canvas,
        rect: RectF,
        metricWidth: Float,
        content: WatermarkContent,
        dark: Boolean,
    ) {
        if (rect.width() <= 2f || rect.height() <= 2f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            textAlign = Paint.Align.LEFT
        }
        val primary = if (dark) 0xFFF6F4EF.toInt() else 0xFF121212.toInt()
        val secondary = if (dark) 0xFFC2C2BC.toInt() else 0xFF5C5C58.toInt()
        val wordmarkSize = metricWidth * 0.0275f
        val altitudeSize = metricWidth * 0.056f
        val metaSize = metricWidth * 0.0235f
        val gap = metricWidth * 0.012f
        val leftWidth = rect.width() * 0.62f
        val rightWidth = rect.width() * 0.36f

        val wordmark = "ALTITUDE"
        paint.typeface = AltitudeTypefaces.mediumOrDefault()
        paint.letterSpacing = 0.2f
        paint.textSize = wordmarkSize
        val wordmarkWidth = paint.measureText(wordmark)
        val wordmarkHeight = paint.lineHeight()

        paint.typeface = AltitudeTypefaces.semiboldOrDefault()
        paint.letterSpacing = -0.03f
        var altSize = altitudeSize
        paint.textSize = altSize
        while (paint.measureText(content.altitude) > rightWidth && altSize > metricWidth * 0.032f) {
            altSize *= 0.94f
            paint.textSize = altSize
        }
        val altitudeHeight = paint.lineHeight()

        val coordinate = content.coordinates ?: "等待定位"
        val coordinateCjk = content.coordinates == null || coordinate.hasCjk()
        paint.typeface = if (coordinateCjk) AltitudeTypefaces.cjk else AltitudeTypefaces.regularOrDefault()
        paint.letterSpacing = if (coordinateCjk) 0f else 0.015f
        var coordinateSize = metaSize
        paint.textSize = coordinateSize
        while (paint.measureText(coordinate) > leftWidth && coordinateSize > metricWidth * 0.015f) {
            coordinateSize *= 0.92f
            paint.textSize = coordinateSize
        }
        val coordinateHeight = paint.lineHeight()

        val place = content.place
        val placeHeight = if (place == null) {
            0f
        } else {
            paint.typeface = AltitudeTypefaces.cjk
            paint.letterSpacing = 0f
            paint.textSize = metaSize
            while (paint.measureText(place) > leftWidth && paint.textSize > metricWidth * 0.015f) {
                paint.textSize *= 0.92f
            }
            paint.lineHeight()
        }
        val placeSize = if (place == null) 0f else paint.textSize

        val time = content.time
        var timeSize = metaSize * 0.96f
        val timeHeight = if (time == null) {
            0f
        } else {
            paint.typeface = AltitudeTypefaces.regularOrDefault()
            paint.letterSpacing = 0.06f
            paint.textSize = timeSize
            while (paint.measureText(time) > rightWidth && paint.textSize > metricWidth * 0.014f) {
                paint.textSize *= 0.92f
            }
            timeSize = paint.textSize
            paint.lineHeight()
        }

        val leftHeight = wordmarkHeight + gap + coordinateHeight + if (place != null) gap + placeHeight else 0f
        val rightHeight = altitudeHeight + if (time != null) gap + timeHeight else 0f
        val block = max(leftHeight, rightHeight)
        val top = rect.top + (rect.height() - block) / 2f
        val leftTop = top + (block - leftHeight) / 2f
        val rightTop = top + (block - rightHeight) / 2f

        paint.color = primary
        paint.typeface = AltitudeTypefaces.mediumOrDefault()
        paint.letterSpacing = 0.2f
        paint.textSize = wordmarkSize
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(wordmark, rect.left, leftTop + paint.baselineOffset(), paint)

        paint.color = RED
        val dotRadius = wordmarkSize * 0.145f
        canvas.drawCircle(
            rect.left + wordmarkWidth + wordmarkSize * 0.52f,
            leftTop + wordmarkHeight / 2f,
            dotRadius,
            paint,
        )

        paint.color = secondary
        paint.typeface = if (coordinateCjk) AltitudeTypefaces.cjk else AltitudeTypefaces.regularOrDefault()
        paint.letterSpacing = if (coordinateCjk) 0f else 0.015f
        paint.textSize = coordinateSize
        val coordinateTop = leftTop + wordmarkHeight + gap
        canvas.drawText(coordinate, rect.left, coordinateTop + paint.baselineOffset(), paint)

        if (place != null) {
            paint.typeface = AltitudeTypefaces.cjk
            paint.letterSpacing = 0f
            paint.textSize = placeSize
            paint.color = secondary
            val placeTop = coordinateTop + coordinateHeight + gap
            canvas.drawText(place, rect.left, placeTop + paint.baselineOffset(), paint)
        }

        paint.color = primary
        paint.typeface = AltitudeTypefaces.semiboldOrDefault()
        paint.letterSpacing = -0.03f
        paint.textSize = altSize
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(content.altitude, rect.right, rightTop + paint.baselineOffset(), paint)

        if (time != null) {
            paint.color = secondary
            paint.typeface = AltitudeTypefaces.regularOrDefault()
            paint.letterSpacing = 0.06f
            paint.textSize = timeSize
            val timeTop = rightTop + altitudeHeight + gap
            canvas.drawText(time, rect.right, timeTop + paint.baselineOffset(), paint)
        }
    }
}

enum class SignatureMode { PLATE, CAPTION }

class SignatureView(context: android.content.Context) : View(context) {
    var content: WatermarkContent? = null
    var dark: Boolean = false
    var mode: SignatureMode = SignatureMode.PLATE
    var anchor: CaptionAnchor = CaptionAnchor.BOTTOM

    init {
        setWillNotDraw(false)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onDraw(canvas: Canvas) {
        val current = content ?: return
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        when (mode) {
            SignatureMode.PLATE -> WatermarkRenderer.drawPlate(canvas, bounds, current, dark)
            SignatureMode.CAPTION -> WatermarkRenderer.drawCaption(canvas, bounds.width(), bounds.height(), current, anchor)
        }
    }
}

private fun Paint.lineHeight(): Float = fontMetrics.descent - fontMetrics.ascent

private fun Paint.baselineOffset(): Float = -fontMetrics.ascent

private fun String.hasCjk(): Boolean = any { character -> character.code > 0x2E80 }
