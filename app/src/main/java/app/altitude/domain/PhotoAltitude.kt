package app.altitude.domain

/**
 * Where the altitude drawn on an imported photo came from.
 * [FROM_PHOTO] is the file's own GPSAltitude. [COMPUTED] is a DEM lookup.
 * [MISSING] means neither source produced a number.
 */
enum class AltitudeProvenance {
    FROM_PHOTO,
    COMPUTED,
    MISSING,
}

data class SelectedAltitude(
    val meters: Double?,
    val provenance: AltitudeProvenance,
)

/**
 * EXIF altitude wins, including zero and below-sea-level values.
 * A lookup is used only when the photo has coordinates and no altitude of its own.
 * Without coordinates the lookup is ignored, so a number is never invented.
 */
fun selectPhotoAltitude(
    exifAltitudeMeters: Double?,
    elevationLookupMeters: Double?,
    hasCoordinates: Boolean,
): SelectedAltitude {
    val recorded = exifAltitudeMeters?.takeIf { it.isFinite() }
    if (recorded != null) return SelectedAltitude(recorded, AltitudeProvenance.FROM_PHOTO)
    if (hasCoordinates) {
        val lookedUp = elevationLookupMeters?.takeIf { it.isFinite() }
        if (lookedUp != null) return SelectedAltitude(lookedUp, AltitudeProvenance.COMPUTED)
    }
    return SelectedAltitude(null, AltitudeProvenance.MISSING)
}

enum class ImportNoticeKind {
    NONE,
    NO_LOCATION,
    LOCATION_HIDDEN,
    COMPUTED_ALTITUDE,
    ALTITUDE_MISSING,
}

/**
 * [altitude] null means the lookup is still running; that is not a missing value yet.
 * Location hidden is only when the photo has no coordinates and photo-location access
 * was not granted, because the picker may have removed GPS on Android 10+.
 */
fun importNoticeKind(
    hasCoordinates: Boolean,
    altitude: SelectedAltitude?,
    mediaLocationGranted: Boolean,
): ImportNoticeKind {
    if (!hasCoordinates) {
        return if (mediaLocationGranted) ImportNoticeKind.NO_LOCATION else ImportNoticeKind.LOCATION_HIDDEN
    }
    return when (altitude?.provenance) {
        AltitudeProvenance.COMPUTED -> ImportNoticeKind.COMPUTED_ALTITUDE
        AltitudeProvenance.MISSING -> ImportNoticeKind.ALTITUDE_MISSING
        AltitudeProvenance.FROM_PHOTO, null -> ImportNoticeKind.NONE
    }
}
