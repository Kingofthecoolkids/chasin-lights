package com.clearscreen.app.camera

/** Everything the calibration math and the lens-choice setting need about one rear camera. */
data class CameraLensInfo(
    val cameraId: String,
    val sensorOrientationDegrees: Int,
    val focalLengthMm: Float,
    val sensorPhysicalWidthMm: Float,
    val sensorPhysicalHeightMm: Float,
    val isUltrawide: Boolean
) {
    val displayName: String get() = if (isUltrawide) "Ultrawide" else "Main"
}
