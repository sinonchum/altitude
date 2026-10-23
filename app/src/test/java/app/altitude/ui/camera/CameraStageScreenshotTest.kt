package app.altitude.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1280dp-h1280dp")
class CameraStageScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun landscapeFrontPreviewFillsTheScreen() {
        render(891, 411, front = true, flashOn = true, shutterOnEnd = true, name = "landscape-front")
    }

    @Test
    fun landscapeBackPreviewFillsTheScreen() {
        render(891, 411, front = false, flashOn = false, shutterOnEnd = true, name = "landscape-back")
    }

    @Test
    fun landscapeReverseKeepsThePreviewLarge() {
        render(891, 411, front = true, flashOn = true, shutterOnEnd = false, name = "landscape-reverse-front")
    }

    @Test
    fun portraitFrontShowsTheHeroPreview() {
        render(411, 891, front = true, flashOn = false, shutterOnEnd = true, name = "portrait-front")
    }

    @Test
    fun portraitBackShowsTheHeroPreview() {
        render(411, 891, front = false, flashOn = false, shutterOnEnd = true, name = "portrait-back")
    }

    private fun render(
        width: Int,
        height: Int,
        front: Boolean,
        flashOn: Boolean,
        shutterOnEnd: Boolean,
        name: String,
    ) {
        rule.setContent {
            CameraStageSample(
                front = front,
                flashOn = flashOn,
                shutterOnEnd = shutterOnEnd,
                modifier = Modifier.requiredSize(width.dp, height.dp),
            )
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        val preview = rule.onNodeWithTag("camera_preview").fetchSemanticsNode().boundsInRoot
        val stage = rule.onNodeWithTag("camera_stage").fetchSemanticsNode().boundsInRoot
        if (width > height) {
            assertTrue(
                "preview ${preview.width}x${preview.height} of stage ${stage.width}x${stage.height}",
                stage.width > stage.height && preview.height / stage.height >= 0.70f,
            )
        }
        val bitmap = rule.onNodeWithTag("camera_stage").captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
