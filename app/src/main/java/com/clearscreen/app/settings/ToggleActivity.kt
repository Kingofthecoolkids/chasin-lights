package com.clearscreen.app.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.clearscreen.app.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The actual home-screen launcher icon target. Themed `Theme.NoDisplay` -- it never shows any
 * UI, it just flips the on/off flag and finishes immediately, which is what makes "tap icon ->
 * instantly toggle" possible with no system dialog after the first-ever activation.
 *
 * `Theme.NoDisplay` requires the activity to finish (or hand off to another activity) before
 * it would otherwise reach onResume, so the DataStore read/write here is deliberately blocking
 * rather than the usual coroutine-collect pattern -- it's a few bytes on local disk, fast enough
 * that blocking the main thread for it is the right tradeoff against the alternative (a visible
 * flash of UI, or NoDisplay throwing because nothing finished it in time).
 */
class ToggleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = SettingsRepository(applicationContext)
        val current = runBlocking { repository.settingsFlow.first() }

        if (!current.hasActivatedOnce) {
            // First ever launch: nothing to toggle yet, go straight to the full setup flow.
            startActivity(Intent(this, SetupActivity::class.java))
        } else {
            runBlocking { repository.update { it.copy(effectOn = !it.effectOn) } }
        }

        finish()
    }
}
