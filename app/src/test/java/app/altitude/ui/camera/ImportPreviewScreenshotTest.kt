package app.altitude.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.altitude.R
import app.altitude.domain.AltitudeProvenance
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.SelectedAltitude
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.CapturedClock
import app.altitude.domain.ExifOrientation
import app.altitude.domain.ImportNoticeKind
import app.altitude.domain.PhotoCapture
import app.altitude.domain.WatermarkContent
import app.altitude.domain.WatermarkStyle
import app.altitude.domain.selectPhotoAltitude
import app.altitude.i18n.rememberLocalFormat
import app.altitude.ui.theme.AltitudeTypefaces
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1280dp-h1280dp-xhdpi")
class ImportPreviewScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun loadType() {
        AltitudeTypefaces.load(RuntimeEnvironment.getApplication())
    }

    @Test
    fun galleryShowsTheImportEntry() {
        rule.setContent {
            Box(Modifier.requiredSize(411.dp, 891.dp).testTag("import_entry_screen")) {
                GalleryScreen(
                    uris = emptyList(),
                    notice = null,
                    onClose = {},
                    onDelete = { false },
                    onRefresh = {},
                    onMessage = {},
                    onImport = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("gallery_import").assertIsDisplayed()
        rule.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.gallery_empty)).assertIsDisplayed()
        save("import_entry_screen", "import-entry")
    }

    @Test
    fun portraitPhotoShowsTheFrame() {
        show(
            photo = scene(900, 1200),
            latitude = 29.65218,
            longitude = 91.117,
            altitude = 3685.0,
            place = "Lhasa",
            notice = ImportNoticeKind.NONE,
            width = 411,
            height = 891,
            name = "import-preview-portrait",
        )
    }

    @Test
    fun landscapePhotoShowsTheFrame() {
        show(
            photo = scene(1400, 900),
            latitude = 27.9881,
            longitude = 86.925,
            altitude = 5200.0,
            place = "Khumbu",
            notice = ImportNoticeKind.NONE,
            width = 411,
            height = 891,
            name = "import-preview-landscape",
        )
    }

    @Test
    fun landscapePhoneKeepsTheFrameBesideTheControls() {
        show(
            photo = scene(1400, 900),
            latitude = 27.9881,
            longitude = 86.925,
            altitude = 5200.0,
            place = "Khumbu",
            notice = ImportNoticeKind.NONE,
            width = 891,
            height = 411,
            name = "import-preview-landscape-phone",
        )
    }

    @Test
    fun computedAltitudeIsMarkedOnTheFrame() {
        show(
            photo = scene(900, 1200),
            latitude = 29.65218,
            longitude = 91.117,
            altitude = null,
            place = "Lhasa",
            notice = ImportNoticeKind.COMPUTED_ALTITUDE,
            width = 411,
            height = 891,
            name = "import-preview-computed",
            computedMeters = 3648.0,
        )
        val context = RuntimeEnvironment.getApplication()
        rule.onNodeWithText(context.getString(R.string.import_computed_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.import_altitude_computed)).assertIsDisplayed()
    }

    @Test
    fun photoWithoutGpsExplainsAndStillFrames() {
        show(
            photo = scene(900, 1200),
            latitude = null,
            longitude = null,
            altitude = null,
            place = null,
            notice = ImportNoticeKind.NO_LOCATION,
            width = 411,
            height = 891,
            name = "import-preview-no-gps",
        )
        rule.onNodeWithTag("import_notice").assertIsDisplayed()
        rule.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.import_no_location_title)).assertIsDisplayed()
    }

    private fun show(
        photo: Bitmap,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        place: String?,
        notice: ImportNoticeKind,
        width: Int,
        height: Int,
        name: String,
        computedMeters: Double? = null,
    ) {
        rule.setContent {
            FramedPreview(
                photo = photo,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                place = place,
                notice = notice,
                computedMeters = computedMeters,
                modifier = Modifier.requiredSize(width.dp, height.dp),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("import_preview").assertIsDisplayed()
        rule.onNodeWithTag("import_photo").assertIsDisplayed()
        save("import_preview", name)
    }

    private fun save(tag: String, name: String) {
        val image = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@Composable
private fun FramedPreview(
    photo: Bitmap,
    latitude: Double?,
    longitude: Double?,
    altitude: Double?,
    place: String?,
    notice: ImportNoticeKind,
    computedMeters: Double? = null,
    modifier: Modifier = Modifier,
) {
    val format = rememberLocalFormat()
    val capture = PhotoCapture(
        latitude = latitude,
        longitude = longitude,
        exifAltitudeMeters = altitude,
        clock = CapturedClock(LocalDateTime.of(2024, 6, 15, 14, 30), ZoneOffset.ofHours(8)),
        orientation = ExifOrientation.NORMAL,
    )
    val selected = if (computedMeters != null) {
        SelectedAltitude(computedMeters, AltitudeProvenance.COMPUTED)
    } else {
        selectPhotoAltitude(altitude, elevationLookupMeters = null, capture.hasCoordinates)
    }
    val content = format.importWatermark(capture, selected, place, showPlace = true, unit = AltitudeUnit.METERS)
    val framed = rememberFrame(photo, content)
    ImportPreviewScaffold(
        pageLabel = null,
        loading = false,
        errorRes = null,
        notice = notice,
        showNotice = notice != ImportNoticeKind.NONE,
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
        modifier = modifier,
    )
}

@Composable
private fun rememberFrame(photo: Bitmap, content: WatermarkContent): Bitmap {
    return remember(photo, content) {
        WatermarkRenderer.render(photo, content, WatermarkStyle.PLATE_LIGHT, CaptionAnchor.BOTTOM)
    }
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
    paint.shader = null
    paint.color = 0xFF8FA4B5.toInt()
    val horizon = height * 0.42f
    canvas.drawRect(0f, horizon, width.toFloat(), horizon + height * 0.012f, paint)
    return bitmap
}
