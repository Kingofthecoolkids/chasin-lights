package com.clearscreen.app.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import java.io.File

/**
 * Captures a still image of whatever wallpaper is active right before ClearScreen takes over, so
 * toggling "off" later can show it back instead of a blank screen. This only works for a static
 * wallpaper -- [WallpaperManager.peekDrawable] has no equivalent for a currently-active *live*
 * wallpaper, so if the user's previous wallpaper was itself a live wallpaper, capture silently
 * does nothing and the "off" state falls back to a neutral background. There is no API available
 * to a normal (non-system) app that can restore a previous live wallpaper's actual behavior.
 */
object WallpaperSnapshot {
    private const val FILE_NAME = "previous_wallpaper.png"
    private const val TAG = "WallpaperSnapshot"

    fun capture(context: Context) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val drawable = wallpaperManager.peekDrawable() ?: return
            val bitmap = drawableToBitmap(drawable)
            File(context.filesDir, FILE_NAME).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not capture current wallpaper; off-state will use a neutral background", e)
        }
    }

    fun filePath(context: Context): String = File(context.filesDir, FILE_NAME).absolutePath

    fun exists(context: Context): Boolean = File(context.filesDir, FILE_NAME).exists()

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
