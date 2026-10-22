package app.altitude.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.altitude.R
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.altitude.domain.AppMode
import app.altitude.ui.camera.CameraScreen
import app.altitude.ui.display.DisplayScreen
import app.altitude.ui.onboarding.OnboardingScreen
import app.altitude.ui.settings.SettingsSheet

@Composable
fun AltitudeRoot(viewModel: AltitudeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsOpen = state.settingsOpen

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForeground(true)
                Lifecycle.Event.ON_STOP -> viewModel.onForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshSystemState() }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshSystemState() }
    val requestLocation = {
        locationLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }
    val requestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) }

    DisposableEffect(state.prefs.maxBrightness, state.prefs.theme, state.mode, state.prefs.onboardingComplete) {
        val activity = context as Activity
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val attributes = window.attributes
        attributes.screenBrightness = if (state.prefs.maxBrightness && state.prefs.onboardingComplete) {
            1f
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attributes
        val controller = WindowCompat.getInsetsController(window, view)
        val light = state.prefs.onboardingComplete &&
            state.mode == AppMode.DISPLAY &&
            state.prefs.theme.lightBars
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF090A0C))) {
        if (!state.prefs.onboardingComplete) {
            OnboardingScreen(
                onAllowLocation = requestLocation,
                onAllowCamera = requestCamera,
                onFinish = viewModel::completeOnboarding,
            )
        } else {
            AnimatedContent(
                targetState = state.mode,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "mode",
            ) { mode ->
                when (mode) {
                    AppMode.DISPLAY -> DisplayScreen(
                        state = state,
                        onMode = viewModel::setMode,
                        onToggleUnit = viewModel::toggleUnit,
                        onToggleBrightness = { viewModel.setMaxBrightness(!state.prefs.maxBrightness) },
                        onOpenSettings = { viewModel.setSettingsOpen(true) },
                        onRequestLocation = requestLocation,
                        onOpenLocationSettings = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        settingsOpen = settingsOpen,
                    )
                    AppMode.CAMERA -> CameraScreen(
                        state = state,
                        onMode = viewModel::setMode,
                        onStyle = viewModel::setWatermarkStyle,
                        onAnchor = viewModel::setCaptionAnchor,
                        onOpenSettings = { viewModel.setSettingsOpen(true) },
                        onRequestCamera = requestCamera,
                        onSaved = viewModel::reportSave,
                    )
                }
            }
        }

        val closeSettings = stringResource(R.string.cd_close_settings)
        AnimatedVisibility(
            visible = settingsOpen && state.prefs.onboardingComplete,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.46f))
                    .clickable { viewModel.setSettingsOpen(false) }
                    .semantics { contentDescription = closeSettings },
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(Color(0xFF141311))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .navigationBarsPadding()
                        .padding(top = 18.dp),
                ) {
                    SettingsSheet(state.prefs, viewModel)
                }
            }
        }
    }
}
