package app.altitude.gallery

import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlin.math.max

fun decodeAlbumBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
    if (maxEdge <= 512 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val thumbnail = runCatching {
            context.contentResolver.loadThumbnail(uri, Size(maxEdge, maxEdge), null)
        }.getOrNull()
        if (thumbnail != null) return thumbnail
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    var sample = 1
    val largest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    while (largest / sample > maxEdge) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }
}

fun shareAlbumPhoto(context: Context, uri: Uri, title: String) {
    val clip = ClipData.newRawUri(title, uri)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = clip
    }
    val chooser = Intent.createChooser(send, title).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = clip
    }
    context.startActivity(chooser)
}

fun openAlbumPhoto(context: Context, uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(view)
}

fun deleteConsentSender(context: Context, uri: Uri, error: SecurityException): IntentSender? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
        }.getOrNull()
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val recoverable = error as? RecoverableSecurityException ?: return null
    return recoverable.userAction.actionIntent.intentSender
}
