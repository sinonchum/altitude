package app.altitude.ui.camera

import android.util.Rational
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.Image
import app.altitude.R
import app.altitude.domain.AppMode
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.WatermarkMetrics
import app.altitude.domain.WatermarkStyle
import app.altitude.gallery.decodeAlbumBitmap
import app.altitude.i18n.labelRes
import app.altitude.i18n.rememberLocalFormat
import app.altitude.ui.AltitudeUiState
import app.altitude.ui.display.ModeSwitch
import app.altitude.ui.display.locationGuidance
import app.altitude.ui.theme.Outfit
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen(
    state: AltitudeUiState,
    onMode: (AppMode) -> Unit,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onCloseGallery: () -> Unit,
    onDeletePhoto: (String) -> Boolean,
    onRefreshAlbum: () -> Unit,
    onSaved: (Int, String?) -> Unit,
) {
    val context = LocalContext.current
    val format = rememberLocalFormat()
    val pipeline = remember { PhotoPipeline(context) }
    val content = format.watermark(
        altitudeMeters = state.reading.altitudeMslMeters,
        absoluteMsl = state.reading.absoluteMsl,
        unit = state.prefs.unit,
        latitude = state.latitude,
        longitude = state.longitude,
        place = if (state.prefs.showPlace) state.placeName else null,
        epochMs = state.nowMillis,
    )
    var cameraError by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(95)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    val settingsDescription = stringResource(R.string.cd_settings)
    val latestPhoto = state.photos.firstOrNull()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeSwitch(
                    mode = AppMode.CAMERA,
                    primary = Color.White,
                    secondary = Color(0xFFB7B7B2),
                    background = Color.White.copy(alpha = 0.08f),
                    onMode = onMode,
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .semantics { contentDescription = settingsDescription }
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = Color(0xFFB7B7B2),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
        if (!state.cameraGranted) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.camera_permission_title),
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.camera_permission_body),
                    color = Color(0xFFB7B7B2),
                    fontSize = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val density = LocalDensity.current
                val portrait = maxHeight > maxWidth
                val aspect = if (portrait) 3f / 4f else 4f / 3f
                val fit = with(density) {
                    WatermarkMetrics.fitFrame(
                        availWidth = maxWidth.toPx(),
                        availHeight = maxHeight.toPx(),
                        photoAspect = aspect,
                        style = state.prefs.watermarkStyle,
                        hasPlace = content.place != null,
                    )
                }
                val photoW = with(density) { fit.photoWidth.toDp() }
                val photoH = with(density) { fit.photoHeight.toDp() }
                val side = with(density) { fit.side.toDp() }
                val top = with(density) { fit.top.toDp() }
                val bottom = with(density) { fit.bottom.toDp() }
                val frameColor = if (state.prefs.watermarkStyle == WatermarkStyle.PLATE_DARK) Color(0xFF0A0A0A) else Color.White
                BindCamera(
                    previewView = previewView,
                    imageCapture = imageCapture,
                    lifecycleOwner = LocalLifecycleOwner.current,
                    onError = { cameraError = it },
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(photoW + side * 2)
                        .background(if (state.prefs.watermarkStyle == WatermarkStyle.CAPTION) Color.Black else frameColor),
                ) {
                    if (state.prefs.watermarkStyle == WatermarkStyle.MOUNT) {
                        Spacer(Modifier.height(top).fillMaxWidth())
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = side)
                            .size(photoW, photoH)
                            .background(Color.Black),
                    ) {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                        if (state.prefs.watermarkStyle == WatermarkStyle.CAPTION) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { SignatureView(it) },
                                update = { view ->
                                    view.mode = SignatureMode.CAPTION
                                    view.anchor = state.prefs.captionAnchor
                                    view.content = content
                                    view.invalidate()
                                },
                            )
                        }
                        cameraError?.let { messageRes ->
                            Text(
                                text = stringResource(messageRes),
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    if (state.prefs.watermarkStyle != WatermarkStyle.CAPTION) {
                        AndroidView(
                            modifier = Modifier
                                .padding(horizontal = side)
                                .width(photoW)
                                .height(bottom),
                            factory = { SignatureView(it) },
                            update = { view ->
                                view.mode = SignatureMode.PLATE
                                view.dark = state.prefs.watermarkStyle == WatermarkStyle.PLATE_DARK
                                view.content = content
                                view.invalidate()
                            },
                        )
                    }
                }
            }
        }
                val message = state.saveMessage
                if (message != null && !state.galleryOpen) {
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
            }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val guidance = state.locationGuidance()
            if (guidance != null) {
                Text(
                    text = stringResource(guidance.title),
                    color = Color.White,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
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
                        .clickable {
                            if (guidance.opensLocationSettings) onOpenLocationSettings() else onRequestLocation()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            StyleRow(state.prefs.watermarkStyle, onStyle)
            if (state.prefs.watermarkStyle == WatermarkStyle.CAPTION) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnchorChip(
                        stringResource(R.string.anchor_bottom),
                        state.prefs.captionAnchor == CaptionAnchor.BOTTOM,
                    ) { onAnchor(CaptionAnchor.BOTTOM) }
                    AnchorChip(
                        stringResource(R.string.anchor_top),
                        state.prefs.captionAnchor == CaptionAnchor.TOP,
                    ) { onAnchor(CaptionAnchor.TOP) }
                }
            }
            Spacer(Modifier.height(14.dp))
            val scale = if (busy) 0.92f else 1f
            val shutterDescription = stringResource(R.string.cd_shutter)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (latestPhoto != null) {
                    LastShot(uri = latestPhoto, onClick = onOpenGallery)
                } else {
                    Spacer(Modifier.size(52.dp))
                }
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .border(2.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (busy) Color(0xFFB9B9B4) else Color.White)
                        .semantics { contentDescription = shutterDescription }
                        .clickable(
                            enabled = state.cameraGranted && !busy,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            context.captureTick()
                            imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                            busy = true
                            scope.launch {
                                val captured = runCatching {
                                    pipeline.capture(
                                        imageCapture = imageCapture,
                                        content = content,
                                        style = state.prefs.watermarkStyle,
                                        anchor = state.prefs.captionAnchor,
                                        latitude = state.latitude,
                                        longitude = state.longitude,
                                        altitudeMeters = state.reading.altitudeMslMeters?.takeIf { state.reading.absoluteMsl },
                                        epochMs = state.nowMillis,
                                    )
                                }
                                val message = captured.fold(
                                    onSuccess = { R.string.save_success },
                                    onFailure = { error ->
                                        (error as? CaptureException)?.messageRes ?: R.string.error_save_failed
                                    },
                                )
                                onSaved(message, captured.getOrNull())
                                busy = false
                            }
                        },
                )
                Spacer(Modifier.size(52.dp))
            }
        }
        }
        if (state.galleryOpen) {
            GalleryScreen(
                uris = state.photos,
                notice = state.saveMessage,
                onClose = onCloseGallery,
                onDelete = onDeletePhoto,
                onRefresh = onRefreshAlbum,
                onMessage = { onSaved(it, null) },
            )
        }
    }
}

@Composable
private fun LastShot(uri: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val description = stringResource(R.string.cd_last_photo)
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { decodeAlbumBitmap(context, Uri.parse(uri), 256) }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun StyleRow(selected: WatermarkStyle, onStyle: (WatermarkStyle) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        WatermarkStyle.entries.forEach { style ->
            StyleChip(stringResource(style.labelRes()), selected == style) { onStyle(style) }
        }
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White,
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun AnchorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else Color(0xFFB7B7B2),
        fontFamily = Outfit,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun BindCamera(
    previewView: PreviewView,
    imageCapture: ImageCapture,
    lifecycleOwner: LifecycleOwner,
    onError: (Int?) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(previewView, imageCapture, lifecycleOwner) {
        var disposed = false
        var providerRef: ProcessCameraProvider? = null
        var boundWidth = -1
        var boundHeight = -1
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val bind = Runnable {
            if (disposed) return@Runnable
            val width = previewView.width
            val height = previewView.height
            if (width == 0 || height == 0) return@Runnable
            if (width == boundWidth && height == boundHeight && providerRef != null) return@Runnable
            try {
                val provider = future.get()
                providerRef = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                imageCapture.targetRotation = rotation
                preview.targetRotation = rotation
                provider.unbindAll()
                val builder = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture)
                builder.setViewPort(ViewPort.Builder(Rational(width, height), rotation).build())
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, builder.build())
                boundWidth = width
                boundHeight = height
                onError(null)
            } catch (error: Throwable) {
                onError(R.string.error_camera_open)
            }
        }
        val layout = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            previewView.post(bind)
        }
        previewView.addOnLayoutChangeListener(layout)
        future.addListener({ previewView.post(bind) }, executor)
        onDispose {
            disposed = true
            previewView.removeOnLayoutChangeListener(layout)
            previewView.removeCallbacks(bind)
            providerRef?.unbindAll()
        }
    }
}

private fun Context.captureTick() {
    val vibrator = getSystemService(Vibrator::class.java) ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(18)
    }
}
