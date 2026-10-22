package app.altitude.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.altitude.R
import app.altitude.domain.AltitudeUnit
import app.altitude.i18n.rememberLocalFormat
import app.altitude.i18n.symbolRes
import app.altitude.ui.theme.Outfit

private val Ink = Color(0xFF090A0C)
private val Paper = Color(0xFFF3F0E8)
private val Mute = Color(0xFF9A958C)
private val Red = Color(0xFFE10600)

@Composable
fun OnboardingScreen(
    onAllowLocation: () -> Unit,
    onAllowCamera: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val format = rememberLocalFormat()
    Box(Modifier.fillMaxSize().background(Ink).padding(horizontal = 28.dp, vertical = 32.dp)) {
        Column(Modifier.fillMaxSize()) {
            val stepDescription = stringResource(R.string.cd_onboarding_step, step + 1, 3)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics { contentDescription = stepDescription },
            ) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .size(if (index == step) 22.dp else 7.dp, 7.dp)
                            .clip(CircleShape)
                            .background(if (index == step) Red else Color.White.copy(alpha = 0.2f)),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val direction = if (forward) 1 else -1
                    (slideInHorizontally { direction * it / 5 } + fadeIn())
                        .togetherWith(slideOutHorizontally { -direction * it / 5 } + fadeOut())
                },
                label = "onboarding",
                modifier = Modifier.weight(1f),
            ) { page ->
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (page) {
                        0 -> Page(
                            kicker = stringResource(R.string.onboarding_display_kicker),
                            title = stringResource(R.string.onboarding_display_title),
                            body = stringResource(R.string.onboarding_display_body),
                            sampleNumber = format.altitudeNumber(5_210.0, AltitudeUnit.METERS),
                            sampleCaption = stringResource(
                                R.string.onboarding_sample_caption,
                                stringResource(AltitudeUnit.METERS.symbolRes()),
                            ),
                        )
                        1 -> Page(
                            kicker = stringResource(R.string.onboarding_msl_kicker),
                            title = stringResource(R.string.onboarding_msl_title),
                            body = stringResource(R.string.onboarding_msl_body),
                            sampleNumber = null,
                            sampleCaption = null,
                        )
                        else -> Page(
                            kicker = stringResource(R.string.onboarding_photo_kicker),
                            title = stringResource(R.string.onboarding_photo_title),
                            body = stringResource(R.string.onboarding_photo_body),
                            sampleNumber = null,
                            sampleCaption = null,
                        )
                    }
                }
            }
            when (step) {
                0 -> PrimaryButton(stringResource(R.string.onboarding_continue)) { step = 1 }
                1 -> {
                    PrimaryButton(stringResource(R.string.onboarding_allow_location)) {
                        onAllowLocation()
                        step = 2
                    }
                    Spacer(Modifier.height(10.dp))
                    QuietButton(stringResource(R.string.onboarding_later)) { step = 2 }
                }
                else -> {
                    PrimaryButton(stringResource(R.string.onboarding_allow_camera)) {
                        onAllowCamera()
                        onFinish()
                    }
                    Spacer(Modifier.height(10.dp))
                    QuietButton(stringResource(R.string.onboarding_display_only)) { onFinish() }
                }
            }
        }
    }
}

@Composable
private fun Page(
    kicker: String,
    title: String,
    body: String,
    sampleNumber: String?,
    sampleCaption: String?,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(kicker, color = Red, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = Paper, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp)
        Spacer(Modifier.height(16.dp))
        Text(body, color = Mute, fontSize = 16.sp, lineHeight = 24.sp)
        if (sampleNumber != null && sampleCaption != null) {
            Spacer(Modifier.height(36.dp))
            Text(
                sampleNumber,
                color = Paper,
                fontFamily = Outfit,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 84.sp,
                letterSpacing = (-2).sp,
                maxLines = 1,
                softWrap = false,
            )
            Text(sampleCaption, color = Mute, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.4.sp)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Ink,
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
    )
}

@Composable
private fun QuietButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Mute,
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    )
}
