package app.altitude.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.DisplayTheme
import app.altitude.domain.PrefsSnapshot
import app.altitude.ui.AltitudeViewModel
import app.altitude.ui.theme.Outfit
import app.altitude.ui.theme.label
import app.altitude.ui.theme.palette

@Composable
fun SettingsSheet(prefs: PrefsSnapshot, viewModel: AltitudeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        Text("设置", color = Color(0xFFF3F0E8), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        Spacer(Modifier.height(18.dp))
        val accent = settingsAccent(prefs.dynamicColor)
        SectionLabel("单位")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("米", prefs.unit == AltitudeUnit.METERS, accent) { viewModel.setUnit(AltitudeUnit.METERS) }
            Choice("英尺", prefs.unit == AltitudeUnit.FEET, accent) { viewModel.setUnit(AltitudeUnit.FEET) }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("亮屏主题")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DisplayTheme.entries.forEach { theme ->
                val palette = theme.palette()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(palette.background)
                            .border(
                                width = if (prefs.theme == theme) 2.dp else 1.dp,
                                color = if (prefs.theme == theme) palette.accent else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape,
                            )
                            .clickable { viewModel.setTheme(theme) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(theme.label(), color = Color(0xFFB7B3AA), fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        ToggleRow("最高亮度", "拍照时把屏幕拉到最亮", prefs.maxBrightness, accent, viewModel::setMaxBrightness)
        ToggleRow("坐标", "亮屏时显示经纬度", prefs.showCoordinates, accent, viewModel::setShowCoordinates)
        ToggleRow("精度", "亮屏时显示误差和信号来源", prefs.showAccuracy, accent, viewModel::setShowAccuracy)
        ToggleRow("时间", "亮屏时显示当地时间", prefs.showClock, accent, viewModel::setShowClock)
        ToggleRow("地名", "能解析到时显示在亮屏和水印上", prefs.showPlace, accent, viewModel::setShowPlace)
        ToggleRow("界面跟随系统配色", "只给设置里的按钮上色，不改亮屏和水印", prefs.dynamicColor, accent, viewModel::setDynamicColor)
        Spacer(Modifier.height(8.dp))
        Text(
            "水印始终带上海拔、经纬度和时间。亮屏上的坐标可以单独关掉，避免拍屏幕时太满。",
            color = Color(0xFF8E8A82),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0xFF8E8A82), fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun settingsAccent(dynamic: Boolean): Color {
    if (!dynamic || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Color(0xFFF3F0E8)
    return dynamicDarkColorScheme(LocalContext.current).primary
}

@Composable
private fun Choice(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color(0xFF141210) else Color(0xFFF3F0E8),
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFFF3F0E8), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF8E8A82), fontSize = 12.sp)
        }
        Text(
            text = if (checked) "开" else "关",
            color = if (checked) Color(0xFF141210) else Color(0xFFB7B3AA),
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) accent else Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
