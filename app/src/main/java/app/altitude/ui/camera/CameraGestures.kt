package app.altitude.ui.camera

import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.altitude.R
import app.altitude.domain.CameraZoom
import app.altitude.ui.theme.Outfit
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val FOCUS_CANCEL_MS = 4_000L

@Composable
fun PreviewGestures(
    previewView: PreviewView,
    camera: Camera?,
    zoomRatio: Float,
    zoomMin: Float,
    zoomMax: Float,
    onZoomRatio: (Float) -> Unit,
    onPinch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewDescription = stringResource(R.string.cd_camera_preview)
    val exposureDescription = stringResource(R.string.cd_exposure)
    val focusDescription = stringResource(R.string.cd_focus_target)
    val lockedLabel = stringResource(R.string.focus_locked)
    val latestRatio by rememberUpdatedState(zoomRatio)
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var locked by remember { mutableStateOf(false) }
    val latestLocked by rememberUpdatedState(locked)
    var focusToken by remember { mutableIntStateOf(0) }
    var exposureIndex by remember { mutableFloatStateOf(0f) }
    val exposure = camera?.cameraInfo?.exposureState
    val exposureRange = exposure?.exposureCompensationRange
    val exposureSupported = camera != null &&
        exposure?.isExposureCompensationSupported == true &&
        exposureRange != null &&
        exposureRange.upper > exposureRange.lower

    LaunchedEffect(focusToken, locked) {
        if (locked || focusAt == null) return@LaunchedEffect
        kotlinx.coroutines.delay(FOCUS_CANCEL_MS)
        focusAt = null
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = previewDescription }
                .pointerInput(camera, zoomMin, zoomMax) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val start = down.position
                        var runningRatio = latestRatio
                        var pinch = false
                        var moved = false
                        var longFired = false
                        val slop = viewConfiguration.touchSlop
                        val longPressMs = viewConfiguration.longPressTimeoutMillis
                        while (true) {
                            val elapsed = (android.os.SystemClock.uptimeMillis() - down.uptimeMillis).coerceAtLeast(0L)
                            val wait = if (!pinch && !moved && !longFired) {
                                (longPressMs - elapsed).coerceAtLeast(1L)
                            } else {
                                null
                            }
                            val event = if (wait == null) {
                                awaitPointerEvent()
                            } else {
                                withTimeoutOrNull(wait) { awaitPointerEvent() }
                            }
                            if (event == null) {
                                longFired = true
                                val active = camera
                                if (active != null) {
                                    val nextLock = !latestLocked
                                    focusMetering(previewView, active, start, nextLock)
                                    locked = nextLock
                                    focusAt = start
                                    focusToken += 1
                                    exposureIndex = active.cameraInfo.exposureState.exposureCompensationIndex.toFloat()
                                }
                                continue
                            }
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                pinch = true
                                val scale = event.calculateZoom()
                                if (scale != 1f) {
                                    runningRatio = CameraZoom.afterPinch(runningRatio, scale, zoomMin, zoomMax)
                                    onZoomRatio(runningRatio)
                                    onPinch()
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1) {
                                val change = pressed.first()
                                if (!pinch && (change.position - start).getDistance() > slop) moved = true
                                if (change.positionChanged()) change.consume()
                            }
                            if (pressed.isEmpty()) break
                        }
                        val active = camera
                        if (!pinch && !moved && !longFired && active != null) {
                            focusMetering(previewView, active, start, lock = false)
                            locked = false
                            focusAt = start
                            focusToken += 1
                            exposureIndex = active.cameraInfo.exposureState.exposureCompensationIndex.toFloat()
                        }
                    }
                },
        )
        val point = focusAt
        if (point != null && camera != null) {
            FocusHud(
                point = point,
                token = focusToken,
                locked = locked,
                lockedLabel = lockedLabel,
                focusDescription = focusDescription,
                exposureSupported = exposureSupported,
                exposureIndex = exposureIndex,
                exposureLower = exposureRange?.lower ?: 0,
                exposureUpper = exposureRange?.upper ?: 0,
                exposureDescription = exposureDescription,
                onExposure = { index ->
                    exposureIndex = index.toFloat()
                    camera.cameraControl.setExposureCompensationIndex(index)
                },
            )
        }
    }
}

@Composable
fun ZoomControls(
    ratio: Float,
    min: Float,
    max: Float,
    linear: Float,
    onRatio: (Float) -> Unit,
    onLinear: (Float) -> Unit,
) {
    val locale = LocalResources.current.configuration.locales[0]
    val presets = CameraZoom.presetsWithin(min, max)
    val adjustable = max - min > 0.05f
    if (presets.isEmpty() && !adjustable) return
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                presets.forEach { preset ->
                    val number = CameraZoom.formatZoomNumber(preset, locale)
                    val label = stringResource(R.string.zoom_ratio, number)
                    val description = stringResource(R.string.cd_zoom_preset, label)
                    val selected = CameraZoom.matchesPreset(ratio, preset)
                    Text(
                        text = label,
                        color = if (selected) Color.Black else Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.16f))
                            .semantics { contentDescription = description }
                            .clickable { onRatio(preset) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (adjustable) {
            val sliderDescription = stringResource(R.string.cd_zoom_slider)
            Slider(
                value = linear.coerceIn(0f, 1f),
                onValueChange = onLinear,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .semantics { contentDescription = sliderDescription },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                ),
            )
        }
    }
}

@Composable
fun ZoomReadout(ratio: Float, modifier: Modifier = Modifier) {
    val locale = LocalResources.current.configuration.locales[0]
    val label = stringResource(R.string.zoom_ratio, CameraZoom.formatZoomNumber(ratio, locale))
    Text(
        text = label,
        color = Color.White,
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun FocusHud(
    point: Offset,
    token: Int,
    locked: Boolean,
    lockedLabel: String,
    focusDescription: String,
    exposureSupported: Boolean,
    exposureIndex: Float,
    exposureLower: Int,
    exposureUpper: Int,
    exposureDescription: String,
    onExposure: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val scale = remember { Animatable(1.35f) }
    LaunchedEffect(token) {
        scale.snapTo(1.45f)
        scale.animateTo(1f, tween(280))
    }
    val ringColor = if (locked) Color(0xFFFFC857) else Color.White
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ring = 72.dp
        val sliderW = if (exposureSupported) 128.dp else 0.dp
        val labelExtra = if (locked) 22.dp else 0.dp
        val maxX = (maxWidth - ring - sliderW).coerceAtLeast(0.dp)
        val maxY = (maxHeight - ring - labelExtra).coerceAtLeast(0.dp)
        val x = with(density) { (point.x.toDp() - ring / 2).coerceIn(0.dp, maxX) }
        val y = with(density) { (point.y.toDp() - ring / 2).coerceIn(0.dp, maxY) }
        Row(
            modifier = Modifier.offset {
                androidx.compose.ui.unit.IntOffset(x.roundToPx(), y.roundToPx())
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(ring)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .border(2.dp, ringColor, CircleShape)
                        .semantics { contentDescription = focusDescription },
                )
                if (locked) {
                    Text(
                        text = lockedLabel,
                        color = ringColor,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (exposureSupported) {
                Slider(
                    value = exposureIndex.coerceIn(exposureLower.toFloat(), exposureUpper.toFloat()),
                    onValueChange = { onExposure(it.roundToInt()) },
                    valueRange = exposureLower.toFloat()..exposureUpper.toFloat(),
                    modifier = Modifier
                        .width(sliderW)
                        .padding(start = 8.dp)
                        .semantics { contentDescription = exposureDescription },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                    ),
                )
            }
        }
    }
}

private fun focusMetering(previewView: PreviewView, camera: Camera, offset: Offset, lock: Boolean) {
    val width = previewView.width.toFloat()
    val height = previewView.height.toFloat()
    if (width <= 1f || height <= 1f) return
    val x = offset.x.coerceIn(0f, width - 1f)
    val y = offset.y.coerceIn(0f, height - 1f)
    val point = previewView.meteringPointFactory.createPoint(x, y)
    val flags = FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
    val builder = FocusMeteringAction.Builder(point, flags)
    if (lock) {
        builder.disableAutoCancel()
    } else {
        builder.setAutoCancelDuration(4, TimeUnit.SECONDS)
    }
    camera.cameraControl.startFocusAndMetering(builder.build())
}
