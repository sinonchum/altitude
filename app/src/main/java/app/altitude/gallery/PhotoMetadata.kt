package app.altitude.gallery

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import app.altitude.domain.ExifOrientation
import app.altitude.domain.PhotoCapture
import app.altitude.domain.cleanExifText
import app.altitude.domain.rationalToDouble
import app.altitude.domain.resolveCapturedClock
import app.altitude.domain.signedGpsAltitudeMeters
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * On Android 10 and later the photo picker strips GPS unless the app holds
 * [android.Manifest.permission.ACCESS_MEDIA_LOCATION] and opens the original.
 * [MediaStore.setRequireOriginal] sets `requireOriginal=1`, which MediaProvider
 * honors for both MediaStore and photo-picker URIs when that permission is granted.
 * Below Android 10 the URI is opened as returned.
 */
fun originalPhotoUri(uri: Uri, sdkInt: Int): Uri {
    if (sdkInt < Build.VERSION_CODES.Q) return uri
    if (uri.getQueryParameter(REQUIRE_ORIGINAL) != null) return uri
    return MediaStore.setRequireOriginal(uri)
}

fun openPhotoStream(resolver: ContentResolver, uri: Uri, sdkInt: Int): InputStream? {
    val original = originalPhotoUri(uri, sdkInt)
    return openQuietly(resolver, original) ?: if (original == uri) null else openQuietly(resolver, uri)
}

fun readPhotoCapture(exif: ExifInterface): PhotoCapture {
    val latLong = exif.latLong
    val latitude = latLong?.getOrNull(0)?.takeIf { it.isFinite() }
    val longitude = latLong?.getOrNull(1)?.takeIf { it.isFinite() }
    val pair = if (latitude != null && longitude != null) latitude to longitude else null
    val altitudeText = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE)
    val magnitude = if (altitudeText.isNullOrBlank()) {
        null
    } else {
        rationalToDouble(altitudeText)
            ?: exif.getAttributeDouble(ExifInterface.TAG_GPS_ALTITUDE, Double.NaN).takeIf { !it.isNaN() }
    }
    return PhotoCapture(
        latitude = pair?.first,
        longitude = pair?.second,
        exifAltitudeMeters = signedGpsAltitudeMeters(magnitude, parseAltitudeRef(exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF))),
        clock = resolveCapturedClock(
            dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            dateTimeDigitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED),
            dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
            offsetTimeOriginal = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
            offsetTimeDigitized = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED),
            offsetTime = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME),
            gpsDateStamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP),
            gpsTimeStamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP),
        ),
        orientation = ExifOrientation.fromTag(
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
        ),
    )
}

fun parseAltitudeRef(raw: String?): Int? {
    val text = cleanExifText(raw) ?: return null
    return rationalToDouble(text)?.toInt() ?: text.toIntOrNull()
}

private fun openQuietly(resolver: ContentResolver, uri: Uri): InputStream? {
    return try {
        resolver.openInputStream(uri)
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: FileNotFoundException) {
        null
    }
}

private const val REQUIRE_ORIGINAL = "requireOriginal"
