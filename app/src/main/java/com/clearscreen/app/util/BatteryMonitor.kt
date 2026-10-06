package com.clearscreen.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/** Watches battery percentage + system Power Saver mode and reports the combined "should pause" signal. */
class BatteryMonitor(private val context: Context, private val onStateChanged: (percent: Int, powerSaveMode: Boolean) -> Unit) {

    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                report(intent)
            }
        }
        receiver = newReceiver
        val initialIntent = context.registerReceiver(newReceiver, filter)
        initialIntent?.let { report(it) } ?: reportCurrentState()
    }

    fun stop() {
        receiver?.let {
            context.unregisterReceiver(it)
            receiver = null
        }
    }

    private fun report(intent: Intent) {
        val percent = batteryPercentFrom(intent) ?: currentBatteryPercent()
        onStateChanged(percent, isPowerSaveMode())
    }

    private fun reportCurrentState() {
        onStateChanged(currentBatteryPercent(), isPowerSaveMode())
    }

    private fun batteryPercentFrom(intent: Intent): Int? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100) / scale
    }

    private fun currentBatteryPercent(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isPowerSaveMode(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }
}
