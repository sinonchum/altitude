package app.altitude.i18n

import androidx.annotation.StringRes
import app.altitude.R
import app.altitude.domain.AltitudeSource
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.DisplayTheme
import app.altitude.domain.WatermarkStyle

@StringRes
fun AltitudeUnit.nameRes(): Int = when (this) {
    AltitudeUnit.METERS -> R.string.unit_meters_name
    AltitudeUnit.FEET -> R.string.unit_feet_name
}

@StringRes
fun AltitudeUnit.symbolRes(): Int = when (this) {
    AltitudeUnit.METERS -> R.string.unit_meters_symbol
    AltitudeUnit.FEET -> R.string.unit_feet_symbol
}

@StringRes
fun DisplayTheme.nameRes(): Int = when (this) {
    DisplayTheme.NIGHT -> R.string.theme_night
    DisplayTheme.VOID -> R.string.theme_void
    DisplayTheme.GLACIER -> R.string.theme_glacier
    DisplayTheme.SAFFRON -> R.string.theme_saffron
    DisplayTheme.SNOW -> R.string.theme_snow
}

@StringRes
fun AltitudeSource.labelRes(): Int = when (this) {
    AltitudeSource.GPS -> R.string.source_gps
    AltitudeSource.FUSED -> R.string.source_fused
    AltitudeSource.PRESSURE_HOLDOVER -> R.string.source_holdover
    AltitudeSource.PRESSURE_UNCALIBRATED -> R.string.source_pressure
    AltitudeSource.NONE -> R.string.source_none
}

@StringRes
fun WatermarkStyle.labelRes(): Int = when (this) {
    WatermarkStyle.PLATE_LIGHT -> R.string.watermark_light
    WatermarkStyle.PLATE_DARK -> R.string.watermark_dark
    WatermarkStyle.MOUNT -> R.string.watermark_mount
    WatermarkStyle.CAPTION -> R.string.watermark_caption
}

@StringRes
fun CaptionAnchor.labelRes(): Int = when (this) {
    CaptionAnchor.BOTTOM -> R.string.anchor_bottom
    CaptionAnchor.TOP -> R.string.anchor_top
}
