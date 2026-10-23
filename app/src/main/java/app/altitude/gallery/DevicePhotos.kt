package app.altitude.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size

private const val PHOTO_QUERY_LIMIT = 240

fun queryRecentPhotos(resolver: ContentResolver, limit: Int = PHOTO_QUERY_LIMIT): List<Uri> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val args = Bundle().apply {
        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
    }
    val cursor = runCatching { resolver.query(collection, projection, args, null) }.getOrNull()
        ?: resolver.query(collection, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")
        ?: return emptyList()
    cursor.use { rows ->
        val idColumn = rows.getColumnIndex(MediaStore.Images.Media._ID)
        if (idColumn < 0) return emptyList()
        val photos = ArrayList<Uri>(rows.count.coerceAtMost(limit))
        while (rows.moveToNext() && photos.size < limit) {
            val id = rows.getLong(idColumn)
            photos += ContentUris.withAppendedId(collection, id)
        }
        return photos
    }
}

fun loadImportThumb(resolver: ContentResolver, uri: Uri, edge: Int): Bitmap? {
    val decoded = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(uri, Size(edge, edge), null)
        } else {
            resolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(stream, null, options)
            }
        }
    } catch (_: Exception) {
        null
    } ?: return null
    if (decoded.config != Bitmap.Config.HARDWARE) return decoded
    val copy = decoded.copy(Bitmap.Config.ARGB_8888, false) ?: return decoded
    decoded.recycle()
    return copy
}
