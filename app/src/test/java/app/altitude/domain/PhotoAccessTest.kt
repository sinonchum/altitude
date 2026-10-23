package app.altitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAccessTest {
    private val picker = "content://media/picker/0/com.android.providers.media.photopicker/media/5"
    private val backport = "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/12"
    private val store = "content://media/external/images/media/5"
    private val primary = "content://media/external_primary/images/media/9"
    private val document = "content://com.android.providers.media.documents/document/image%3A55"
    private val photos = "content://com.google.android.apps.photos.contentprovider/0/1/" +
        "content%3A%2F%2Fmedia%2Fexternal%2Fimages%2Fmedia%2F321/ORIGINAL/NONE/image.jpg"
    private val vendor = "content://com.miui.gallery.open/raw"

    @Test
    fun pickerUrisAreNotMediaStoreItems() {
        assertEquals(PhotoUriKind.PICKER, classifyPhotoUri(picker))
        assertEquals(PhotoUriKind.PICKER, classifyPhotoUri(backport))
        assertEquals(PhotoUriKind.PICKER, classifyPhotoUri("$picker?requireOriginal=1"))
    }

    @Test
    fun readablePickerUriIsReopenedAsTheMediaStoreItem() {
        val resolved = resolvePhotoRead(picker, canReadMediaStore = true)
        assertEquals(PhotoUriKind.PICKER, resolved.kind)
        assertEquals(store, resolved.openUri)
        assertTrue(resolved.upgraded)
        assertTrue(resolved.mediaStore)
        val blocked = resolvePhotoRead(picker, canReadMediaStore = false)
        assertEquals(strip(picker), blocked.openUri)
        assertFalse(blocked.upgraded)
        assertFalse(blocked.mediaStore)
        assertEquals(externalImageUri(12), resolvePhotoRead(backport, canReadMediaStore = true).openUri)
    }

    @Test
    fun mediaStoreUriStaysOnItsOwnVolume() {
        val resolved = resolvePhotoRead("$primary?requireOriginal=1", canReadMediaStore = true)
        assertEquals(PhotoUriKind.MEDIA_STORE, resolved.kind)
        assertEquals(primary, resolved.openUri)
        assertFalse(resolved.upgraded)
        assertTrue(resolved.mediaStore)
        assertEquals(PhotoUriKind.MEDIA_STORE, classifyPhotoUri(store))
    }

    @Test
    fun documentAndEmbeddedUrisUpgradeOnlyWithReadAccess() {
        val documentRead = resolvePhotoRead(document, canReadMediaStore = true)
        assertEquals(PhotoUriKind.MEDIA_DOCUMENT, documentRead.kind)
        assertEquals(externalImageUri(55), documentRead.openUri)
        assertFalse(resolvePhotoRead(document, canReadMediaStore = false).upgraded)

        val embedded = resolvePhotoRead(photos, canReadMediaStore = true)
        assertEquals(PhotoUriKind.OTHER, embedded.kind)
        assertEquals(externalImageUri(321), embedded.openUri)
        assertFalse(resolvePhotoRead(photos, canReadMediaStore = false).upgraded)
    }

    @Test
    fun vendorGalleryUriStaysOtherUntilAnEmbeddedIdExists() {
        val resolved = resolvePhotoRead(vendor, canReadMediaStore = true)
        assertEquals(PhotoUriKind.OTHER, resolved.kind)
        assertEquals(vendor, resolved.openUri)
        assertFalse(resolved.mediaStore)
    }

    @Test
    fun permissionSetsMatchTheSdk() {
        assertEquals(
            setOf(MediaGrant.IMAGES, MediaGrant.VISUAL_USER_SELECTED, MediaGrant.MEDIA_LOCATION),
            grantsToRequest(34),
        )
        assertEquals(setOf(MediaGrant.IMAGES, MediaGrant.MEDIA_LOCATION), grantsToRequest(33))
        assertEquals(
            setOf(MediaGrant.EXTERNAL_STORAGE, MediaGrant.MEDIA_LOCATION),
            grantsToRequest(29),
        )
        assertEquals(setOf(MediaGrant.EXTERNAL_STORAGE), grantsToRequest(28))
    }

    @Test
    fun locationPermissionWithoutMediaReadDoesNotUnlockOriginals() {
        assertEquals(LibraryAccess.NONE, libraryAccess(34, setOf(MediaGrant.MEDIA_LOCATION)))
        assertEquals(LibraryAccess.PARTIAL, libraryAccess(34, setOf(MediaGrant.VISUAL_USER_SELECTED)))
        assertEquals(
            LibraryAccess.FULL,
            libraryAccess(34, setOf(MediaGrant.IMAGES, MediaGrant.MEDIA_LOCATION)),
        )
        assertFalse(canReadOriginalLocation(33, setOf(MediaGrant.IMAGES)))
        assertFalse(canReadOriginalLocation(33, setOf(MediaGrant.MEDIA_LOCATION)))
        assertTrue(
            canReadOriginalLocation(33, setOf(MediaGrant.IMAGES, MediaGrant.MEDIA_LOCATION)),
        )
        assertTrue(
            canReadOriginalLocation(34, setOf(MediaGrant.VISUAL_USER_SELECTED, MediaGrant.MEDIA_LOCATION)),
        )
        assertTrue(canReadOriginalLocation(28, setOf(MediaGrant.EXTERNAL_STORAGE)))
        assertEquals(LibraryAccess.NONE, libraryAccess(30, emptySet()))
        assertEquals(LibraryAccess.FULL, libraryAccess(30, setOf(MediaGrant.EXTERNAL_STORAGE)))
    }

    @Test
    fun partialAccessAsksAgainAndAFullGrantOpensSettings() {
        assertEquals(
            AccessAction.REQUEST,
            accessAction(34, setOf(MediaGrant.VISUAL_USER_SELECTED, MediaGrant.MEDIA_LOCATION)),
        )
        assertEquals(AccessAction.REQUEST, accessAction(33, setOf(MediaGrant.IMAGES)))
        assertEquals(
            AccessAction.SETTINGS,
            accessAction(33, setOf(MediaGrant.IMAGES, MediaGrant.MEDIA_LOCATION)),
        )
        assertEquals(AccessAction.SETTINGS, accessAction(28, setOf(MediaGrant.EXTERNAL_STORAGE)))
    }

    @Test
    fun redactionIsSeparateFromAFileThatHasNoGps() {
        assertEquals(
            LocationExposure.REDACTED,
            locationExposure(PhotoUriKind.PICKER, upgraded = false, openedOriginal = false, hasCoordinates = false),
        )
        assertEquals(
            LocationExposure.ABSENT,
            locationExposure(PhotoUriKind.PICKER, upgraded = true, openedOriginal = true, hasCoordinates = false),
        )
        assertEquals(
            LocationExposure.PRESENT,
            locationExposure(PhotoUriKind.PICKER, upgraded = false, openedOriginal = false, hasCoordinates = true),
        )
        assertEquals(
            LocationExposure.REDACTED,
            locationExposure(PhotoUriKind.MEDIA_STORE, upgraded = false, openedOriginal = false, hasCoordinates = false),
        )
        assertEquals(
            LocationExposure.ABSENT,
            locationExposure(PhotoUriKind.MEDIA_STORE, upgraded = false, openedOriginal = true, hasCoordinates = false),
        )
        assertEquals(
            LocationExposure.REDACTED,
            locationExposure(PhotoUriKind.OTHER, upgraded = false, openedOriginal = false, hasCoordinates = false),
        )
    }

    private fun strip(uri: String): String = uri.substringBefore('?')
}
