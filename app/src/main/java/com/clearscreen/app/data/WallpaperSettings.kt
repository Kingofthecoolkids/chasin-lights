package com.clearscreen.app.data

/**
 * Everything the renderer and the settings UI share. `zoom`/`offsetX`/`offsetY` are the
 * calibration values (see README "How calibration works"); the rest are the "Extra settings"
 * from the project brief.
 */
data class WallpaperSettings(
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val brightness: Float = 1f,
    val blurEnabled: Boolean = false,
    val fpsCap: Int = 24,
    val batterySaverThresholdPercent: Int = 15,
    val lensId: String? = null,
    val hasCompletedAutoCalibration: Boolean = false
) {
    companion object {
        val FPS_CAP_OPTIONS = listOf(15, 24, 30)
        const val OFFSET_RANGE = 0.3f
        const val BRIGHTNESS_MIN = 0.15f // never let the dim overlay go fully black
    }
}
