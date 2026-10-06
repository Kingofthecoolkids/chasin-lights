package com.clearscreen.app.render

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.view.Surface

/** A GL-renderable [EGLSurface] bound to an Android [Surface] (here, the wallpaper's SurfaceHolder surface). */
class WindowSurface(private val eglCore: EglCore, surface: Surface) {

    private var eglSurface: EGLSurface = eglCore.createWindowSurface(surface)

    fun makeCurrent() = eglCore.makeCurrent(eglSurface)

    fun swapBuffers(): Boolean = eglCore.swapBuffers(eglSurface)

    fun release() {
        eglCore.releaseSurface(eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}
