package com.clearscreen.app.wallpaper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.clearscreen.app.data.SettingsRepository
import com.clearscreen.app.render.GLRenderThread
import com.clearscreen.app.util.BatteryMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The live wallpaper engine. Its one real job, beyond wiring the pieces together, is the rule
 * the whole project hinges on: the camera is open if and only if this engine is visible, has
 * permission, and isn't battery-paused -- see [GLRenderThread.recomputeCameraDesire].
 */
class ClearScreenEngine(service: WallpaperService) : service.Engine() {

    private val appContext: Context = service.applicationContext
    private val renderThread = GLRenderThread(appContext)
    private val settingsRepository = SettingsRepository(appContext)
    private val engineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var batteryMonitor: BatteryMonitor? = null
    private var lastKnownBatteryThreshold = 15

    override fun onCreate(surfaceHolder: SurfaceHolder) {
        super.onCreate(surfaceHolder)
        setTouchEventsEnabled(false)

        engineScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                lastKnownBatteryThreshold = settings.batterySaverThresholdPercent
                renderThread.updateSettings(settings)
            }
        }

        batteryMonitor = BatteryMonitor(appContext) { percent, powerSaveMode ->
            renderThread.setBatteryPaused(powerSaveMode || isBelowThreshold(percent))
        }.also { it.start() }
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
        super.onSurfaceCreated(holder)
        renderThread.onSurfaceCreated(holder)
    }

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.onSurfaceChanged(holder, format, width, height)
        renderThread.onSurfaceChanged(holder, width, height, currentDisplayRotationDegrees())
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
        super.onSurfaceDestroyed(holder)
        renderThread.onSurfaceDestroyed()
    }

    override fun onVisibilityChanged(visible: Boolean) {
        super.onVisibilityChanged(visible)
        if (visible) {
            renderThread.setCameraPermissionGranted(hasCameraPermission())
        }
        // This single call is what opens or closes the camera -- see class doc.
        renderThread.setVisible(visible)
    }

    override fun onDestroy() {
        super.onDestroy()
        engineScope.cancel() // also cancels the settings-collection coroutine
        batteryMonitor?.stop()
        renderThread.shutdown()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun isBelowThreshold(percent: Int): Boolean =
        lastKnownBatteryThreshold > 0 && percent <= lastKnownBatteryThreshold

    @Suppress("DEPRECATION")
    private fun currentDisplayRotationDegrees(): Int {
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return windowManager.defaultDisplay.rotation * 90
    }
}
