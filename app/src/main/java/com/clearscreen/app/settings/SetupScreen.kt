package com.clearscreen.app.settings

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clearscreen.app.camera.CameraCalibrationMath
import com.clearscreen.app.data.WallpaperSettings
import com.clearscreen.app.util.WallpaperSnapshot
import com.clearscreen.app.wallpaper.ClearScreenWallpaperService

@Composable
fun SetupScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val settingsState = viewModel.settings.collectAsState()
    val settings = settingsState.value
    var guidedModeActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ClearScreen setup", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

        Box {
            CameraPreview(settingsState = settingsState, modifier = Modifier.fillMaxWidth())
            if (guidedModeActive) {
                GuidedCalibrationOverlay(modifier = Modifier.fillMaxWidth().height(320.dp))
            }
        }

        val activateLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.markActivated()
            }
        }

        Button(
            onClick = {
                // Snapshot whatever wallpaper is active right now, before we replace it -- this
                // is what lets the home-screen icon toggle "off" show something other than a
                // blank screen later, with no further system dialog involved.
                WallpaperSnapshot.capture(context)
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, ClearScreenWallpaperService::class.java)
                )
                activateLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (settings.hasActivatedOnce) "Re-activate ClearScreen" else "Activate ClearScreen")
        }

        if (settings.hasActivatedOnce) {
            Text(
                "This is a one-time step. After this, tapping the ClearScreen icon on your " +
                    "home screen instantly turns the effect on or off -- no dialog, no picker.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (settings.effectOn) "Currently ON" else "Currently OFF")
                Switch(checked = settings.effectOn, onCheckedChange = viewModel::setEffectOn)
            }
        }

        HorizontalDivider()

        Text("Calibration", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

        if (guidedModeActive) {
            Text(
                "Hold the phone at arm's length in front of a straight edge (a door frame or " +
                    "table edge works well). Adjust zoom and offset until the edge lines up " +
                    "through the gap around the phone.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }

        LabeledSlider(
            label = "Zoom",
            value = settings.zoom,
            range = CameraCalibrationMath.MIN_ZOOM..CameraCalibrationMath.MAX_ZOOM,
            onValueChange = viewModel::setZoom
        )
        LabeledSlider(
            label = "Horizontal offset",
            value = settings.offsetX,
            range = -WallpaperSettings.OFFSET_RANGE..WallpaperSettings.OFFSET_RANGE,
            onValueChange = viewModel::setOffsetX
        )
        LabeledSlider(
            label = "Vertical offset",
            value = settings.offsetY,
            range = -WallpaperSettings.OFFSET_RANGE..WallpaperSettings.OFFSET_RANGE,
            onValueChange = viewModel::setOffsetY
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { guidedModeActive = !guidedModeActive },
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text(if (guidedModeActive) "Stop guided calibration" else "Start guided calibration")
            }
            OutlinedButton(onClick = { viewModel.resetToDefault() }) {
                Text("Reset to default")
            }
        }

        HorizontalDivider()

        Text("Extra settings", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

        LabeledSlider(
            label = "Dim overlay",
            value = settings.brightness,
            range = WallpaperSettings.BRIGHTNESS_MIN..1f,
            onValueChange = viewModel::setBrightness
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Blur (readability)")
            Switch(checked = settings.blurEnabled, onCheckedChange = viewModel::setBlurEnabled)
        }

        Text("Frame rate cap")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperSettings.FPS_CAP_OPTIONS.forEach { fps ->
                FilterChip(
                    selected = settings.fpsCap == fps,
                    onClick = { viewModel.setFpsCap(fps) },
                    label = { Text("$fps fps") }
                )
            }
        }

        LabeledSlider(
            label = "Pause below battery ${settings.batterySaverThresholdPercent}%",
            value = settings.batterySaverThresholdPercent.toFloat(),
            range = 0f..50f,
            onValueChange = { viewModel.setBatterySaverThreshold(it.toInt()) }
        )

        if (viewModel.lenses.size > 1) {
            Text("Lens")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.lenses.forEach { lens ->
                    FilterChip(
                        selected = settings.lensId == lens.cameraId ||
                            (settings.lensId == null && !lens.isUltrawide),
                        onClick = { viewModel.setLens(lens.cameraId) },
                        label = { Text(lens.displayName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label)
            Slider(value = value, onValueChange = onValueChange, valueRange = range)
        }
    }
}

@Composable
private fun GuidedCalibrationOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val guideColor = Color.White.copy(alpha = 0.6f)
        // Center crosshair.
        drawLine(
            guideColor,
            Offset(size.width / 2f, 0f),
            Offset(size.width / 2f, size.height),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            guideColor,
            Offset(0f, size.height / 2f),
            Offset(size.width, size.height / 2f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        // Edge margins to help line up a straight edge behind the phone.
        val margin = size.width * 0.1f
        drawLine(guideColor, Offset(margin, 0f), Offset(margin, size.height), strokeWidth = 2f)
        drawLine(
            guideColor,
            Offset(size.width - margin, 0f),
            Offset(size.width - margin, size.height),
            strokeWidth = 2f
        )
    }
}
