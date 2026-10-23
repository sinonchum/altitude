package app.altitude.ui.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Landscape front", widthDp = 891, heightDp = 411, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewLandscapeFront() {
    CameraStageSample(front = true, flashOn = true, modifier = Modifier.fillMaxSize())
}

@Preview(name = "Landscape back", widthDp = 891, heightDp = 411, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewLandscapeBack() {
    CameraStageSample(front = false, modifier = Modifier.fillMaxSize())
}

@Preview(name = "Portrait front", widthDp = 411, heightDp = 891, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewPortraitFront() {
    CameraStageSample(front = true, flashOn = false, modifier = Modifier.fillMaxSize())
}

@Preview(name = "Portrait back", widthDp = 411, heightDp = 891, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewPortraitBack() {
    CameraStageSample(front = false, modifier = Modifier.fillMaxSize())
}

@Preview(
    name = "Landscape cutout",
    device = "spec:width=891dp,height=411dp,dpi=420,cutout=punch_hole,navigation=gesture",
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
private fun PreviewLandscapeCutout() {
    CameraStageSample(front = true, flashOn = true, shutterOnEnd = false, modifier = Modifier.fillMaxSize())
}
