package com.clearscreen.app.camera

/**
 * The auto-default zoom calculation -- the one piece of calibration that can actually be derived
 * from hardware characteristics rather than eyeballed. Horizontal and vertical *offset* have no
 * reliable generic source (Camera2 doesn't expose lens-to-screen-center physical placement), so
 * those stay at 0 until the user runs guided calibration; see README "How calibration works".
 *
 * Derivation: a camera with horizontal field of view fov, at an assumed viewing distance d,
 * captures a real-world width of `2 * d * tan(fov/2)` across its full frame. For the phone to look
 * transparent, the screen (physical width screenWidthMm) must show exactly the real-world width it
 * physically occupies, i.e. screenWidthMm itself (same distance, viewer roughly behind the camera
 * axis -- the simplifying assumption every app of this kind makes; it ignores eye/camera parallax,
 * which is exactly what guided calibration lets the user correct for).
 *
 * So the crop-in factor needed is capturedWidth / screenWidthMm, i.e.:
 *   zoom = (d * sensorWidthMm) / (focalLengthMm * screenWidthMm)
 * (the two `tan`/`atan` cancel algebraically, see below).
 */
object CameraCalibrationMath {

    /** Typical distance (mm) someone holds a phone up to compare against what's behind it. */
    const val ASSUMED_VIEWING_DISTANCE_MM = 300f

    fun computeAutoZoom(
        lens: CameraLensInfo,
        screenPhysicalWidthMm: Float,
        viewingDistanceMm: Float = ASSUMED_VIEWING_DISTANCE_MM
    ): Float {
        if (screenPhysicalWidthMm <= 0f || lens.focalLengthMm <= 0f) return 1f
        val zoom = (viewingDistanceMm * lens.sensorPhysicalWidthMm) /
            (lens.focalLengthMm * screenPhysicalWidthMm)
        return zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    const val MIN_ZOOM = 0.5f
    const val MAX_ZOOM = 4f
}
