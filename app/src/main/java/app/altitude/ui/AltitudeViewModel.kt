package app.altitude.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.altitude.data.UserPreferences
import app.altitude.domain.AltitudeFusion
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.AppMode
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.DisplayTheme
import app.altitude.domain.FusionInput
import app.altitude.domain.FusionOutput
import app.altitude.domain.GeoidModel
import app.altitude.domain.GnssSample
import app.altitude.domain.LastKnown
import app.altitude.domain.PrefsSnapshot
import app.altitude.domain.WatermarkStyle
import app.altitude.domain.Egm96Geoid
import app.altitude.domain.resolveMsl
import app.altitude.location.GnssRepository
import app.altitude.location.isLocationEnabled
import app.altitude.location.pressureHpa
import app.altitude.location.resolvePlaceName
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AltitudeUiState(
    val reading: FusionOutput,
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
    val prefs: PrefsSnapshot,
    val mode: AppMode,
    val saveMessage: Int?,
    val settingsOpen: Boolean,
    val locationGranted: Boolean,
    val cameraGranted: Boolean,
    val locationEnabled: Boolean,
    val hasBarometer: Boolean,
    val lastKnown: LastKnown?,
    val nowMillis: Long,
    val geoidReady: Boolean,
)

class AltitudeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UserPreferences(application)
    private val fusion = AltitudeFusion()
    private val gnss = GnssRepository(application)

    private val reading = MutableStateFlow(FusionOutput.empty())
    private val gps = MutableStateFlow<GnssSample?>(null)
    private val pressure = MutableStateFlow<Double?>(null)
    private val place = MutableStateFlow<String?>(null)
    private val geoid = MutableStateFlow<GeoidModel?>(null)
    private val mode = MutableStateFlow(AppMode.DISPLAY)
    private val saveMessage = MutableStateFlow<Int?>(null)
    private val settingsOpen = MutableStateFlow(false)
    private val locationGranted = MutableStateFlow(false)
    private val cameraGranted = MutableStateFlow(false)
    private val locationEnabled = MutableStateFlow(true)
    private val hasBarometer = MutableStateFlow(false)
    private val lastKnown = MutableStateFlow(preferences.readLastKnown())
    private val nowMillis = MutableStateFlow(System.currentTimeMillis())
    private val foreground = MutableStateFlow(false)

    private var gnssJob: Job? = null
    private var lastPersistElapsed = 0L

    val uiState: StateFlow<AltitudeUiState>

    init {
        hasBarometer.value = application.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        refreshSystemState()

        viewModelScope.launch {
            val bytes = application.assets.open("egm96-15.bin").use { it.readBytes() }
            geoid.value = Egm96Geoid.decode(bytes)
        }
        viewModelScope.launch {
            pressureHpa(application).collect { pressure.value = it }
        }
        viewModelScope.launch {
            gps.filterNotNull()
                .map { cell(it.latitude) to cell(it.longitude) }
                .distinctUntilChanged()
                .collect { (latCell, lonCell) ->
                    val name = resolvePlaceName(application, latCell * CELL, lonCell * CELL)
                    if (!name.isNullOrBlank()) place.value = name
                }
        }
        viewModelScope.launch {
            while (isActive) {
                stepFusion()
                delay(100)
            }
        }

        val sensors = combine(reading, gps, place, nowMillis, geoid) { current, sample, placeName, now, model ->
            SensorSlice(current, sample, placeName, now, model != null)
        }
        val access = combine(locationGranted, cameraGranted, settingsOpen) { location, camera, settings ->
            AccessSlice(location, camera, settings)
        }
        val chrome = combine(
            preferences.updates(),
            mode,
            saveMessage,
            access,
        ) { prefs, currentMode, message, accessSlice ->
            ChromeSlice(
                prefs,
                currentMode,
                message,
                accessSlice.locationGranted,
                accessSlice.cameraGranted,
                accessSlice.settingsOpen,
            )
        }
        val device = combine(locationEnabled, hasBarometer, lastKnown) { enabled, baro, known ->
            Triple(enabled, baro, known)
        }
        uiState = combine(sensors, chrome, device) { sensor, chromeSlice, deviceSlice ->
            val wall = sensor.now
            val sample = sensor.sample
            val coordinatesFresh = sample != null && (sample.timeMs <= 0L || wall - sample.timeMs in 0..30_000)
            AltitudeUiState(
                reading = sensor.reading,
                latitude = if (coordinatesFresh) sample?.latitude else null,
                longitude = if (coordinatesFresh) sample?.longitude else null,
                placeName = sensor.place,
                prefs = chromeSlice.prefs,
                mode = chromeSlice.mode,
                saveMessage = chromeSlice.saveMessage,
                settingsOpen = chromeSlice.settingsOpen,
                locationGranted = chromeSlice.locationGranted,
                cameraGranted = chromeSlice.cameraGranted,
                locationEnabled = deviceSlice.first,
                hasBarometer = deviceSlice.second,
                lastKnown = deviceSlice.third,
                nowMillis = wall,
                geoidReady = sensor.geoidReady,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())
    }

    fun onForeground(isForeground: Boolean) {
        foreground.value = isForeground
        if (isForeground) {
            refreshSystemState()
            ensureGnss()
        } else {
            gnssJob?.cancel()
            gnssJob = null
        }
    }

    fun refreshSystemState() {
        val app = getApplication<Application>()
        locationGranted.value = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        cameraGranted.value = hasPermission(Manifest.permission.CAMERA)
        locationEnabled.value = isLocationEnabled(app)
        if (foreground.value) ensureGnss()
    }

    fun setMode(value: AppMode) {
        mode.value = value
    }

    fun toggleUnit() {
        val next = if (preferences.current().unit == AltitudeUnit.METERS) AltitudeUnit.FEET else AltitudeUnit.METERS
        preferences.setUnit(next)
    }

    fun setUnit(unit: AltitudeUnit) = preferences.setUnit(unit)

    fun setTheme(theme: DisplayTheme) = preferences.setTheme(theme)

    fun setMaxBrightness(enabled: Boolean) = preferences.setMaxBrightness(enabled)

    fun setShowCoordinates(enabled: Boolean) = preferences.setShowCoordinates(enabled)

    fun setShowAccuracy(enabled: Boolean) = preferences.setShowAccuracy(enabled)

    fun setShowClock(enabled: Boolean) = preferences.setShowClock(enabled)

    fun setShowPlace(enabled: Boolean) = preferences.setShowPlace(enabled)

    fun setDynamicColor(enabled: Boolean) = preferences.setDynamicColor(enabled)

    fun setWatermarkStyle(style: WatermarkStyle) = preferences.setWatermarkStyle(style)

    fun setCaptionAnchor(anchor: CaptionAnchor) = preferences.setCaptionAnchor(anchor)

    fun completeOnboarding() = preferences.setOnboardingComplete()

    fun setSettingsOpen(open: Boolean) {
        settingsOpen.value = open
    }

    fun reportSave(messageRes: Int) {
        saveMessage.value = messageRes
        viewModelScope.launch {
            delay(2_800)
            if (saveMessage.value == messageRes) saveMessage.value = null
        }
    }

    private fun ensureGnss() {
        if (!locationGranted.value || !foreground.value) return
        if (gnssJob?.isActive == true) return
        gnssJob = viewModelScope.launch {
            gnss.fixes().catch { }.collect { gps.value = it }
        }
    }

    private fun stepFusion() {
        val elapsed = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        nowMillis.value = wall
        val sample = gps.value
        val msl = sample?.let { resolveMsl(it, geoid.value) }
        val fresh = sample != null && msl != null && (sample.timeMs <= 0L || wall - sample.timeMs in 0..3_000)
        val output = fusion.update(
            FusionInput(
                timestampMs = elapsed,
                gpsMslMeters = if (fresh) msl else null,
                gpsVerticalAccuracyMeters = if (fresh) sample?.verticalAccuracyMeters else null,
                pressureHpa = pressure.value,
            ),
        )
        reading.value = output
        val altitude = output.altitudeMslMeters
        if (output.absoluteMsl && altitude != null && sample != null && elapsed - lastPersistElapsed > 5_000) {
            lastPersistElapsed = elapsed
            val known = LastKnown(altitude, wall, sample.latitude, sample.longitude)
            preferences.writeLastKnown(known)
            lastKnown.value = known
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun initialState(): AltitudeUiState {
        val prefs = preferences.current()
        return AltitudeUiState(
            reading = FusionOutput.empty(),
            latitude = null,
            longitude = null,
            placeName = null,
            prefs = prefs,
            mode = AppMode.DISPLAY,
            saveMessage = null,
            settingsOpen = false,
            locationGranted = false,
            cameraGranted = false,
            locationEnabled = true,
            hasBarometer = false,
            lastKnown = preferences.readLastKnown(),
            nowMillis = System.currentTimeMillis(),
            geoidReady = false,
        )
    }

    private data class SensorSlice(
        val reading: FusionOutput,
        val sample: GnssSample?,
        val place: String?,
        val now: Long,
        val geoidReady: Boolean,
    )

    private data class AccessSlice(
        val locationGranted: Boolean,
        val cameraGranted: Boolean,
        val settingsOpen: Boolean,
    )

    private data class ChromeSlice(
        val prefs: PrefsSnapshot,
        val mode: AppMode,
        val saveMessage: Int?,
        val locationGranted: Boolean,
        val cameraGranted: Boolean,
        val settingsOpen: Boolean,
    )

    private companion object {
        const val CELL = 0.05
        fun cell(value: Double): Int = (value / CELL).roundToInt()
    }
}
