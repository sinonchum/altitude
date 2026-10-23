package app.altitude.gallery

import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import app.altitude.BuildConfig
import app.altitude.domain.MediaGrant
import app.altitude.domain.PhotoUriKind

private const val TAG = "AltitudeImport"

fun logImportRead(
    uri: Uri,
    kind: PhotoUriKind,
    upgraded: Boolean,
    openedOriginal: Boolean,
    grants: Set<MediaGrant>,
    exif: ExifInterface?,
) {
    if (!BuildConfig.DEBUG) return
    val tags = exif?.let { presentTags(it) }.orEmpty()
    Log.d(
        TAG,
        "authority=${uri.authority.orEmpty()} kind=${kind.name} upgraded=$upgraded " +
            "original=$openedOriginal grants=${grants.joinToString(",") { it.name }} " +
            "tags=${tags.joinToString(",")}",
    )
}

private fun presentTags(exif: ExifInterface): List<String> {
    return WATCHED.filter { name -> !exif.getAttribute(name).isNullOrBlank() }
}

private val WATCHED = listOf(
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_ORIENTATION,
)
