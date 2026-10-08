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
    val hasCompletedAutoCalibration: Boolean = false,
    /** True once the user has confirmed ClearScreen in the system's live-wallpaper picker at least once. */
    val hasActivatedOnce: Boolean = false,
    /** The on/off state the home-screen icon toggles. Camera only runs when this is also true. */
    val effectOn: Boolean = true,
    /**
     * Extra clockwise rotation (0/90/180/270) added on top of the auto-detected sensor/display
     * rotation. The auto-detected rotation can't be verified against every device's actual
     * sensorOrientation/mount angle without real hardware to test on, so this is the guaranteed
     * manual fix if the feed ever comes up sideways or upside down: cycle it until it looks right.
     */
    val manualRotationOverride: Int = 0,
    /**
     * Flips the feed left-right. Confirmed needed (default true) by a real-device test where
     * text read backwards -- the crop/rotation matrix itself has no reflection in it, so this is
     * most likely a camera2 buffer-orientation quirk on that specific hardware/HAL rather than
     * something provably wrong in this code; either way, a manual toggle is the reliable fix
     * instead of guessing further at code no device here can verify against.
     */
    val mirrorHorizontal: Boolean = true
) {
    companion object {
        val FPS_CAP_OPTIONS = listOf(15, 24, 30)
        val ROTATION_OPTIONS = listOf(0, 90, 180, 270)
        const val OFFSET_RANGE = 0.3f
        const val BRIGHTNESS_MIN = 0.15f // never let the dim overlay go fully black
    }
}
