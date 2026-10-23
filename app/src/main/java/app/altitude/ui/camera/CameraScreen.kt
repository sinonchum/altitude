package app.altitude.ui.camera

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Rational
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ZoomState
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.ScreenFlashView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.Image
import app.altitude.R
import app.altitude.domain.AppMode
import app.altitude.domain.CameraLens
import app.altitude.domain.CameraLenses
import app.altitude.domain.CameraStageMetrics
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.CaptureOrientation
import app.altitude.domain.DevicePose
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
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen(
    state: AltitudeUiState,
    onMode: (AppMode) -> Unit,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onFrontCamera: (Boolean) -> Unit,
    onScreenFlash: (Boolean) -> Unit,
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
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var autofocus by remember { mutableStateOf(false) }
    val screenFlashView = remember {
        ScreenFlashView(context).apply {
            isClickable = false
            isFocusable = false
        }
    }
    var backLens by remember { mutableStateOf<Boolean?>(null) }
    var frontLens by remember { mutableStateOf<Boolean?>(null) }
    val preferredLens = if (state.prefs.frontCamera) CameraLens.FRONT else CameraLens.BACK
    val lens = if (backLens == null || frontLens == null) {
        preferredLens
    } else {
        CameraLenses.resolve(preferredLens, backLens == true, frontLens == true)
    }
    val showLensButton = CameraLenses.buttonVisible(backLens == true, frontLens == true)
    var lensTurns by remember { mutableFloatStateOf(0f) }
    val lensSpin by animateFloatAsState(lensTurns, tween(420), label = "lens")
    val lensCover = remember { Animatable(0f) }
    var lensCoverArmed by remember { mutableStateOf(false) }
    LaunchedEffect(lens) {
        if (!lensCoverArmed) {
            lensCoverArmed = true
            return@LaunchedEffect
        }
        lensCover.snapTo(0.72f)
        lensCover.animateTo(0f, tween(320))
    }
    val view = LocalView.current
    var deviceDegrees by remember { mutableIntStateOf(CaptureOrientation.UNKNOWN) }
    DisposableEffect(context) {
        var snapped = CaptureOrientation.UNKNOWN
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val next = CaptureOrientation.snapDegrees(snapped, orientation)
                if (next != snapped) {
                    snapped = next
                    deviceDegrees = next
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    val activity = context as? Activity
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER }
    }
    LaunchedEffect(deviceDegrees) {
        if (deviceDegrees < 0) return@LaunchedEffect
        activity?.requestedOrientation = when (CaptureOrientation.pose(deviceDegrees)) {
            DevicePose.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DevicePose.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            DevicePose.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            DevicePose.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }
    val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
    val captureRotation = if (deviceDegrees < 0) displayRotation else CaptureOrientation.surfaceRotation(deviceDegrees)
    val upright by animateFloatAsState(
        CaptureOrientation.iconOffsetDegrees(if (deviceDegrees < 0) 0 else deviceDegrees, displayRotation).toFloat(),
        tween(280),
        label = "upright",
    )
    val useScreenFlash = lens == CameraLens.FRONT && state.prefs.screenFlash
    SideEffect {
        imageCapture.targetRotation = captureRotation
        val window = activity?.window
        if (window != null) screenFlashView.setScreenFlashWindow(window)
        if (useScreenFlash) {
            imageCapture.screenFlash = screenFlashView.screenFlash
            imageCapture.flashMode = ImageCapture.FLASH_MODE_SCREEN
        } else {
            imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
            imageCapture.screenFlash = null
        }
    }
    LaunchedEffect(boundCamera) {
        autofocus = boundCamera?.let { supportsAutofocus(it) } ?: false
    }
    var zoomMin by remember { mutableFloatStateOf(1f) }
    var zoomMax by remember { mutableFloatStateOf(1f) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var zoomHud by remember { mutableStateOf(false) }
    var zoomHudToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(zoomHudToken) {
        if (zoomHudToken == 0) return@LaunchedEffect
        zoomHud = true
        kotlinx.coroutines.delay(1_000)
        zoomHud = false
    }
    DisposableEffect(boundCamera) {
        val live = boundCamera?.cameraInfo?.zoomState
        if (live == null) {
            onDispose { }
        } else {
            val observer = Observer<ZoomState> { reading ->
                zoomMin = reading.minZoomRatio
                zoomMax = reading.maxZoomRatio
                zoomRatio = reading.zoomRatio
                linearZoom = reading.linearZoom
            }
            live.observeForever(observer)
            onDispose { live.removeObserver(observer) }
        }
    }

    val latestPhoto = state.photos.firstOrNull()
    val guidance = state.locationGuidance()
    val chrome = CameraChromeState(
        cameraGranted = state.cameraGranted,
        shutterOnEnd = CameraStageMetrics.shutterOnEnd(displayRotation),
        lensFront = lens == CameraLens.FRONT,
        showLensButton = showLensButton,
        showScreenFlash = lens == CameraLens.FRONT,
        screenFlashOn = state.prefs.screenFlash,
        busy = busy,
        upright = upright,
        lensSpin = lensSpin,
        zoomVisible = boundCamera != null && zoomMax > zoomMin,
        zoomRatio = zoomRatio,
        zoomMin = zoomMin,
        zoomMax = zoomMax,
        linearZoom = linearZoom,
        zoomAdjusting = zoomHud,
        style = state.prefs.watermarkStyle,
        anchor = state.prefs.captionAnchor,
        hasPlace = content.place != null,
        guidance = guidance,
        saveMessage = if (state.galleryOpen) null else state.saveMessage,
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraStage(
            state = chrome,
            onMode = onMode,
            onStyle = onStyle,
            onAnchor = onAnchor,
            onToggleLens = {
                lensTurns += 180f
                onFrontCamera(lens != CameraLens.FRONT)
            },
            onToggleFlash = { onScreenFlash(!state.prefs.screenFlash) },
            onOpenSettings = onOpenSettings,
            onShutter = {
                context.captureTick()
                imageCapture.targetRotation = captureRotation
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
            onZoomRatio = { next ->
                zoomRatio = next
                boundCamera?.cameraControl?.setZoomRatio(next)
                zoomHudToken += 1
            },
            onLinearZoom = { value ->
                linearZoom = value
                boundCamera?.cameraControl?.setLinearZoom(value)
                zoomHudToken += 1
            },
            onGuidance = {
                if (guidance?.opensLocationSettings == true) onOpenLocationSettings() else onRequestLocation()
            },
            onRequestCamera = onRequestCamera,
            thumbnail = {
                if (latestPhoto != null) {
                    LastShot(uri = latestPhoto, onClick = onOpenGallery)
                }
            },
            photo = { fit ->
                if (state.cameraGranted) {
                    BindCamera(
                        previewView = previewView,
                        imageCapture = imageCapture,
                        front = lens == CameraLens.FRONT,
                        rotation = captureRotation,
                        lifecycleOwner = LocalLifecycleOwner.current,
                        onError = { cameraError = it },
                        onCamera = { boundCamera = it },
                        onLenses = { back, front ->
                            backLens = back
                            frontLens = front
                        },
                    )
                }
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                AndroidView(factory = { screenFlashView }, modifier = Modifier.fillMaxSize())
                if (state.prefs.watermarkStyle == WatermarkStyle.CAPTION) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { SignatureView(it) },
                        update = { view ->
                            view.mode = SignatureMode.CAPTION
                            view.anchor = state.prefs.captionAnchor
                            view.content = content
                            view.metricPx = min(fit.photoWidth, fit.photoHeight)
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
                PreviewGestures(
                    previewView = previewView,
                    camera = boundCamera,
                    autofocus = autofocus,
                    zoomRatio = zoomRatio,
                    zoomMin = zoomMin,
                    zoomMax = zoomMax,
                    onZoomRatio = { next ->
                        zoomRatio = next
                        boundCamera?.cameraControl?.setZoomRatio(next)
                    },
                    onPinch = { zoomHudToken += 1 },
                )
                if (zoomHud) {
                    ZoomReadout(ratio = zoomRatio, modifier = Modifier.align(Alignment.Center))
                }
                if (lensCover.value > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = lensCover.value)),
                    )
                }
            },
            plate = { fit ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { SignatureView(it) },
                    update = { view ->
                        view.mode = SignatureMode.PLATE
                        view.dark = state.prefs.watermarkStyle == WatermarkStyle.PLATE_DARK
                        view.content = content
                        view.metricPx = min(fit.photoWidth, fit.photoHeight)
                        view.invalidate()
                    },
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
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
private fun BindCamera(
    previewView: PreviewView,
    imageCapture: ImageCapture,
    front: Boolean,
    rotation: Int,
    lifecycleOwner: LifecycleOwner,
    onError: (Int?) -> Unit,
    onCamera: (Camera?) -> Unit,
    onLenses: (Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val currentOnCamera by rememberUpdatedState(onCamera)
    val currentOnLenses by rememberUpdatedState(onLenses)
    val currentOnError by rememberUpdatedState(onError)
    DisposableEffect(previewView, imageCapture, lifecycleOwner, front, rotation) {
        var disposed = false
        var providerRef: ProcessCameraProvider? = null
        var boundWidth = -1
        var boundHeight = -1
        var boundFront = front
        var boundRotation = rotation
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val bind = Runnable {
            if (disposed) return@Runnable
            val width = previewView.width
            val height = previewView.height
            if (width == 0 || height == 0) return@Runnable
            if (
                width == boundWidth &&
                height == boundHeight &&
                boundFront == front &&
                boundRotation == rotation &&
                providerRef != null
            ) {
                return@Runnable
            }
            try {
                val provider = future.get()
                providerRef = provider
                val back = runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
                val frontAvailable = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
                currentOnLenses(back, frontAvailable)
                val useFront = (front && frontAvailable) || (!back && frontAvailable)
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                imageCapture.targetRotation = rotation
                preview.targetRotation = rotation
                provider.unbindAll()
                val builder = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture)
                builder.setViewPort(ViewPort.Builder(Rational(width, height), rotation).build())
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                    builder.build(),
                )
                currentOnCamera(camera)
                boundWidth = width
                boundHeight = height
                boundFront = front
                boundRotation = rotation
                currentOnError(null)
            } catch (_: Throwable) {
                currentOnCamera(null)
                currentOnError(R.string.error_camera_open)
            }
        }
        val layout = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            previewView.post(bind)
        }
        previewView.addOnLayoutChangeListener(layout)
        future.addListener({ previewView.post(bind) }, executor)
        onDispose {
            disposed = true
            currentOnCamera(null)
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
