package app.altitude.gallery

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import app.altitude.domain.ExifOrientation
import app.altitude.domain.LocationExposure
import app.altitude.domain.MediaGrant
import app.altitude.domain.PhotoCapture
import app.altitude.domain.PhotoUriKind
import app.altitude.domain.canReadOriginalLocation
import app.altitude.domain.cleanExifText
import app.altitude.domain.libraryAccess
import app.altitude.domain.LibraryAccess
import app.altitude.domain.locationExposure
import app.altitude.domain.rationalToDouble
import app.altitude.domain.resolveCapturedClock
import app.altitude.domain.resolvePhotoRead
import app.altitude.domain.signedGpsAltitudeMeters
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Opens the photo bytes that still carry GPS when the platform will allow it.
 * Picker URIs are rewritten to `content://media/<volume>/images/media/<id>` first.
 * `requireOriginal=1` is applied only to that MediaStore URI.
 */
data class OpenedImport(
    val stream: InputStream,
    val kind: PhotoUriKind,
    val upgraded: Boolean,
    val openedOriginal: Boolean,
) {
    fun exposure(hasCoordinates: Boolean): LocationExposure {
        return locationExposure(kind, upgraded, openedOriginal, hasCoordinates)
    }
}

fun openImportPhoto(
    resolver: ContentResolver,
    uri: Uri,
    sdk: Int,
    grants: Set<MediaGrant>,
): OpenedImport? {
    val canRead = libraryAccess(sdk, grants) != LibraryAccess.NONE
    val resolved = resolvePhotoRead(uri.toString(), canRead)
    val target = Uri.parse(resolved.openUri)
    val wantOriginal = sdk >= Build.VERSION_CODES.Q &&
        canReadOriginalLocation(sdk, grants) &&
        resolved.mediaStore
    if (wantOriginal) {
        val original = mediaStoreOriginal(target)
        val stream = openQuietly(resolver, original)
        if (stream != null) {
            return OpenedImport(stream, resolved.kind, resolved.upgraded, openedOriginal = true)
        }
    }
    val fallback = openQuietly(resolver, uri)
        ?: if (resolved.upgraded) openQuietly(resolver, target) else null
    if (fallback == null) return null
    return OpenedImport(
        stream = fallback,
        kind = resolved.kind,
        upgraded = resolved.upgraded,
        openedOriginal = sdk < Build.VERSION_CODES.Q,
    )
}

private fun mediaStoreOriginal(uri: Uri): Uri {
    if (uri.getQueryParameter(REQUIRE_ORIGINAL) != null) return uri
    return MediaStore.setRequireOriginal(uri)
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
