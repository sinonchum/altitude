package app.altitude.gallery

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import app.altitude.domain.ExifOrientation
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class PhotoExifDeviceTest {
    @Test
    fun fileRoundTripReadsRefOffsetAndOrientation() {
        val file = jpeg()
        val written = ExifInterface(file.absolutePath)
        written.setLatLong(29.652, 91.117)
        written.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "125/1")
        written.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "1")
        written.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:06:15 14:30:00")
        written.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+08:00")
        written.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        written.saveAttributes()

        val read = readPhotoCapture(ExifInterface(file.absolutePath))
        assertEquals(-125.0, read.exifAltitudeMeters!!, 0.01)
        assertEquals(29.652, read.latitude!!, 0.0001)
        assertEquals(91.117, read.longitude!!, 0.0001)
        assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30), read.clock!!.local)
        assertEquals(ZoneOffset.ofHours(8), read.clock.offset)
        assertEquals(ExifOrientation.ROTATE_90, read.orientation)
    }

    @Test
    fun missingTagsStayMissing() {
        val read = readPhotoCapture(ExifInterface(jpeg().absolutePath))
        assertNull(read.latitude)
        assertNull(read.longitude)
        assertNull(read.exifAltitudeMeters)
        assertNull(read.clock)
        assertEquals(ExifOrientation.NORMAL, read.orientation)
    }

    @Test
    fun setAltitudeBelowSeaLevelRoundTrips() {
        val file = jpeg()
        val written = ExifInterface(file.absolutePath)
        written.setAltitude(-42.5)
        written.saveAttributes()
        val read = readPhotoCapture(ExifInterface(file.absolutePath))
        assertEquals(-42.5, read.exifAltitudeMeters!!, 0.05)
    }

    @Test
    fun rotate90SwapsTheBitmapEdges() {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        val turned = PhotoFrames.applyOrientation(source, ExifOrientation.ROTATE_90)
        assertEquals(2, turned.width)
        assertEquals(4, turned.height)
        val flipped = PhotoFrames.applyOrientation(
            Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888),
            ExifOrientation.NORMAL,
        )
        assertEquals(3, flipped.width)
    }

    @Test
    fun originalUriRequestsTheUnredactedFileOnAndroid10() {
        val uri = Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/5")
        val opened = originalPhotoUri(uri, 29)
        assertEquals("1", opened.getQueryParameter("requireOriginal"))
        assertEquals(MediaStore.setRequireOriginal(uri), opened)
        assertEquals(opened, originalPhotoUri(opened, 33))
        assertEquals(uri, originalPhotoUri(uri, 28))
    }

    private fun jpeg(): File {
        val file = File.createTempFile("exif", ".jpg")
        val image = java.awt.image.BufferedImage(16, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image, "jpg", file)
        return file
    }
}
