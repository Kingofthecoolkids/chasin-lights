package com.clearscreen.app.settings

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Size
import android.view.Surface
import com.clearscreen.app.camera.CameraController
import com.clearscreen.app.camera.CameraLensInfo
import com.clearscreen.app.data.WallpaperSettings
import com.clearscreen.app.render.CameraTextureRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Drives the same [CameraTextureRenderer]/calibration pipeline as the live wallpaper, but inside
 * a plain [GLSurfaceView] in the settings UI -- GLSurfaceView already manages its own EGL context,
 * so this is simpler than [com.clearscreen.app.render.GLRenderThread]. Only used while the setup
 * screen is actually on screen: [onPause]/[onResume] close and reopen the camera, same principle
 * as the wallpaper itself.
 */
class PreviewRenderer(
    private val context: Context,
    private val settingsProvider: () -> WallpaperSettings
) : GLSurfaceView.Renderer {

    private val cameraController = CameraController(context)
    private var textureRenderer: CameraTextureRenderer? = null
    private var lenses: List<CameraLensInfo> = emptyList()
    private var currentLens: CameraLensInfo? = null
    private var frameSize = Size(0, 0)
    private var hasRenderedFrame = false
    private var isCameraOpen = false
    private var viewWidth = 0
    private var viewHeight = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureRenderer = CameraTextureRenderer()
        lenses = cameraController.listRearLenses()
        hasRenderedFrame = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        openCamera()
    }

    override fun onDrawFrame(gl: GL10?) {
        val renderer = textureRenderer ?: return
        if (isCameraOpen) {
            renderer.updateTexImage()
            hasRenderedFrame = true
        }
        if (hasRenderedFrame) {
            val s = settingsProvider()
            renderer.draw(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                frameWidth = frameSize.width,
                frameHeight = frameSize.height,
                sensorOrientationDegrees = currentLens?.sensorOrientationDegrees ?: 90,
                displayRotationDegrees = 0, // the setup screen is portrait-only; see activity theme/manifest
                zoom = s.zoom,
                offsetXFraction = s.offsetX,
                offsetYFraction = s.offsetY,
                brightness = s.brightness,
                blurEnabled = s.blurEnabled
            )
        } else {
            GLES20.glViewport(0, 0, viewWidth, viewHeight)
            GLES20.glClearColor(0.04f, 0.05f, 0.06f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
    }

    /** Call from the hosting Activity/Compose lifecycle when the preview stops being visible. */
    fun onPause() {
        cameraController.close()
        isCameraOpen = false
    }

    /** Call when the preview becomes visible again. */
    fun onResume() {
        openCamera()
    }

    /** Deletes GL resources -- must be invoked via `glSurfaceView.queueEvent { }` so it runs on the GL thread. */
    fun release() {
        cameraController.close()
        isCameraOpen = false
        textureRenderer?.release()
        textureRenderer = null
    }

    private fun openCamera() {
        val renderer = textureRenderer ?: return
        if (viewWidth == 0 || viewHeight == 0) return
        if (lenses.isEmpty()) lenses = cameraController.listRearLenses()
        val requestedLensId = settingsProvider().lensId
        val lens = lenses.firstOrNull { it.cameraId == requestedLensId }
            ?: lenses.firstOrNull { !it.isUltrawide }
            ?: lenses.firstOrNull()
            ?: return

        val size = cameraController.choosePreviewSize(lens.cameraId, viewWidth, viewHeight)
        frameSize = size
        renderer.surfaceTexture.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(renderer.surfaceTexture)

        cameraController.open(lens.cameraId, surface, size, object : CameraController.Listener {
            override fun onOpened(
                characteristics: android.hardware.camera2.CameraCharacteristics,
                frameSize: Size
            ) {
                isCameraOpen = true
                val sensorOrientation =
                    characteristics.get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                currentLens = lens.copy(sensorOrientationDegrees = sensorOrientation)
                this@PreviewRenderer.frameSize = frameSize
            }

            override fun onError(message: String, fatal: Boolean) {
                isCameraOpen = false
            }
        })
    }

    fun availableLenses(): List<CameraLensInfo> {
        if (lenses.isEmpty()) lenses = cameraController.listRearLenses()
        return lenses
    }
}
