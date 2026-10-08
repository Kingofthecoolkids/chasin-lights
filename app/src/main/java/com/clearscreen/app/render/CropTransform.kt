package com.clearscreen.app.render

import android.graphics.Matrix

/**
 * Builds the texture-coordinate transform that implements calibration: crop, zoom, offset and
 * rotation, all composed into one 4x4 matrix the vertex shader applies to the output quad
 * *before* the camera's own [android.graphics.SurfaceTexture.getTransformMatrix] is applied.
 *
 * Everything here is done in pixel space (view pixels and native sensor pixels), not normalized
 * 0..1 space, specifically to avoid the aspect-ratio distortion bugs that show up when rotating
 * coordinates inside a non-square normalized unit square.
 */
object CropTransform {

    /**
     * @param nativeFrameWidth / Height camera stream dimensions, in the sensor's native (un-rotated) orientation.
     * @param viewWidth / Height wallpaper surface dimensions, in pixels.
     * @param sensorOrientationDegrees CameraCharacteristics.SENSOR_ORIENTATION for the active lens.
     * @param displayRotationDegrees current display rotation, 0/90/180/270.
     * @param zoom multiplier on top of the automatic "cover fit" scale; 1.0 = auto default.
     * @param offsetXFraction / YFraction crop-window shift, as a fraction of view width/height.
     * @param mirrorX flips the final image left-right, in view space (i.e. independent of
     * rotation). Exists because camera2's buffer orientation handling varies enough across real
     * devices that a mirrored feed can't be reliably ruled out or corrected for in code without
     * hardware to test against -- see WallpaperSettings.mirrorHorizontal.
     */
    fun computeCropMatrix(
        nativeFrameWidth: Int,
        nativeFrameHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        sensorOrientationDegrees: Int,
        displayRotationDegrees: Int,
        zoom: Float,
        offsetXFraction: Float,
        offsetYFraction: Float,
        mirrorX: Boolean = false
    ): FloatArray {
        if (nativeFrameWidth <= 0 || nativeFrameHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return IDENTITY_4X4.copyOf()
        }

        // Back-facing camera rotation-to-upright formula (see CameraCharacteristics.SENSOR_ORIENTATION docs).
        val totalRotation = ((sensorOrientationDegrees - displayRotationDegrees) % 360 + 360) % 360
        val rotationSwapsAxes = totalRotation == 90 || totalRotation == 270

        val visualFrameWidth = if (rotationSwapsAxes) nativeFrameHeight else nativeFrameWidth
        val visualFrameHeight = if (rotationSwapsAxes) nativeFrameWidth else nativeFrameHeight

        // "Cover fit": scale so the upright frame fills the view, cropping the excess dimension.
        val coverScale = maxOf(
            viewWidth.toFloat() / visualFrameWidth,
            viewHeight.toFloat() / visualFrameHeight
        )
        val totalScale = coverScale * zoom

        val offsetPxX = offsetXFraction * viewWidth
        val offsetPxY = offsetYFraction * viewHeight

        // Maps an output-quad coordinate (0..1, 0..1) to a normalized native-texture coordinate.
        // Built with sequential postXxx calls, which compose in call order when starting from
        // identity -- i.e. this reads top-to-bottom as the forward pipeline applied to a point.
        val m = Matrix()
        m.postScale(viewWidth.toFloat(), viewHeight.toFloat())           // unit quad -> view pixels
        if (mirrorX) {
            // Flip around the view's own center, in view-pixel space -- applied first, so it's
            // always a left-right flip of what the viewer sees, regardless of device rotation.
            m.postScale(-1f, 1f)
            m.postTranslate(viewWidth.toFloat(), 0f)
        }
        m.postTranslate(-viewWidth / 2f, -viewHeight / 2f)                // center on origin
        m.postTranslate(-offsetPxX, -offsetPxY)                          // calibration offset
        m.postScale(1f / totalScale, 1f / totalScale)                    // undo cover+zoom scale
        m.postRotate(-totalRotation.toFloat())                           // visual orientation -> native sensor orientation
        m.postTranslate(nativeFrameWidth / 2f, nativeFrameHeight / 2f)   // origin back to native top-left
        m.postScale(1f / nativeFrameWidth, 1f / nativeFrameHeight)       // pixels -> normalized texture coords

        return androidMatrixToGl4(m)
    }

    private fun androidMatrixToGl4(matrix: Matrix): FloatArray {
        val v = FloatArray(9)
        matrix.getValues(v)
        // v is row-major [a,b,c, d,e,f, g,h,i]; postRotate/postScale/postTranslate never introduce
        // perspective, so g/h/i are always 0/0/1 and can be ignored safely.
        val a = v[0]; val b = v[1]; val c = v[2]
        val d = v[3]; val e = v[4]; val f = v[5]
        return floatArrayOf(
            a, d, 0f, 0f,
            b, e, 0f, 0f,
            0f, 0f, 1f, 0f,
            c, f, 0f, 1f
        )
    }

    val IDENTITY_4X4 = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}
