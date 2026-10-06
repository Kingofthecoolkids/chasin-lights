package com.clearscreen.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "clearscreen_settings")

/** Persists [WallpaperSettings] via DataStore and exposes it as a Flow the engine and the settings UI both observe. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ZOOM = floatPreferencesKey("zoom")
        val OFFSET_X = floatPreferencesKey("offset_x")
        val OFFSET_Y = floatPreferencesKey("offset_y")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
        val FPS_CAP = intPreferencesKey("fps_cap")
        val BATTERY_SAVER_THRESHOLD = intPreferencesKey("battery_saver_threshold")
        val LENS_ID = stringPreferencesKey("lens_id")
        val HAS_COMPLETED_AUTO_CALIBRATION = booleanPreferencesKey("has_completed_auto_calibration")
    }

    val settingsFlow: Flow<WallpaperSettings> = context.dataStore.data.map { prefs ->
        WallpaperSettings(
            zoom = prefs[Keys.ZOOM] ?: 1f,
            offsetX = prefs[Keys.OFFSET_X] ?: 0f,
            offsetY = prefs[Keys.OFFSET_Y] ?: 0f,
            brightness = prefs[Keys.BRIGHTNESS] ?: 1f,
            blurEnabled = prefs[Keys.BLUR_ENABLED] ?: false,
            fpsCap = prefs[Keys.FPS_CAP] ?: 24,
            batterySaverThresholdPercent = prefs[Keys.BATTERY_SAVER_THRESHOLD] ?: 15,
            lensId = prefs[Keys.LENS_ID],
            hasCompletedAutoCalibration = prefs[Keys.HAS_COMPLETED_AUTO_CALIBRATION] ?: false
        )
    }

    suspend fun update(transform: (WallpaperSettings) -> WallpaperSettings) {
        context.dataStore.edit { prefs ->
            val current = WallpaperSettings(
                zoom = prefs[Keys.ZOOM] ?: 1f,
                offsetX = prefs[Keys.OFFSET_X] ?: 0f,
                offsetY = prefs[Keys.OFFSET_Y] ?: 0f,
                brightness = prefs[Keys.BRIGHTNESS] ?: 1f,
                blurEnabled = prefs[Keys.BLUR_ENABLED] ?: false,
                fpsCap = prefs[Keys.FPS_CAP] ?: 24,
                batterySaverThresholdPercent = prefs[Keys.BATTERY_SAVER_THRESHOLD] ?: 15,
                lensId = prefs[Keys.LENS_ID],
                hasCompletedAutoCalibration = prefs[Keys.HAS_COMPLETED_AUTO_CALIBRATION] ?: false
            )
            val updated = transform(current)
            prefs[Keys.ZOOM] = updated.zoom
            prefs[Keys.OFFSET_X] = updated.offsetX
            prefs[Keys.OFFSET_Y] = updated.offsetY
            prefs[Keys.BRIGHTNESS] = updated.brightness
            prefs[Keys.BLUR_ENABLED] = updated.blurEnabled
            prefs[Keys.FPS_CAP] = updated.fpsCap
            prefs[Keys.BATTERY_SAVER_THRESHOLD] = updated.batterySaverThresholdPercent
            updated.lensId?.let { prefs[Keys.LENS_ID] = it }
            prefs[Keys.HAS_COMPLETED_AUTO_CALIBRATION] = updated.hasCompletedAutoCalibration
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
