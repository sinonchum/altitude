package app.altitude.ui.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import app.altitude.R
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.CapturedClock
import app.altitude.domain.WatermarkContent
import app.altitude.domain.WatermarkStyle
import app.altitude.domain.formatExifDateTime
import app.altitude.domain.formatExifOffset
import app.altitude.domain.formatExifWallClock
import app.altitude.domain.importDisplayName
import java.io.ByteArrayInputStream
import java.io.File
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class CaptureException(@StringRes val messageRes: Int) : IllegalStateException()

class PhotoPipeline(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    suspend fun capture(
        imageCapture: ImageCapture,
        content: WatermarkContent,
        style: WatermarkStyle,
        anchor: CaptionAnchor,
        latitude: Double?,
        longitude: Double?,
        altitudeMeters: Double?,
        epochMs: Long,
    ): String = withContext(Dispatchers.IO) {
        val (bytes, rotation) = takeJpeg(imageCapture)
        // Rotation brings the JPEG upright. The bitmap is not mirrored, so a selfie
        // matches what other people see and the watermark text stays readable.
        val upright = decodeUpright(bytes, rotation)
        val stamped = WatermarkRenderer.render(upright, content, style, anchor)
        if (stamped !== upright) upright.recycle()
        val temp = File.createTempFile("altitude_", ".jpg", context.cacheDir)
        try {
            temp.outputStream().use { stream ->
                if (!stamped.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw CaptureException(R.string.error_photo_compress)
                }
            }
            stamped.recycle()
            writeExif(
                temp,
                SavedExif(
                    latitude = latitude,
                    longitude = longitude,
                    altitudeMeters = altitudeMeters,
                    clock = null,
                    fallbackEpochMs = epochMs,
                ),
            )
            saveToGallery(temp, importDisplayName(clock = null, fallbackEpochMs = epochMs, index = 0))
        } finally {
            temp.delete()
        }
    }

    private suspend fun takeJpeg(imageCapture: ImageCapture): Pair<ByteArray, Int> {
        return suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val rotation = image.imageInfo.rotationDegrees
                            image.close()
                            if (continuation.isActive) continuation.resume(bytes to rotation)
                        } catch (error: Throwable) {
                            runCatching { image.close() }
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }

    private fun decodeUpright(bytes: ByteArray, rotationDegrees: Int): Bitmap {
        val rotation = if (rotationDegrees != 0) rotationDegrees else exifRotation(bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 12_000) sample *= 2
        val decoded = decodeSampled(bytes, sample)
            ?: throw CaptureException(R.string.error_photo_decode)
        return rotate(decoded, rotation)
    }

    private fun decodeSampled(bytes: ByteArray, sample: Int): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } catch (_: OutOfMemoryError) {
            if (sample >= 4) null else decodeSampled(bytes, sample * 2)
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) source.recycle()
        return rotated
    }

    private fun exifRotation(bytes: ByteArray): Int {
        return runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }

    suspend fun saveFramedCopy(
        upright: Bitmap,
        content: WatermarkContent,
        style: WatermarkStyle,
        anchor: CaptionAnchor,
        saved: SavedExif,
        nameIndex: Int,
    ): String = withContext(Dispatchers.IO) {
        val stamped = WatermarkRenderer.render(upright, content, style, anchor)
        val temp = File.createTempFile("altitude_", ".jpg", context.cacheDir)
        try {
            val compressed = temp.outputStream().use { stream ->
                stamped.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            if (stamped !== upright) stamped.recycle()
            if (!compressed) throw CaptureException(R.string.error_photo_compress)
            writeExif(temp, saved)
            saveToGallery(temp, importDisplayName(saved.clock, saved.fallbackEpochMs, nameIndex))
        } finally {
            temp.delete()
        }
    }

    private fun writeExif(file: File, saved: SavedExif) {
        val exif = ExifInterface(file.absolutePath)
        if (saved.latitude != null && saved.longitude != null) {
            runCatching { exif.setLatLong(saved.latitude, saved.longitude) }
        }
        if (saved.altitudeMeters != null) {
            runCatching { exif.setAltitude(saved.altitudeMeters) }
        }
        val clock = saved.clock
        val stamp = if (clock != null) formatExifWallClock(clock.local) else formatExifDateTime(saved.fallbackEpochMs)
        exif.setAttribute(ExifInterface.TAG_DATETIME, stamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
        val offset = clock?.offset
        if (offset != null) {
            val text = formatExifOffset(offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, text)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, text)
            clock.instant()?.withOffsetSameInstant(ZoneOffset.UTC)?.let { utc ->
                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, utc.format(GPS_DATE))
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, utc.format(GPS_TIME))
            }
        }
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Altitude")
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
    }

    private fun saveToGallery(file: File, displayName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Altitude")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Altitude",
                )
                if (!dir.exists()) dir.mkdirs()
                put(MediaStore.Images.Media.DATA, File(dir, displayName).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw CaptureException(R.string.error_gallery_rejected)
        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: throw CaptureException(R.string.error_gallery_write)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        return uri.toString()
    }

}

data class SavedExif(
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val clock: CapturedClock?,
    val fallbackEpochMs: Long,
)

private val GPS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.US)
private val GPS_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
