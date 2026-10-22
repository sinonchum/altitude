package app.altitude.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.altitude.BuildConfig
import app.altitude.R
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.DisplayTheme
import app.altitude.domain.PrefsSnapshot
import app.altitude.i18n.AppLanguage
import app.altitude.i18n.applyAppLanguage
import app.altitude.i18n.nameRes
import app.altitude.ui.AltitudeViewModel
import app.altitude.ui.theme.Outfit
import app.altitude.ui.theme.palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(prefs: PrefsSnapshot, viewModel: AltitudeViewModel) {
    val context = LocalContext.current
    val selectedLanguage = AppLanguage.current()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            color = Color(0xFFF3F0E8),
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(18.dp))
        val accent = settingsAccent(prefs.dynamicColor)
        SectionLabel(stringResource(R.string.settings_language))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { language ->
                Choice(stringResource(language.labelRes()), selectedLanguage == language, accent) {
                    if (selectedLanguage != language) {
                        applyAppLanguage(context, language)
                        viewModel.onAppLanguageChanged(language)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.settings_section_units))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(stringResource(R.string.unit_meters_name), prefs.unit == AltitudeUnit.METERS, accent) {
                viewModel.setUnit(AltitudeUnit.METERS)
            }
            Choice(stringResource(R.string.unit_feet_name), prefs.unit == AltitudeUnit.FEET, accent) {
                viewModel.setUnit(AltitudeUnit.FEET)
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.settings_section_theme))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DisplayTheme.entries.forEach { theme ->
                val palette = theme.palette()
                val themeName = stringResource(theme.nameRes())
                val themeDescription = stringResource(R.string.cd_theme, themeName)
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
                            .semantics { contentDescription = themeDescription }
                            .clickable { viewModel.setTheme(theme) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(themeName, color = Color(0xFFB7B3AA), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        ToggleRow(
            stringResource(R.string.settings_brightness_title),
            stringResource(R.string.settings_brightness_subtitle),
            prefs.maxBrightness,
            accent,
            viewModel::setMaxBrightness,
        )
        ToggleRow(
            stringResource(R.string.settings_coordinates_title),
            stringResource(R.string.settings_coordinates_subtitle),
            prefs.showCoordinates,
            accent,
            viewModel::setShowCoordinates,
        )
        ToggleRow(
            stringResource(R.string.settings_accuracy_title),
            stringResource(R.string.settings_accuracy_subtitle),
            prefs.showAccuracy,
            accent,
            viewModel::setShowAccuracy,
        )
        ToggleRow(
            stringResource(R.string.settings_clock_title),
            stringResource(R.string.settings_clock_subtitle),
            prefs.showClock,
            accent,
            viewModel::setShowClock,
        )
        ToggleRow(
            stringResource(R.string.settings_place_title),
            stringResource(R.string.settings_place_subtitle),
            prefs.showPlace,
            accent,
            viewModel::setShowPlace,
        )
        ToggleRow(
            stringResource(R.string.settings_dynamic_title),
            stringResource(R.string.settings_dynamic_subtitle),
            prefs.dynamicColor,
            accent,
            viewModel::setDynamicColor,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_watermark_note),
            color = Color(0xFF8E8A82),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            color = Color(0xFF8E8A82),
            fontFamily = Outfit,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0xFF8E8A82), fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp)
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
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    val onLabel = stringResource(R.string.toggle_on)
    val offLabel = stringResource(R.string.toggle_off)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color(0xFFF3F0E8), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF8E8A82), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text(
            text = if (checked) onLabel else offLabel,
            color = if (checked) Color(0xFF141210) else Color(0xFFB7B3AA),
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) accent else Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
