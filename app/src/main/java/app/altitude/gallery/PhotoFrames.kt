package app.altitude.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import app.altitude.domain.ExifOrientation
import java.io.File
import kotlin.math.max

object PhotoFrames {
    fun decodeUpright(file: File, orientation: ExifOrientation, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        val decoded = try {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        return applyOrientation(decoded, orientation)
    }

    fun applyOrientation(source: Bitmap, orientation: ExifOrientation): Bitmap {
        val transform = orientation.transform()
        if (transform.clockwiseDegrees == 0 && !transform.mirrorHorizontal) return source
        val matrix = Matrix()
        if (transform.clockwiseDegrees != 0) matrix.postRotate(transform.clockwiseDegrees.toFloat())
        if (transform.mirrorHorizontal) matrix.postScale(-1f, 1f)
        val transformed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (transformed != source) source.recycle()
        return transformed
    }
}
