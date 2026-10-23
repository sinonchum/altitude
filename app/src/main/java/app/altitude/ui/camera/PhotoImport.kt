package app.altitude.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import app.altitude.R
import app.altitude.domain.PhotoCapture
import app.altitude.domain.SelectedAltitude
import app.altitude.domain.selectPhotoAltitude
import app.altitude.gallery.PhotoFrames
import app.altitude.gallery.openPhotoStream
import app.altitude.gallery.readPhotoCapture
import app.altitude.location.fetchElevations
import app.altitude.location.resolvePlaceName
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val IMPORT_PREVIEW_EDGE = 1_600
internal const val IMPORT_SAVE_EDGE = 12_000

internal data class ImportedPage(
    val uri: Uri,
    val file: File? = null,
    val capture: PhotoCapture? = null,
    val preview: Bitmap? = null,
    val altitude: SelectedAltitude? = null,
    val place: String? = null,
    val errorRes: Int? = null,
) {
    val ready: Boolean
        get() = file != null && capture != null && preview != null && altitude != null && errorRes == null
}

internal class ImportFiles(private val context: Context) {
    private val created = mutableListOf<File>()

    suspend fun copy(uri: Uri): File = withContext(Dispatchers.IO) {
        val file = File.createTempFile("import_", ".jpg", context.cacheDir)
        created += file
        val input = openPhotoStream(context.contentResolver, uri, Build.VERSION.SDK_INT)
            ?: throw CaptureException(R.string.error_photo_decode)
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        if (file.length() == 0L) throw CaptureException(R.string.error_photo_decode)
        file
    }

    fun deleteAll() {
        created.forEach { it.delete() }
        created.clear()
    }
}

internal fun mediaLocationGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Copies each photo, reads its EXIF, and fills altitude and place from that photo.
 * Altitude lookup runs as one request. Place names use the existing Nominatim pipeline.
 * The original URI is only read. Nothing is written back to it.
 */
internal suspend fun loadImportedPages(
    context: Context,
    files: ImportFiles,
    uris: List<Uri>,
    showPlace: Boolean,
    locale: Locale,
    onPage: (List<ImportedPage>) -> Unit,
): List<ImportedPage> {
    val loaded = mutableListOf<ImportedPage>()
    for (uri in uris) {
        loaded += loadOne(files, uri)
        onPage(loaded.toList() + uris.drop(loaded.size).map { ImportedPage(it) })
    }
    val pending = loaded.mapIndexedNotNull { index, page ->
        val capture = page.capture ?: return@mapIndexedNotNull null
        if (page.altitude == null && capture.hasCoordinates) {
            index to (capture.latitude!! to capture.longitude!!)
        } else {
            null
        }
    }
    if (pending.isNotEmpty()) {
        val elevations = runCatching { fetchElevations(pending.map { it.second }) }.getOrElse { emptyList() }
        pending.forEachIndexed { slot, (index, _) ->
            val page = loaded[index]
            val capture = page.capture ?: return@forEachIndexed
            loaded[index] = page.copy(
                altitude = selectPhotoAltitude(
                    exifAltitudeMeters = capture.exifAltitudeMeters,
                    elevationLookupMeters = elevations.getOrNull(slot),
                    hasCoordinates = true,
                ),
            )
        }
        onPage(loaded.toList())
    }
    if (showPlace) {
        for (index in loaded.indices) {
            val capture = loaded[index].capture ?: continue
            if (!capture.hasCoordinates) continue
            val name = runCatching {
                resolvePlaceName(context, capture.latitude!!, capture.longitude!!, locale)
            }.getOrNull()
            loaded[index] = loaded[index].copy(place = name)
            onPage(loaded.toList())
        }
    }
    return loaded
}

private suspend fun loadOne(files: ImportFiles, uri: Uri): ImportedPage {
    return try {
        val file = files.copy(uri)
        val capture = withContext(Dispatchers.IO) { readPhotoCapture(ExifInterface(file.absolutePath)) }
        val preview = withContext(Dispatchers.IO) {
            PhotoFrames.decodeUpright(file, capture.orientation, IMPORT_PREVIEW_EDGE)
        } ?: throw CaptureException(R.string.error_photo_decode)
        val altitude = if (capture.exifAltitudeMeters != null || !capture.hasCoordinates) {
            selectPhotoAltitude(capture.exifAltitudeMeters, elevationLookupMeters = null, capture.hasCoordinates)
        } else {
            null
        }
        ImportedPage(uri = uri, file = file, capture = capture, preview = preview, altitude = altitude)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: CaptureException) {
        ImportedPage(uri = uri, errorRes = error.messageRes)
    } catch (_: Exception) {
        ImportedPage(uri = uri, errorRes = R.string.error_photo_decode)
    }
}
