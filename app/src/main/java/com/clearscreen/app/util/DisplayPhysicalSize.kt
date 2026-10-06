package com.clearscreen.app.util

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/** Physical screen size derived from reported DPI. OEM xdpi/ydpi values are sometimes inaccurate,
 *  which is precisely why calibration has a manual/guided path on top of this auto-default. */
object DisplayPhysicalSize {

    data class PhysicalSizeMm(val widthMm: Float, val heightMm: Float)

    fun get(context: Context): PhysicalSizeMm {
        val metrics = currentDisplayMetrics(context)
        val widthMm = metrics.widthPixels / metrics.xdpi * MM_PER_INCH
        val heightMm = metrics.heightPixels / metrics.ydpi * MM_PER_INCH
        return PhysicalSizeMm(widthMm, heightMm)
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayMetrics(context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private const val MM_PER_INCH = 25.4f
}
