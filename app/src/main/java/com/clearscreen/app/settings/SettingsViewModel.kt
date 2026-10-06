package com.clearscreen.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clearscreen.app.camera.CameraCalibrationMath
import com.clearscreen.app.camera.CameraController
import com.clearscreen.app.camera.CameraLensInfo
import com.clearscreen.app.data.SettingsRepository
import com.clearscreen.app.data.WallpaperSettings
import com.clearscreen.app.util.DisplayPhysicalSize
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SettingsRepository(app)

    // Only used to list/characterize lenses for the UI and the auto-calibration math -- never opened.
    private val lensLookup = CameraController(app)

    val settings: StateFlow<WallpaperSettings> = repository.settingsFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, WallpaperSettings()
    )

    val lenses: List<CameraLensInfo> by lazy { lensLookup.listRearLenses() }

    fun setZoom(zoom: Float) = update { it.copy(zoom = zoom) }
    fun setOffsetX(offsetX: Float) = update { it.copy(offsetX = offsetX) }
    fun setOffsetY(offsetY: Float) = update { it.copy(offsetY = offsetY) }
    fun setBrightness(brightness: Float) = update { it.copy(brightness = brightness) }
    fun setBlurEnabled(enabled: Boolean) = update { it.copy(blurEnabled = enabled) }
    fun setFpsCap(fps: Int) = update { it.copy(fpsCap = fps) }
    fun setBatterySaverThreshold(percent: Int) = update { it.copy(batterySaverThresholdPercent = percent) }
    fun setLens(lensId: String?) = update { it.copy(lensId = lensId) }
    fun setEffectOn(on: Boolean) = update { it.copy(effectOn = on) }

    /** Called once the system wallpaper picker confirms ClearScreen was actually set. */
    fun markActivated() = update { it.copy(hasActivatedOnce = true, effectOn = true) }

    /** Runs once per install: computes the hardware-derived auto zoom default. Offsets stay at 0 (see CameraCalibrationMath doc). */
    fun applyAutoCalibrationIfNeeded() {
        val current = settings.value
        if (current.hasCompletedAutoCalibration) return
        val lens = lenses.firstOrNull { it.cameraId == current.lensId }
            ?: lenses.firstOrNull { !it.isUltrawide }
            ?: lenses.firstOrNull()
            ?: return
        val screenWidthMm = DisplayPhysicalSize.get(getApplication()).widthMm
        val autoZoom = CameraCalibrationMath.computeAutoZoom(lens, screenWidthMm)
        update { it.copy(zoom = autoZoom, hasCompletedAutoCalibration = true) }
    }

    fun resetToDefault() = viewModelScope.launch { repository.resetToDefaults() }

    private fun update(transform: (WallpaperSettings) -> WallpaperSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
