package com.clearscreen.app.settings

import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.clearscreen.app.data.WallpaperSettings

/**
 * Live preview of the calibrated feed, embedded in the setup screen. Opens/closes the camera with
 * the Activity's own lifecycle (onResume/onPause) -- same "camera only while actually visible"
 * discipline as the wallpaper itself, just driven by a regular Activity lifecycle instead of
 * WallpaperService visibility.
 */
@Composable
fun CameraPreview(settingsState: State<WallpaperSettings>, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var rendererHolder: PreviewRenderer? by remember { mutableStateOf(null) }
    var viewHolder: GLSurfaceView? by remember { mutableStateOf(null) }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(320.dp),
        factory = { context ->
            val renderer = PreviewRenderer(context) { settingsState.value }
            val view = GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
            rendererHolder = renderer
            viewHolder = view
            view
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewHolder?.onResume()
                    rendererHolder?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    rendererHolder?.onPause()
                    viewHolder?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val view = viewHolder
            val renderer = rendererHolder
            if (view != null && renderer != null) {
                view.queueEvent { renderer.release() }
            }
        }
    }
}
