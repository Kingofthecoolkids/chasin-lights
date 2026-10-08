package com.clearscreen.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface

/**
 * Owns exactly one open [CameraDevice] at a time and runs its own background thread for Camera2's
 * callback-heavy API, as required (Camera2 callbacks must not run on the calling thread's looper
 * if there isn't one, and must never block the GL render thread).
 *
 * Callers are responsible for calling [open] only while the wallpaper is actually visible and
 * [close] the moment it stops being visible -- this class does not make that decision itself.
 */
class CameraController(context: Context) {

    interface Listener {
        fun onOpened(characteristics: CameraCharacteristics, frameSize: Size)
        fun onError(message: String, fatal: Boolean)
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    /**
     * [CameraManager.getCameraCharacteristics] (and `cameraIdList`) can throw
     * [CameraAccessException] if the camera subsystem is in a bad state (service crashed, device
     * mid-sleep/wake, etc.). Both this and [choosePreviewSize] are called from code paths with no
     * try/catch of their own (e.g. a plain visibility change), so they must never let that escape.
     */
    fun listRearLenses(): List<CameraLensInfo> {
        val result = mutableListOf<Pair<CameraLensInfo, Float>>() // info + focal length for ultrawide ranking
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                if (focalLengths == null || focalLengths.isEmpty() || sensorSize == null) continue
                val focalLength = focalLengths[0]
                result += CameraLensInfo(
                    cameraId = id,
                    sensorOrientationDegrees = sensorOrientation,
                    focalLengthMm = focalLength,
                    sensorPhysicalWidthMm = sensorSize.width,
                    sensorPhysicalHeightMm = sensorSize.height,
                    isUltrawide = false // placeholder, fixed up below
                ) to focalLength
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enumerate rear lenses", e)
            return emptyList()
        }
        if (result.isEmpty()) return emptyList()
        // Shortest focal length among back lenses = widest field of view = "ultrawide".
        val minFocal = result.minOf { it.second }
        return result.map { (info, focal) ->
            if (focal == minFocal && result.size > 1) info.copy(isUltrawide = true) else info
        }
    }

    fun choosePreviewSize(cameraId: String, targetWidth: Int, targetHeight: Int): Size {
        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return Size(targetWidth, targetHeight)
            val choices = map.getOutputSizes(android.graphics.SurfaceTexture::class.java) ?: emptyArray()
            if (choices.isEmpty()) return Size(targetWidth, targetHeight)

            val targetAspect = targetWidth.toFloat() / targetHeight
            // No need for 4K: cap the long side near the screen's long side, then pick the closest
            // aspect ratio among sizes at or above that cap so we're not drastically upscaling.
            val cappedLongSide = maxOf(targetWidth, targetHeight)
            return choices
                .filter { maxOf(it.width, it.height) <= cappedLongSide * 1.5 }
                .ifEmpty { choices.toList() }
                .minByOrNull { size ->
                    val aspect = size.width.toFloat() / size.height
                    kotlin.math.abs(aspect - targetAspect) * 1000 - minOf(size.width, size.height) * 0.001f
                } ?: choices[0]
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read stream configuration for $cameraId", e)
            return Size(targetWidth, targetHeight)
        }
    }

    @SuppressLint("MissingPermission") // caller has already verified CAMERA permission is granted
    fun open(cameraId: String, surface: Surface, frameSize: Size, listener: Listener) {
        close() // defensive: never allow two sessions to overlap

        val thread = HandlerThread("ClearScreenCamera").also { it.start() }
        cameraThread = thread
        val handler = Handler(thread.looper)
        cameraHandler = handler

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cameraDevice: CameraDevice) {
                    device = cameraDevice
                    startSession(cameraDevice, surface, cameraId, frameSize, listener, handler)
                }

                override fun onDisconnected(cameraDevice: CameraDevice) {
                    Log.w(TAG, "Camera $cameraId disconnected (likely taken by another app)")
                    cameraDevice.close()
                    device = null
                    listener.onError("Camera disconnected", fatal = true)
                }

                override fun onError(cameraDevice: CameraDevice, error: Int) {
                    Log.w(TAG, "Camera $cameraId error: $error")
                    cameraDevice.close()
                    device = null
                    listener.onError("Camera error code $error", fatal = true)
                }
            }, handler)
        } catch (e: CameraAccessException) {
            listener.onError("CameraAccessException: ${e.reason}", fatal = true)
        } catch (e: SecurityException) {
            listener.onError("Camera permission not granted", fatal = true)
        }
    }

    private fun startSession(
        cameraDevice: CameraDevice,
        surface: Surface,
        cameraId: String,
        frameSize: Size,
        listener: Listener,
        handler: Handler
    ) {
        try {
            // The (List<Surface>, StateCallback, Handler) overload is deprecated in favor of
            // SessionConfiguration, but that replacement needs API 28+; this one still works
            // correctly through the latest Android versions and covers minSdk 26 too.
            @Suppress("DEPRECATION")
            cameraDevice.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configuredSession: CameraCaptureSession) {
                        session = configuredSession
                        try {
                            val request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            request.addTarget(surface)
                            request.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                            request.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            configuredSession.setRepeatingRequest(request.build(), null, handler)

                            val chars = cameraManager.getCameraCharacteristics(cameraId)
                            listener.onOpened(chars, frameSize)
                        } catch (e: CameraAccessException) {
                            listener.onError("Failed to start preview: ${e.message}", fatal = true)
                        }
                    }

                    override fun onConfigureFailed(failedSession: CameraCaptureSession) {
                        listener.onError("Capture session configuration failed", fatal = true)
                    }
                },
                handler
            )
        } catch (e: CameraAccessException) {
            listener.onError("createCaptureSession failed: ${e.message}", fatal = true)
        }
    }

    fun close() {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session", e)
        }
        session = null

        try {
            device?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing device", e)
        }
        device = null

        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
