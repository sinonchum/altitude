package app.altitude.ui.camera

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.altitude.R
import app.altitude.domain.AppMode
import app.altitude.domain.CameraStageMetrics
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.FrameFit
import app.altitude.domain.WatermarkMetrics
import app.altitude.domain.WatermarkStyle
import app.altitude.i18n.labelRes
import app.altitude.ui.display.LocationGuidanceCopy
import app.altitude.ui.display.ModeSwitch
import app.altitude.ui.theme.Outfit

internal data class CameraChromeState(
    val cameraGranted: Boolean,
    val shutterOnEnd: Boolean,
    val lensFront: Boolean,
    val showLensButton: Boolean,
    val showScreenFlash: Boolean,
    val screenFlashOn: Boolean,
    val busy: Boolean,
    val upright: Float,
    val lensSpin: Float,
    val zoomVisible: Boolean,
    val zoomRatio: Float,
    val zoomMin: Float,
    val zoomMax: Float,
    val linearZoom: Float,
    val zoomAdjusting: Boolean,
    val style: WatermarkStyle,
    val anchor: CaptionAnchor,
    val hasPlace: Boolean,
    val guidance: LocationGuidanceCopy?,
    val saveMessage: Int?,
)

@Composable
internal fun CameraStage(
    state: CameraChromeState,
    onMode: (AppMode) -> Unit,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onToggleLens: () -> Unit,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    onShutter: () -> Unit,
    onZoomRatio: (Float) -> Unit,
    onLinearZoom: (Float) -> Unit,
    onGuidance: () -> Unit,
    onRequestCamera: () -> Unit,
    thumbnail: @Composable () -> Unit,
    photo: @Composable BoxScope.(FrameFit) -> Unit,
    plate: @Composable (FrameFit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var topReserve by remember { mutableStateOf(52.dp) }
    var bottomReserve by remember { mutableStateOf(220.dp) }
    BoxWithConstraints(
        modifier
            .background(Color.Black)
            .testTag("camera_stage"),
    ) {
        val landscape = CameraStageMetrics.isLandscape(maxWidth.value, maxHeight.value)
        val zoomRail = if (state.zoomVisible) CameraStageMetrics.ZOOM_RAIL.dp else 0.dp
        val startTarget = when {
            !landscape -> 0.dp
            state.shutterOnEnd -> CameraStageMetrics.UTILITY_RAIL.dp
            else -> CameraStageMetrics.SHUTTER_RAIL.dp + zoomRail
        }
        val endTarget = when {
            !landscape -> 0.dp
            state.shutterOnEnd -> CameraStageMetrics.SHUTTER_RAIL.dp + zoomRail
            else -> CameraStageMetrics.UTILITY_RAIL.dp
        }
        val topPad by animateDpAsState(if (landscape) 0.dp else topReserve, tween(280), label = "top")
        val bottomPad by animateDpAsState(if (landscape) 0.dp else bottomReserve, tween(280), label = "bottom")
        val startPad by animateDpAsState(startTarget, tween(280), label = "start")
        val endPad by animateDpAsState(endTarget, tween(280), label = "end")
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = startPad, top = topPad, end = endPad, bottom = bottomPad),
        ) {
            if (!state.cameraGranted) {
                PermissionPrompt(onRequestCamera)
            } else {
                ViewfinderFrame(
                    state = state,
                    landscape = landscape,
                    onLinearZoom = onLinearZoom,
                    photo = photo,
                    plate = plate,
                )
            }
            val message = state.saveMessage
            if (message != null) {
                Text(
                    text = stringResource(message),
                    color = Color(0xFF141414),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFF4F1EA))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            if (landscape && state.guidance != null) {
                GuidanceBanner(
                    guidance = state.guidance,
                    onGuidance = onGuidance,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
        if (landscape) {
            LandscapeRails(
                state = state,
                zoomRail = zoomRail,
                onMode = onMode,
                onStyle = onStyle,
                onAnchor = onAnchor,
                onToggleLens = onToggleLens,
                onToggleFlash = onToggleFlash,
                onOpenSettings = onOpenSettings,
                onShutter = onShutter,
                onZoomRatio = onZoomRatio,
                onLinearZoom = onLinearZoom,
                thumbnail = thumbnail,
            )
        } else {
            PortraitChrome(
                state = state,
                onMode = onMode,
                onStyle = onStyle,
                onAnchor = onAnchor,
                onToggleLens = onToggleLens,
                onToggleFlash = onToggleFlash,
                onOpenSettings = onOpenSettings,
                onShutter = onShutter,
                onZoomRatio = onZoomRatio,
                onLinearZoom = onLinearZoom,
                onGuidance = onGuidance,
                thumbnail = thumbnail,
                onTop = { topReserve = with(density) { it.toDp() } },
                onBottom = { bottomReserve = with(density) { it.toDp() } },
            )
        }
    }
}

@Composable
private fun ViewfinderFrame(
    state: CameraChromeState,
    landscape: Boolean,
    onLinearZoom: (Float) -> Unit,
    photo: @Composable BoxScope.(FrameFit) -> Unit,
    plate: @Composable (FrameFit) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(CameraStageMetrics.EDGE_INSET.dp),
    ) {
        val portraitFrame = maxHeight > maxWidth
        val aspect = if (portraitFrame) 3f / 4f else 4f / 3f
        val fit = with(density) {
            WatermarkMetrics.fitFrame(
                availWidth = maxWidth.toPx(),
                availHeight = maxHeight.toPx(),
                photoAspect = aspect,
                style = state.style,
                hasPlace = state.hasPlace,
            )
        }
        val photoW by animateDpAsState(with(density) { fit.photoWidth.toDp() }, tween(280), label = "photoW")
        val photoH by animateDpAsState(with(density) { fit.photoHeight.toDp() }, tween(280), label = "photoH")
        val side by animateDpAsState(with(density) { fit.side.toDp() }, tween(280), label = "side")
        val top by animateDpAsState(with(density) { fit.top.toDp() }, tween(280), label = "top")
        val bottom by animateDpAsState(with(density) { fit.bottom.toDp() }, tween(280), label = "bottom")
        val frameColor = when (state.style) {
            WatermarkStyle.PLATE_DARK -> Color(0xFF0A0A0A)
            WatermarkStyle.CAPTION -> Color.Black
            else -> Color.White
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(photoW + side * 2)
                .background(frameColor),
        ) {
            if (state.style == WatermarkStyle.MOUNT) {
                Spacer(Modifier.height(top).fillMaxWidth())
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = side)
                    .size(photoW, photoH)
                    .background(Color.Black)
                    .testTag("camera_preview"),
            ) {
                photo(fit)
                if (landscape && state.zoomVisible && state.zoomAdjusting) {
                    Box(
                        Modifier
                            .align(if (state.shutterOnEnd) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(4.dp),
                    ) {
                        VerticalZoomSlider(state.linearZoom, onLinearZoom)
                    }
                }
            }
            if (state.style != WatermarkStyle.CAPTION) {
                Box(
                    Modifier
                        .padding(horizontal = side)
                        .width(photoW)
                        .height(bottom),
                ) {
                    plate(fit)
                }
            }
        }
    }
}

@Composable
private fun LandscapeRails(
    state: CameraChromeState,
    zoomRail: androidx.compose.ui.unit.Dp,
    onMode: (AppMode) -> Unit,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onToggleLens: () -> Unit,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    onShutter: () -> Unit,
    onZoomRatio: (Float) -> Unit,
    onLinearZoom: (Float) -> Unit,
    thumbnail: @Composable () -> Unit,
) {
    val shutterAlign = if (state.shutterOnEnd) Alignment.CenterEnd else Alignment.CenterStart
    val utilityAlign = if (state.shutterOnEnd) Alignment.CenterStart else Alignment.CenterEnd
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(utilityAlign)
                .width(CameraStageMetrics.UTILITY_RAIL.dp)
                .fillMaxHeight(),
        ) {
            UtilityRail(state, onMode, onStyle, onAnchor, onToggleFlash, onOpenSettings, verticalMode = true)
        }
        if (state.zoomVisible) {
            Box(
                Modifier
                    .align(shutterAlign)
                    .padding(
                        end = if (state.shutterOnEnd) CameraStageMetrics.SHUTTER_RAIL.dp else 0.dp,
                        start = if (state.shutterOnEnd) 0.dp else CameraStageMetrics.SHUTTER_RAIL.dp,
                    )
                    .width(zoomRail)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                ZoomControls(
                    ratio = state.zoomRatio,
                    min = state.zoomMin,
                    max = state.zoomMax,
                    linear = state.linearZoom,
                    onRatio = onZoomRatio,
                    onLinear = onLinearZoom,
                    modifier = Modifier.width(zoomRail),
                    vertical = true,
                    showSlider = false,
                )
            }
        }
        Box(
            Modifier
                .align(shutterAlign)
                .width(CameraStageMetrics.SHUTTER_RAIL.dp)
                .fillMaxHeight(),
        ) {
            CaptureRail(state, onToggleLens, onShutter, thumbnail)
        }
    }
}

@Composable
private fun PortraitChrome(
    state: CameraChromeState,
    onMode: (AppMode) -> Unit,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onToggleLens: () -> Unit,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    onShutter: () -> Unit,
    onZoomRatio: (Float) -> Unit,
    onLinearZoom: (Float) -> Unit,
    onGuidance: () -> Unit,
    thumbnail: @Composable () -> Unit,
    onTop: (Int) -> Unit,
    onBottom: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { onTop(it.height) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.graphicsLayer { rotationZ = state.upright }) {
                ModeSwitch(
                    mode = AppMode.CAMERA,
                    primary = Color.White,
                    secondary = Color(0xFFB7B7B2),
                    background = Color.White.copy(alpha = 0.08f),
                    onMode = onMode,
                )
            }
            ToolPair(state, onToggleFlash, onOpenSettings)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { onBottom(it.height) }
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.guidance != null) {
                GuidanceBanner(state.guidance, onGuidance, Modifier.padding(bottom = 10.dp))
            }
            if (state.zoomVisible) {
                Box(Modifier.graphicsLayer { rotationZ = state.upright }) {
                    ZoomControls(
                        ratio = state.zoomRatio,
                        min = state.zoomMin,
                        max = state.zoomMax,
                        linear = state.linearZoom,
                        onRatio = onZoomRatio,
                        onLinear = onLinearZoom,
                        showSlider = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.graphicsLayer { rotationZ = state.upright }) {
                StyleChoices(state.style, onStyle, vertical = false)
            }
            if (state.style == WatermarkStyle.CAPTION) {
                Spacer(Modifier.height(8.dp))
                AnchorChoices(state.anchor, onAnchor, vertical = false)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { thumbnail() }
                ShutterButton(state, onShutter)
                LensSlot(state, onToggleLens)
            }
        }
    }
}

@Composable
private fun CaptureRail(
    state: CameraChromeState,
    onToggleLens: () -> Unit,
    onShutter: () -> Unit,
    thumbnail: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LensSlot(state, onToggleLens)
        ShutterButton(state, onShutter)
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { thumbnail() }
    }
}

@Composable
private fun UtilityRail(
    state: CameraChromeState,
    onMode: (AppMode) -> Unit,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    verticalMode: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.graphicsLayer { rotationZ = state.upright }) {
                ModeSwitch(
                    mode = AppMode.CAMERA,
                    primary = Color.White,
                    secondary = Color(0xFFB7B7B2),
                    background = Color.White.copy(alpha = 0.08f),
                    onMode = onMode,
                    vertical = verticalMode,
                )
            }
            StyleChoices(state.style, onStyle, vertical = true)
            if (state.style == WatermarkStyle.CAPTION) {
                AnchorChoices(state.anchor, onAnchor, vertical = true)
            }
            ToolPair(state, onToggleFlash, onOpenSettings, stacked = true)
        }
    }
}

@Composable
private fun ToolPair(
    state: CameraChromeState,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    stacked: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        if (state.showScreenFlash) {
            val description = stringResource(
                if (state.screenFlashOn) R.string.cd_screen_flash_on else R.string.cd_screen_flash_off,
            )
            CircleTool(
                description = description,
                emphasized = state.screenFlashOn,
                rotation = state.upright,
                enabled = !state.busy,
                onClick = onToggleFlash,
            ) {
                Icon(
                    if (state.screenFlashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = null,
                    tint = if (state.screenFlashOn) Color.Black else Color(0xFFB7B7B2),
                )
            }
        }
        val settingsDescription = stringResource(R.string.cd_settings)
        CircleTool(
            description = settingsDescription,
            emphasized = false,
            rotation = state.upright,
            onClick = onOpenSettings,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color(0xFFB7B7B2))
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun CircleTool(
    description: String,
    emphasized: Boolean,
    rotation: Float,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer { rotationZ = rotation }
            .clip(CircleShape)
            .background(if (emphasized) Color.White else Color.White.copy(alpha = 0.08f))
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun ShutterButton(state: CameraChromeState, onShutter: () -> Unit) {
    val description = stringResource(R.string.cd_shutter)
    val scale = if (state.busy) 0.92f else 1f
    Box(
        modifier = Modifier
            .size(78.dp)
            .graphicsLayer {
                rotationZ = state.upright
                scaleX = scale
                scaleY = scale
            }
            .border(2.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (state.busy) Color(0xFFB9B9B4) else Color.White)
            .semantics { contentDescription = description }
            .clickable(
                enabled = state.cameraGranted && !state.busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onShutter() },
    )
}

@Composable
private fun LensSlot(state: CameraChromeState, onToggleLens: () -> Unit) {
    if (!state.showLensButton) {
        Spacer(Modifier.size(52.dp))
        return
    }
    val description = stringResource(
        if (state.lensFront) R.string.cd_switch_to_back else R.string.cd_switch_to_front,
    )
    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer { rotationZ = state.upright + state.lensSpin }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .semantics { contentDescription = description }
            .clickable(enabled = !state.busy, onClick = onToggleLens),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Cameraswitch, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun StyleChoices(
    selected: WatermarkStyle,
    onStyle: (WatermarkStyle) -> Unit,
    vertical: Boolean,
) {
    val chips: @Composable () -> Unit = {
        WatermarkStyle.entries.forEach { style ->
            StyleChip(stringResource(style.labelRes()), selected == style, fill = vertical) { onStyle(style) }
        }
    }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) { chips() }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) { chips() }
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, fill: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White,
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = if (fill) 12.sp else 13.sp,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = if (fill) 6.dp else 12.dp, vertical = if (fill) 6.dp else 7.dp),
    )
}

@Composable
private fun AnchorChoices(
    selected: CaptionAnchor,
    onAnchor: (CaptionAnchor) -> Unit,
    vertical: Boolean,
) {
    val chips: @Composable () -> Unit = {
        AnchorChip(stringResource(R.string.anchor_bottom), selected == CaptionAnchor.BOTTOM) {
            onAnchor(CaptionAnchor.BOTTOM)
        }
        AnchorChip(stringResource(R.string.anchor_top), selected == CaptionAnchor.TOP) {
            onAnchor(CaptionAnchor.TOP)
        }
    }
    if (vertical) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun AnchorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else Color(0xFFB7B7B2),
        fontFamily = Outfit,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun GuidanceBanner(
    guidance: LocationGuidanceCopy,
    onGuidance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(guidance.title),
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(guidance.action),
            color = Color.Black,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White)
                .clickable(onClick = onGuidance)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun CameraStageSample(
    front: Boolean,
    modifier: Modifier = Modifier,
    shutterOnEnd: Boolean = true,
    flashOn: Boolean = false,
) {
    CameraStage(
        state = CameraChromeState(
            cameraGranted = true,
            shutterOnEnd = shutterOnEnd,
            lensFront = front,
            showLensButton = true,
            showScreenFlash = front,
            screenFlashOn = flashOn && front,
            busy = false,
            upright = 0f,
            lensSpin = 0f,
            zoomVisible = true,
            zoomRatio = 2f,
            zoomMin = 0.5f,
            zoomMax = 8f,
            linearZoom = 0.35f,
            zoomAdjusting = false,
            style = WatermarkStyle.PLATE_LIGHT,
            anchor = CaptionAnchor.BOTTOM,
            hasPlace = false,
            guidance = null,
            saveMessage = null,
        ),
        onMode = {},
        onStyle = {},
        onAnchor = {},
        onToggleLens = {},
        onToggleFlash = {},
        onOpenSettings = {},
        onShutter = {},
        onZoomRatio = {},
        onLinearZoom = {},
        onGuidance = {},
        onRequestCamera = {},
        thumbnail = {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF8AA0B8)),
            )
        },
        photo = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF152033), Color(0xFF4C6E8A), Color(0xFF101820)),
                        ),
                    ),
            )
        },
        plate = {
            Box(Modifier.fillMaxSize().background(Color.White))
        },
        modifier = modifier,
    )
}

@Composable
private fun PermissionPrompt(onRequestCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.camera_permission_title),
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.camera_permission_body),
            color = Color(0xFFB7B7B2),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.camera_permission_action),
            color = Color.Black,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White)
                .clickable(onClick = onRequestCamera)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}
