package app.altitude.domain

/**
 * How a photo URI can be opened.
 * [PICKER] URIs (the system photo picker and its Play services backport) always
 * ship a copy with GPS removed. [MediaStore.setRequireOriginal] does not change
 * that: MediaProvider only honors `requireOriginal=1` on a real MediaStore item
 * such as `content://media/external/images/media/<id>`, and only when the app
 * holds both a media-read grant and ACCESS_MEDIA_LOCATION.
 */
enum class PhotoUriKind {
    MEDIA_STORE,
    MEDIA_DOCUMENT,
    PICKER,
    OTHER,
}

enum class MediaGrant {
    IMAGES,
    VISUAL_USER_SELECTED,
    EXTERNAL_STORAGE,
    MEDIA_LOCATION,
}

enum class LibraryAccess {
    FULL,
    PARTIAL,
    NONE,
}

enum class AccessAction {
    REQUEST,
    SETTINGS,
}

enum class LocationExposure {
    PRESENT,
    ABSENT,
    REDACTED,
}

data class ResolvedPhoto(
    val kind: PhotoUriKind,
    val openUri: String,
    val upgraded: Boolean,
    val mediaStore: Boolean,
)

private const val ANDROID_Q = 29
private const val ANDROID_TIRAMISU = 33
private const val ANDROID_UPSIDE_DOWN_CAKE = 34

fun grantsToRequest(sdk: Int): Set<MediaGrant> = when {
    sdk >= ANDROID_UPSIDE_DOWN_CAKE -> setOf(
        MediaGrant.IMAGES,
        MediaGrant.VISUAL_USER_SELECTED,
        MediaGrant.MEDIA_LOCATION,
    )
    sdk >= ANDROID_TIRAMISU -> setOf(MediaGrant.IMAGES, MediaGrant.MEDIA_LOCATION)
    sdk >= ANDROID_Q -> setOf(MediaGrant.EXTERNAL_STORAGE, MediaGrant.MEDIA_LOCATION)
    else -> setOf(MediaGrant.EXTERNAL_STORAGE)
}

fun libraryAccess(sdk: Int, granted: Set<MediaGrant>): LibraryAccess {
    if (sdk >= ANDROID_UPSIDE_DOWN_CAKE) {
        if (MediaGrant.IMAGES in granted) return LibraryAccess.FULL
        if (MediaGrant.VISUAL_USER_SELECTED in granted) return LibraryAccess.PARTIAL
        return LibraryAccess.NONE
    }
    if (sdk >= ANDROID_TIRAMISU) {
        return if (MediaGrant.IMAGES in granted) LibraryAccess.FULL else LibraryAccess.NONE
    }
    return if (MediaGrant.EXTERNAL_STORAGE in granted) LibraryAccess.FULL else LibraryAccess.NONE
}

/**
 * Unredacted EXIF on Android 10+ requires a media-read grant (full or the
 * Android 14 selected-photos grant) together with ACCESS_MEDIA_LOCATION.
 * The location permission on its own is ignored.
 */
fun canReadOriginalLocation(sdk: Int, granted: Set<MediaGrant>): Boolean {
    if (libraryAccess(sdk, granted) == LibraryAccess.NONE) return false
    if (sdk < ANDROID_Q) return true
    return MediaGrant.MEDIA_LOCATION in granted
}

fun accessAction(sdk: Int, granted: Set<MediaGrant>): AccessAction {
    val library = libraryAccess(sdk, granted)
    val location = canReadOriginalLocation(sdk, granted)
    return if (library == LibraryAccess.FULL && location) AccessAction.SETTINGS else AccessAction.REQUEST
}

fun classifyPhotoUri(uri: String): PhotoUriKind {
    val bare = stripQuery(uri)
    if (isPicker(bare)) return PhotoUriKind.PICKER
    if (documentImageId(bare) != null) return PhotoUriKind.MEDIA_DOCUMENT
    if (mediaStoreImageId(bare) != null) return PhotoUriKind.MEDIA_STORE
    return PhotoUriKind.OTHER
}

fun resolvePhotoRead(uri: String, canReadMediaStore: Boolean): ResolvedPhoto {
    val bare = stripQuery(uri)
    val kind = classifyPhotoUri(bare)
    val upgradedId = when (kind) {
        PhotoUriKind.MEDIA_STORE -> null
        PhotoUriKind.MEDIA_DOCUMENT -> if (canReadMediaStore) documentImageId(bare) else null
        PhotoUriKind.PICKER -> if (canReadMediaStore) pickerOrEmbeddedId(bare) else null
        PhotoUriKind.OTHER -> if (canReadMediaStore) embeddedImageId(bare) else null
    }
    val upgradedUri = upgradedId?.let { externalImageUri(it) }
    return ResolvedPhoto(
        kind = kind,
        openUri = upgradedUri ?: bare,
        upgraded = upgradedUri != null,
        mediaStore = kind == PhotoUriKind.MEDIA_STORE || upgradedUri != null,
    )
}

fun externalImageUri(id: Long): String = "content://media/external/images/media/$id"

/**
 * [openedOriginal] is true only when the bytes came from a MediaStore item
 * opened with `requireOriginal`, or from any file on Android 9 and below.
 * A picker or vendor URI that could not be upgraded still has no GPS even
 * when ACCESS_MEDIA_LOCATION is granted.
 */
fun locationExposure(
    kind: PhotoUriKind,
    upgraded: Boolean,
    openedOriginal: Boolean,
    hasCoordinates: Boolean,
): LocationExposure {
    if (hasCoordinates) return LocationExposure.PRESENT
    val sawFile = openedOriginal && (kind == PhotoUriKind.MEDIA_STORE || kind == PhotoUriKind.MEDIA_DOCUMENT || upgraded)
    return if (sawFile) LocationExposure.ABSENT else LocationExposure.REDACTED
}

private fun stripQuery(uri: String): String = uri.substringBefore('?').substringBefore('#')

private fun isPicker(bare: String): Boolean {
    val lower = bare.lowercase()
    return lower.contains("/picker/") ||
        lower.contains("/picker_get_content/") ||
        lower.contains("photopicker")
}

private fun mediaStoreImageId(bare: String): Long? {
    val match = MEDIA_ITEM.find(bare) ?: return null
    return match.groupValues[1].toLongOrNull()
}

private fun documentImageId(bare: String): Long? {
    if (!bare.startsWith(MEDIA_DOCUMENTS)) return null
    val document = looseDecode(bare.substringAfterLast('/'))
    if (!document.startsWith("image:")) return null
    return document.removePrefix("image:").substringBefore('/').toLongOrNull()
}

private fun pickerOrEmbeddedId(bare: String): Long? = embeddedImageId(bare) ?: trailingMediaId(bare)

private fun embeddedImageId(bare: String): Long? {
    val decoded = looseDecode(bare)
    val match = EMBEDDED_ITEM.find(decoded) ?: return null
    return match.groupValues[1].toLongOrNull()
}

private fun trailingMediaId(bare: String): Long? {
    val path = bare.substringAfter("://").substringAfter('/')
    val parts = path.split('/')
    val index = parts.indexOf("media")
    if (index < 0 || index + 1 >= parts.size) return null
    return parts[index + 1].toLongOrNull()
}

private fun looseDecode(text: String): String {
    return try {
        java.net.URLDecoder.decode(text, "UTF-8")
    } catch (_: Exception) {
        text
    }
}

private val MEDIA_ITEM = Regex("""^content://media/[^/]+/images/media/(\d+)$""")
private val EMBEDDED_ITEM = Regex("""images/media/(\d+)""")
private const val MEDIA_DOCUMENTS = "content://com.android.providers.media.documents/document/"
