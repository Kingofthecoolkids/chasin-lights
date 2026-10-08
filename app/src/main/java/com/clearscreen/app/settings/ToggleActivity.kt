package com.clearscreen.app.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
 *
 * Everything is wrapped defensively: this activity has no UI of its own to show an error in, and
 * a `Theme.NoDisplay` activity that throws before finishing is exactly the kind of crash a user
 * sees as "app has stopped" with no further information. If anything here fails, fall back to
 * opening the full Setup screen rather than taking the whole process down.
 */
class ToggleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            toggleOrRedirect()
        } catch (e: Exception) {
            Log.e(TAG, "Toggle failed, falling back to Setup", e)
            try {
                startActivity(Intent(this, SetupActivity::class.java))
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback to Setup also failed", e2)
            }
        }
        finish()
    }

    private fun toggleOrRedirect() {
        val repository = SettingsRepository(applicationContext)
        val current = runBlocking { repository.settingsFlow.first() }

        if (!current.hasActivatedOnce) {
            // First ever launch: nothing to toggle yet, go straight to the full setup flow.
            startActivity(Intent(this, SetupActivity::class.java))
        } else {
            runBlocking { repository.update { it.copy(effectOn = !it.effectOn) } }
        }
    }

    companion object {
        private const val TAG = "ToggleActivity"
    }
}
