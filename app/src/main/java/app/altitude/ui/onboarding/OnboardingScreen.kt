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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Box(Modifier.fillMaxSize().background(Ink).padding(horizontal = 28.dp, vertical = 32.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                when (page) {
                    0 -> Page(
                        kicker = "亮屏",
                        title = "举到镜头前面",
                        body = "在山口、垭口、营地把屏幕亮着。海拔用大字停在画面中央，朋友用另一台手机拍你，数字依然清楚。",
                        showSample = true,
                    )
                    1 -> Page(
                        kicker = "海平面",
                        title = "不是椭球高度",
                        body = "卫星给出的是地球椭球上的高度。地图和路牌上的海拔相对海平面。手机会换算过来；有气压计时，读数更稳，反应也更快。不需要网络。",
                        showSample = false,
                    )
                    else -> Page(
                        kicker = "成片",
                        title = "写进照片，而不是贴在截图上",
                        body = "拍照时，海拔、经纬度和时间按底栏水印画进成片，和取景时看到的是同一套排版，并写入照片的位置信息。相册目录是 Pictures/Altitude。",
                        showSample = false,
                    )
                }
            }
            when (step) {
                0 -> PrimaryButton("继续") { step = 1 }
                1 -> {
                    PrimaryButton("允许定位") {
                        onAllowLocation()
                        step = 2
                    }
                    Spacer(Modifier.height(10.dp))
                    QuietButton("稍后") { step = 2 }
                }
                else -> {
                    PrimaryButton("允许相机") {
                        onAllowCamera()
                        onFinish()
                    }
                    Spacer(Modifier.height(10.dp))
                    QuietButton("先只用亮屏") { onFinish() }
                }
            }
        }
    }
}

@Composable
private fun Page(kicker: String, title: String, body: String, showSample: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(kicker, color = Red, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = Paper, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp)
        Spacer(Modifier.height(16.dp))
        Text(body, color = Mute, fontSize = 16.sp, lineHeight = 24.sp)
        if (showSample) {
            Spacer(Modifier.height(36.dp))
            Text("5,210", color = Paper, fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 84.sp, letterSpacing = (-2).sp)
            Text("米  ·  海平面", color = Mute, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 1.sp)
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
            .padding(vertical = 16.dp),
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
            .padding(vertical = 12.dp),
    )
}
