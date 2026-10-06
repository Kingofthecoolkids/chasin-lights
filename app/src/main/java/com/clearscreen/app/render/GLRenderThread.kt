package com.clearscreen.app.render

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import com.clearscreen.app.camera.CameraController
import com.clearscreen.app.camera.CameraLensInfo
import com.clearscreen.app.data.WallpaperSettings
import com.clearscreen.app.util.WallpaperSnapshot

/**
 * The render thread: owns the EGL context, the camera-backed external texture, and the
 * [CameraController], and is the single place that decides when the camera is actually open.
 *
 * All public methods are safe to call from any thread -- they hop onto this thread's own
 * [Handler] immediately. All the mutable state below is touched only from that handler's thread.
 */
class GLRenderThread(private val appContext: Context) {

    private val handlerThread = HandlerThread("ClearScreenGL").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val cameraController = CameraController(appContext)

    private var eglCore: EglCore? = null
    private var windowSurface: WindowSurface? = null
    private var textureRenderer: CameraTextureRenderer? = null
    private var staticRenderer: StaticBitmapRenderer? = null
    private var triedLoadingSnapshot = false

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var displayRotationDegrees = 0

    private var isVisible = false
    private var hasCameraPermission = false
    private var isBatteryPaused = false
    private var isCameraOpen = false
    private var hasRenderedFrame = false
    private var currentLens: CameraLensInfo? = null
    private var frameSize = Size(0, 0)
    private var lastDrawTimeNanos = 0L

    private var settingsState = WallpaperSettings()
    private var lenses: List<CameraLensInfo> = emptyList()

    fun onSurfaceCreated(holder: SurfaceHolder) = handler.post { doSurfaceCreated(holder) }

    fun onSurfaceChanged(holder: SurfaceHolder, width: Int, height: Int, rotationDegrees: Int) =
        handler.post { doSurfaceChanged(holder, width, height, rotationDegrees) }

    fun onSurfaceDestroyed() = handler.post { doSurfaceDestroyed() }

    fun setVisible(visible: Boolean) = handler.post {
        isVisible = visible
        recomputeCameraDesire()
    }

    fun setCameraPermissionGranted(granted: Boolean) = handler.post {
        hasCameraPermission = granted
        recomputeCameraDesire()
    }

    fun setBatteryPaused(paused: Boolean) = handler.post {
        isBatteryPaused = paused
        recomputeCameraDesire()
    }

    fun updateSettings(settings: WallpaperSettings) = handler.post {
        val lensChanged = settingsState.lensId != settings.lensId
        settingsState = settings
        if (lensChanged && isCameraOpen) {
            closeCameraNow()
        }
        recomputeCameraDesire()
        renderFrame() // reflect the new calibration/brightness/blur immediately
    }

    fun shutdown() {
        handler.post { doSurfaceDestroyed() }
        handlerThread.quitSafely()
    }

    // --- Everything below runs only on the GL render thread's handler. ---

    private fun doSurfaceCreated(holder: SurfaceHolder) {
        val core = EglCore()
        eglCore = core
        windowSurface = WindowSurface(core, holder.surface)
        windowSurface?.makeCurrent()
        textureRenderer = CameraTextureRenderer().also { renderer ->
            renderer.surfaceTexture.setOnFrameAvailableListener({ onNewCameraFrame() }, handler)
        }
        staticRenderer = StaticBitmapRenderer()
        triedLoadingSnapshot = false
        lenses = cameraController.listRearLenses()
        recomputeCameraDesire()
    }

    private fun doSurfaceChanged(holder: SurfaceHolder, width: Int, height: Int, rotationDegrees: Int) {
        surfaceWidth = width
        surfaceHeight = height
        displayRotationDegrees = rotationDegrees
        // Surface dimensions/format may have changed (e.g. rotation): rebuild the EGL window
        // surface against the same (possibly resized) native Surface.
        val core = eglCore ?: return
        windowSurface?.release()
        windowSurface = WindowSurface(core, holder.surface)
        windowSurface?.makeCurrent()
        if (isCameraOpen) {
            // Re-pick preview size for the new dimensions/orientation and restart the stream.
            closeCameraNow()
        }
        recomputeCameraDesire()
        renderFrame()
    }

    private fun doSurfaceDestroyed() {
        closeCameraNow()
        textureRenderer?.release()
        textureRenderer = null
        staticRenderer?.release()
        staticRenderer = null
        windowSurface?.release()
        windowSurface = null
        eglCore?.release()
        eglCore = null
        hasRenderedFrame = false
    }

    private fun onNewCameraFrame() {
        textureRenderer?.updateTexImage()
        hasRenderedFrame = true
        val minIntervalNanos = 1_000_000_000L / settingsState.fpsCap.coerceAtLeast(1)
        val now = System.nanoTime()
        if (now - lastDrawTimeNanos >= minIntervalNanos) {
            renderFrame()
        }
    }

    private fun renderFrame() {
        val window = windowSurface ?: return
        if (textureRenderer == null || staticRenderer == null) return
        if (surfaceWidth == 0 || surfaceHeight == 0) return
        window.makeCurrent()

        if (!settingsState.effectOn) {
            ensureSnapshotLoaded()
            val staticBitmap = staticRenderer
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES20.glClearColor(0.04f, 0.05f, 0.06f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (staticBitmap?.hasBitmap == true) {
                staticBitmap.draw(surfaceWidth, surfaceHeight)
            }
        } else if (hasRenderedFrame) {
            textureRenderer!!.draw(
                viewWidth = surfaceWidth,
                viewHeight = surfaceHeight,
                frameWidth = frameSize.width,
                frameHeight = frameSize.height,
                sensorOrientationDegrees = currentLens?.sensorOrientationDegrees ?: 90,
                displayRotationDegrees = displayRotationDegrees,
                zoom = settingsState.zoom,
                offsetXFraction = settingsState.offsetX,
                offsetYFraction = settingsState.offsetY,
                brightness = settingsState.brightness,
                blurEnabled = settingsState.blurEnabled
            )
        } else {
            // Never had a frame yet (permission just granted, no camera hardware, etc.): neutral
            // background rather than an undefined/garbage buffer.
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES20.glClearColor(0.04f, 0.05f, 0.06f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        window.swapBuffers()
        lastDrawTimeNanos = System.nanoTime()
    }

    /** Lazily decodes the cached previous-wallpaper snapshot into a GL texture, once. */
    private fun ensureSnapshotLoaded() {
        val renderer = staticRenderer ?: return
        if (renderer.hasBitmap || triedLoadingSnapshot) return
        triedLoadingSnapshot = true
        if (!WallpaperSnapshot.exists(appContext)) return
        val bitmap = try {
            BitmapFactory.decodeFile(WallpaperSnapshot.filePath(appContext))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode cached wallpaper snapshot", e)
            null
        }
        bitmap?.let { renderer.setBitmap(it) }
    }

    private fun shouldCameraBeOpen(): Boolean =
        isVisible && hasCameraPermission && !isBatteryPaused && settingsState.effectOn &&
            textureRenderer != null && surfaceWidth > 0

    private fun recomputeCameraDesire() {
        val shouldOpen = shouldCameraBeOpen()
        if (shouldOpen && !isCameraOpen) {
            openCameraNow()
        } else if (!shouldOpen && isCameraOpen) {
            closeCameraNow()
        }
    }

    private fun openCameraNow() {
        val renderer = textureRenderer ?: return
        if (lenses.isEmpty()) lenses = cameraController.listRearLenses()
        val lens = lenses.firstOrNull { it.cameraId == settingsState.lensId }
            ?: lenses.firstOrNull { !it.isUltrawide }
            ?: lenses.firstOrNull()
        if (lens == null) {
            Log.w(TAG, "No rear camera available")
            return
        }
        val size = cameraController.choosePreviewSize(lens.cameraId, surfaceWidth, surfaceHeight)
        frameSize = size
        renderer.surfaceTexture.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(renderer.surfaceTexture)

        cameraController.open(lens.cameraId, surface, size, object : CameraController.Listener {
            override fun onOpened(characteristics: CameraCharacteristics, frameSize: Size) {
                handler.post {
                    isCameraOpen = true
                    val sensorOrientation =
                        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                    currentLens = lens.copy(sensorOrientationDegrees = sensorOrientation)
                    this@GLRenderThread.frameSize = frameSize
                }
            }

            override fun onError(message: String, fatal: Boolean) {
                Log.w(TAG, "Camera error: $message (fatal=$fatal)")
                handler.post {
                    isCameraOpen = false
                    if (shouldCameraBeOpen()) {
                        handler.postDelayed({ recomputeCameraDesire() }, RETRY_DELAY_MS)
                    }
                }
            }
        })
    }

    private fun closeCameraNow() {
        cameraController.close()
        isCameraOpen = false
    }

    companion object {
        private const val TAG = "GLRenderThread"
        private const val RETRY_DELAY_MS = 3000L
    }
}
