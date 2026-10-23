package app.altitude.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.CapturedClock
import app.altitude.domain.ExifOrientation
import app.altitude.domain.ImportNoticeKind
import app.altitude.domain.PhotoCapture
import app.altitude.domain.WatermarkStyle
import app.altitude.domain.selectPhotoAltitude
import app.altitude.i18n.rememberLocalFormat
import java.time.LocalDateTime
import java.time.ZoneOffset

@Preview(name = "Import portrait", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun PreviewImportPortrait() {
    ImportPreviewSample(width = 720, height = 960, latitude = 29.65218, longitude = 91.117, altitude = 3685.0, place = "Lhasa")
}

@Preview(name = "Import landscape photo", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun PreviewImportLandscapePhoto() {
    ImportPreviewSample(width = 960, height = 640, latitude = 27.9881, longitude = 86.925, altitude = 5200.0, place = "Khumbu")
}

@Preview(name = "Import no GPS", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun PreviewImportNoGps() {
    ImportPreviewSample(width = 720, height = 960, latitude = null, longitude = null, altitude = null, place = null)
}

@Composable
private fun ImportPreviewSample(
    width: Int,
    height: Int,
    latitude: Double?,
    longitude: Double?,
    altitude: Double?,
    place: String?,
) {
    val format = rememberLocalFormat()
    val capture = PhotoCapture(
        latitude = latitude,
        longitude = longitude,
        exifAltitudeMeters = altitude,
        clock = CapturedClock(LocalDateTime.of(2024, 6, 15, 14, 30), ZoneOffset.ofHours(8)),
        orientation = ExifOrientation.NORMAL,
    )
    val selected = selectPhotoAltitude(altitude, null, capture.hasCoordinates)
    val content = format.importWatermark(capture, selected, place, showPlace = place != null, AltitudeUnit.METERS)
    val framed = remember(width, height, content) {
        WatermarkRenderer.render(scene(width, height), content, WatermarkStyle.PLATE_LIGHT, CaptionAnchor.BOTTOM)
    }
    ImportPreviewScaffold(
        pageLabel = null,
        loading = false,
        errorRes = null,
        notice = if (capture.hasCoordinates) ImportNoticeKind.NONE else ImportNoticeKind.NO_LOCATION,
        showNotice = !capture.hasCoordinates,
        resolving = false,
        style = WatermarkStyle.PLATE_LIGHT,
        anchor = CaptionAnchor.BOTTOM,
        canSave = true,
        canSaveAll = false,
        multiple = false,
        saving = false,
        status = null,
        onStyle = {},
        onAnchor = {},
        onClose = {},
        onAllowLocations = {},
        onSave = {},
        onSaveAll = {},
        image = framed.asImageBitmap(),
        modifier = Modifier,
    )
}

private fun scene(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        intArrayOf(0xFF1B3348.toInt(), 0xFFC4874A.toInt(), 0xFFE7D3B0.toInt()),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}
