package app.altitude.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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
import app.altitude.i18n.LocalFormat
import app.altitude.i18n.rememberLocalFormat
import app.altitude.ui.theme.AltitudeTypefaces
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertControlsFit()
    }

    @Test
    fun englishImportPreviews() = renderLocale("en")

    @Test
    @Config(qualifiers = "fr-rFR-w1280dp-h1280dp-xhdpi")
    fun frenchImportPreviews() = renderLocale("fr")

    @Test
    @Config(qualifiers = "zh-rCN-w1280dp-h1280dp-xhdpi")
    fun chineseImportPreviews() = renderLocale("zh")

    @Test
    fun missingAltitudeIsOmittedFromTheFrame() {
        val context = RuntimeEnvironment.getApplication()
        val format = LocalFormat(context.resources)
        val capture = PhotoCapture(
            latitude = null,
            longitude = null,
            exifAltitudeMeters = null,
            clock = CapturedClock(LocalDateTime.of(2024, 6, 15, 14, 30), ZoneOffset.ofHours(8)),
            orientation = ExifOrientation.NORMAL,
        )
        val missing = selectPhotoAltitude(null, elevationLookupMeters = null, hasCoordinates = false)
        val content = format.importWatermark(capture, missing, place = null, showPlace = true, unit = AltitudeUnit.METERS)
        assertEquals("", content.altitude)
        val photo = scene(480, 320)
        val clean = WatermarkRenderer.render(photo, content, WatermarkStyle.PLATE_LIGHT, CaptionAnchor.BOTTOM)
        val dashed = WatermarkRenderer.render(
            photo,
            content.copy(altitude = context.getString(R.string.altitude_unavailable)),
            WatermarkStyle.PLATE_LIGHT,
            CaptionAnchor.BOTTOM,
        )
        val plateTop = photo.height
        val right = (photo.width * 0.55f).toInt()
        assertTrue(darkPixels(dashed, plateTop, dashed.height, right) > darkPixels(clean, plateTop, clean.height, right))

        val labeled = content.copy(altitude = "3,685 m")
        val top = WatermarkRenderer.render(photo, labeled, WatermarkStyle.PLATE_LIGHT, CaptionAnchor.TOP)
        val bottom = WatermarkRenderer.render(photo, labeled, WatermarkStyle.PLATE_LIGHT, CaptionAnchor.BOTTOM)
        assertEquals(top.width, bottom.width)
        assertEquals(top.height, bottom.height)
        assertTrue(isWhite(top, top.width / 2, 2))
        assertFalse(isWhite(bottom, bottom.width / 2, 2))
        assertTrue(isWhite(bottom, bottom.width / 2, bottom.height - 3))
        assertFalse(isWhite(top, top.width / 2, top.height - 3))

        val mountTop = WatermarkRenderer.render(photo, labeled, WatermarkStyle.MOUNT, CaptionAnchor.TOP)
        val mountBottom = WatermarkRenderer.render(photo, labeled, WatermarkStyle.MOUNT, CaptionAnchor.BOTTOM)
        assertEquals(mountTop.width, mountBottom.width)
        assertEquals(mountTop.height, mountBottom.height)
        assertTrue(isWhite(mountTop, mountTop.width / 2, 40))
        assertFalse(isWhite(mountBottom, mountBottom.width / 2, 40))
    }

    private fun renderLocale(suffix: String) {
        val context = RuntimeEnvironment.getApplication()
        val portrait = Shot(
            photo = scene(900, 1200),
            latitude = 29.65218,
            longitude = 91.117,
            altitude = 3685.0,
            place = "Lhasa",
            notice = ImportNoticeKind.NONE,
            width = 411,
            height = 891,
            name = "import-preview-portrait-$suffix",
        )
        val landscape = Shot(
            photo = scene(1400, 900),
            latitude = 27.9881,
            longitude = 86.925,
            altitude = 5200.0,
            place = "Khumbu",
            notice = ImportNoticeKind.NONE,
            width = 891,
            height = 411,
            name = "import-preview-landscape-phone-$suffix",
        )
        val noGps = Shot(
            photo = scene(900, 1200),
            latitude = null,
            longitude = null,
            altitude = null,
            place = null,
            notice = ImportNoticeKind.NO_LOCATION,
            width = 411,
            height = 891,
            name = "import-preview-no-gps-$suffix",
        )
        val computed = Shot(
            photo = scene(900, 1200),
            latitude = 29.65218,
            longitude = 91.117,
            altitude = null,
            place = "Lhasa",
            notice = ImportNoticeKind.COMPUTED_ALTITUDE,
            width = 411,
            height = 891,
            name = "import-preview-computed-$suffix",
            computedMeters = 3648.0,
        )
        var shot by mutableStateOf(portrait)
        rule.setContent {
            FramedPreview(
                photo = shot.photo,
                latitude = shot.latitude,
                longitude = shot.longitude,
                altitude = shot.altitude,
                place = shot.place,
                notice = shot.notice,
                computedMeters = shot.computedMeters,
                modifier = Modifier.requiredSize(shot.width.dp, shot.height.dp),
            )
        }
        fun capture(next: Shot, check: () -> Unit = {}) {
            shot = next
            rule.waitForIdle()
            rule.onNodeWithTag("import_preview").assertIsDisplayed()
            rule.onNodeWithTag("import_photo").assertIsDisplayed()
            save("import_preview", next.name)
            check()
        }
        capture(portrait) { assertControlsFit() }
        capture(landscape) {
            assertControlsFit()
            assertSaveAnchored()
            val root = rule.onNodeWithTag("import_preview").fetchSemanticsNode()
            val chip = rule.onNodeWithText(context.getString(R.string.watermark_caption)).fetchSemanticsNode()
            assertTrue(chip.boundsInRoot.left > root.size.width * 0.45f)
        }
        capture(noGps) {
            rule.onNodeWithTag("import_notice").assertIsDisplayed()
            assertTextFits(context.getString(R.string.import_no_location_title))
            assertTextFits(context.getString(R.string.import_no_location_body))
            assertControlsFit()
        }
        capture(computed) {
            assertTextFits(context.getString(R.string.import_computed_title))
            assertTextFits(context.getString(R.string.import_altitude_computed))
            assertControlsFit()
        }
    }

    private fun assertControlsFit() {
        val context = RuntimeEnvironment.getApplication()
        rule.onNodeWithTag("import_anchor").assertIsDisplayed()
        listOf(
            R.string.watermark_light,
            R.string.watermark_dark,
            R.string.watermark_mount,
            R.string.watermark_caption,
            R.string.anchor_bottom,
            R.string.anchor_top,
            R.string.import_save,
        ).forEach { id -> assertTextFits(context.getString(id)) }
    }

    private fun assertSaveAnchored() {
        val save = rule.onNodeWithText(
            RuntimeEnvironment.getApplication().getString(R.string.import_save),
        ).fetchSemanticsNode()
        val root = rule.onNodeWithTag("import_preview").fetchSemanticsNode()
        val gap = root.size.height - save.boundsInRoot.bottom
        assertTrue("Save copy sits $gap px above the bottom", gap < 120f)
    }

    private fun assertTextFits(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getter ->
            getter(layouts)
        }
        val layout = layouts.single()
        for (line in 0 until layout.lineCount) {
            assertFalse("$text line $line is clipped", layout.isLineEllipsized(line))
        }
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

private data class Shot(
    val photo: Bitmap,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val place: String?,
    val notice: ImportNoticeKind,
    val width: Int,
    val height: Int,
    val name: String,
    val computedMeters: Double? = null,
)

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

private fun isWhite(bitmap: Bitmap, x: Int, y: Int): Boolean = (bitmap.getPixel(x, y) and 0xFFFFFF) == 0xFFFFFF

private fun darkPixels(bitmap: Bitmap, top: Int, bottom: Int, left: Int): Int {
    var count = 0
    for (y in top until bottom) {
        for (x in left until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            if (red < 80 && green < 80 && blue < 80) count++
        }
    }
    return count
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
