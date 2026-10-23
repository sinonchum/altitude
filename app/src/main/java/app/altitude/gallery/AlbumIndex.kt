package app.altitude.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

class AlbumIndex(private val context: Context) {
    fun listUris(): List<String> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val (selection, args) = AlbumQuery.selection(Build.VERSION.SDK_INT)
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val uris = mutableListOf<String>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                uris += ContentUris.withAppendedId(collection, id).toString()
            }
        }
        return uris
    }

    fun delete(uri: Uri): Boolean {
        return context.contentResolver.delete(uri, null, null) > 0
    }
}
