package app.altitude.ui.display

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.altitude.domain.AltitudeSource
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.AppMode
import app.altitude.domain.formatAccuracy
import app.altitude.domain.formatAltitudeNumber
import app.altitude.domain.formatAltitudeReading
import app.altitude.domain.formatCoordinatePair
import app.altitude.domain.formatDisplayClock
import app.altitude.domain.groupThousands
import app.altitude.ui.AltitudeUiState
import app.altitude.ui.theme.Outfit
import app.altitude.ui.theme.dot
import app.altitude.ui.theme.palette
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DisplayScreen(
    state: AltitudeUiState,
    onMode: (AppMode) -> Unit,
    onToggleUnit: () -> Unit,
    onToggleBrightness: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    settingsOpen: Boolean,
) {
    val palette = state.prefs.theme.palette()
    var chrome by remember { mutableStateOf(true) }
    var token by remember { mutableIntStateOf(0) }
    LaunchedEffect(token, settingsOpen) {
        if (!settingsOpen) {
            kotlinx.coroutines.delay(4_600)
            chrome = false
        }
    }
    val reveal = {
        chrome = true
        token += 1
    }
    val reading = state.reading
    val meters = reading.altitudeMslMeters

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(token) { detectTapGestures(onTap = { reveal() }) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 28.dp, bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "ALTITUDE",
                color = palette.secondary,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.weight(1f))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val sample = when {
                    meters == null -> "—"
                    else -> formatAltitudeNumber(meters, state.prefs.unit)
                }
                val fontSize = min(
                    maxWidth.value / (sample.length.coerceAtLeast(1) * 0.70f),
                    maxHeight.value.coerceAtLeast(180f) * 0.92f,
                ).coerceIn(72f, 196f).sp
                HeroNumber(
                    meters = meters,
                    unit = state.prefs.unit,
                    color = palette.primary,
                    fontSize = fontSize,
                    onTap = { reveal() },
                    onToggleUnit = {
                        reveal()
                        onToggleUnit()
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    meters == null -> " "
                    reading.absoluteMsl && state.prefs.unit == AltitudeUnit.FEET -> "英尺  ·  海平面"
                    reading.absoluteMsl -> "米  ·  海平面"
                    else -> "气压高度  ·  尚未用卫星校准"
                },
                color = if (reading.absoluteMsl || meters == null) palette.secondary else palette.accent,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.weight(1f))
            if (!state.locationGranted) {
                Prompt(
                    title = "还没有定位权限",
                    body = "海拔离不开卫星。授权之后，数字会开始跟着你走。",
                    action = "允许定位",
                    onAction = onRequestLocation,
                    primary = palette.primary,
                    secondary = palette.secondary,
                )
            } else if (!state.locationEnabled) {
                Prompt(
                    title = "系统定位关着",
                    body = "打开定位服务，卫星高度才会进来。",
                    action = "去打开",
                    onAction = onOpenLocationSettings,
                    primary = palette.primary,
                    secondary = palette.secondary,
                )
            } else if (meters == null) {
                Searching(state, palette.primary, palette.secondary)
            } else {
                MetaBlock(state, palette.primary, palette.secondary)
            }
            Spacer(Modifier.height(12.dp))
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(420, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { reveal(); onToggleBrightness() }) {
                    Icon(
                        Icons.Outlined.WbSunny,
                        contentDescription = if (state.prefs.maxBrightness) "关闭最高亮度" else "最高亮度",
                        tint = if (state.prefs.maxBrightness) palette.accent else palette.secondary,
                    )
                }
                Text(
                    text = if (state.prefs.unit == AltitudeUnit.METERS) "米" else "英尺",
                    color = palette.primary,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { reveal(); onToggleUnit() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                IconButton(onClick = { reveal(); onOpenSettings() }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = palette.secondary)
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(220)) + slideInVertically { it / 3 },
            exit = fadeOut(tween(420)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        ) {
            ModeSwitch(
                mode = AppMode.DISPLAY,
                primary = palette.primary,
                secondary = palette.secondary,
                background = palette.primary.copy(alpha = 0.08f),
                onMode = {
                    reveal()
                    onMode(it)
                },
            )
        }
    }
}

@Composable
private fun HeroNumber(
    meters: Double?,
    unit: AltitudeUnit,
    color: androidx.compose.ui.graphics.Color,
    fontSize: TextUnit,
    onTap: () -> Unit,
    onToggleUnit: () -> Unit,
) {
    val anim = remember { Animatable(0f) }
    var primed by remember { mutableStateOf(false) }
    LaunchedEffect(meters) {
        val value = meters?.toFloat() ?: return@LaunchedEffect
        if (!primed) {
            anim.snapTo(value)
            primed = true
        } else {
            anim.animateTo(value, spring(dampingRatio = 0.9f, stiffness = 140f))
        }
    }
    val shown = if (meters != null && primed) groupThousands(anim.value.roundToInt().toLong()) else "—"
    val description = if (meters == null) {
        "正在等待海拔"
    } else {
        "海拔 ${formatAltitudeReading(meters, unit)}"
    }
    Box(
        modifier = Modifier
            .semantics { contentDescription = description }
            .pointerInput(unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onToggleUnit() },
                )
            },
    ) {
        RollingDigits(text = shown, color = color, fontSize = fontSize)
    }
}

@Composable
private fun RollingDigits(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontSize: TextUnit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, character ->
            if (character.isDigit()) {
                AnimatedContent(
                    targetState = character,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val direction = if (forward) 1 else -1
                        (slideInVertically { direction * it } + fadeIn(tween(160)))
                            .togetherWith(slideOutVertically { -direction * it } + fadeOut(tween(140)))
                    },
                    label = "digit-$index",
                ) { digit ->
                    Digit(digit.toString(), color, fontSize)
                }
            } else {
                Digit(character.toString(), color, fontSize)
            }
        }
    }
}

@Composable
private fun Digit(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontSize: TextUnit,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontFamily = Outfit,
        fontWeight = FontWeight.ExtraBold,
        style = TextStyle(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center,
        letterSpacing = (-1.5).sp,
    )
}

@Composable
private fun MetaBlock(
    state: AltitudeUiState,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
) {
    val reading = state.reading
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.prefs.showAccuracy && reading.accuracyMeters != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(reading.quality.dot()),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatAccuracy(reading.accuracyMeters, state.prefs.unit) + "   " + reading.source.shortLabel(),
                    color = secondary,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = 0.6.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        if (state.prefs.showCoordinates && state.latitude != null && state.longitude != null) {
            Text(
                text = formatCoordinatePair(state.latitude, state.longitude),
                color = primary.copy(alpha = 0.88f),
                fontFamily = Outfit,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                letterSpacing = 0.4.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (state.prefs.showPlace && !state.placeName.isNullOrBlank()) {
            Text(
                text = state.placeName,
                color = secondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (state.prefs.showClock) {
            Text(
                text = formatDisplayClock(state.nowMillis),
                color = secondary,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun Searching(
    state: AltitudeUiState,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
) {
    val pulse = rememberInfiniteTransition(label = "search")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "search-alpha",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (state.geoidReady) "正在寻找卫星" else "正在准备海拔模型",
            color = primary,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier.alpha(alpha),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "走到空旷处，把天空让给天线。第一颗星有时要一分钟。",
            color = secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
        val known = state.lastKnown
        if (known != null) {
            val age = abs(state.nowMillis - known.timeMs)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "上次 ${formatAltitudeReading(known.altitudeMeters, state.prefs.unit)} · ${relativeAge(age)}",
                color = secondary,
                fontFamily = Outfit,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Prompt(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = primary, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = secondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = action,
            color = primary,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(primary.copy(alpha = 0.1f))
                .clickable(onClick = onAction)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun ModeSwitch(
    mode: AppMode,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    onMode: (AppMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwitchChip("亮屏", mode == AppMode.DISPLAY, primary, secondary) { onMode(AppMode.DISPLAY) }
        SwitchChip("相机", mode == AppMode.CAMERA, primary, secondary) { onMode(AppMode.CAMERA) }
    }
}

@Composable
private fun SwitchChip(
    label: String,
    selected: Boolean,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) primary else secondary,
        fontFamily = Outfit,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun IconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun AltitudeSource.shortLabel(): String = when (this) {
    AltitudeSource.GPS -> "卫星"
    AltitudeSource.FUSED -> "卫星 + 气压"
    AltitudeSource.PRESSURE_HOLDOVER -> "气压延续"
    AltitudeSource.PRESSURE_UNCALIBRATED -> "气压未校准"
    AltitudeSource.NONE -> "无信号"
}

private fun relativeAge(ageMs: Long): String {
    val minutes = ageMs / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}
