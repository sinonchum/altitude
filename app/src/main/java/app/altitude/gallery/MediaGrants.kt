package app.altitude.gallery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.altitude.domain.MediaGrant

fun currentMediaGrants(context: Context): Set<MediaGrant> {
    fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    return buildSet {
        if (granted(Manifest.permission.READ_MEDIA_IMAGES)) add(MediaGrant.IMAGES)
        if (granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) add(MediaGrant.VISUAL_USER_SELECTED)
        if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) add(MediaGrant.EXTERNAL_STORAGE)
        if (granted(Manifest.permission.ACCESS_MEDIA_LOCATION)) add(MediaGrant.MEDIA_LOCATION)
    }
}

fun permissionName(grant: MediaGrant): String = when (grant) {
    MediaGrant.IMAGES -> Manifest.permission.READ_MEDIA_IMAGES
    MediaGrant.VISUAL_USER_SELECTED -> Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    MediaGrant.EXTERNAL_STORAGE -> Manifest.permission.READ_EXTERNAL_STORAGE
    MediaGrant.MEDIA_LOCATION -> Manifest.permission.ACCESS_MEDIA_LOCATION
}
