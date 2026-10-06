package com.clearscreen.app.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class SetupActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var hasPermission by remember { mutableStateOf(isCameraPermissionGranted()) }
                    var askedOnce by remember { mutableStateOf(false) }
                    val lifecycleOwner = LocalLifecycleOwner.current

                    // The system permission dialog (and the app-info screen, for the "denied
                    // forever" path) both return control via onResume, not a direct callback --
                    // re-check permission state every time this screen comes back to the front.
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                hasPermission = isCameraPermissionGranted()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (hasPermission) {
                        LaunchedEffect(Unit) { viewModel.applyAutoCalibrationIfNeeded() }
                        SetupScreen(viewModel)
                    } else {
                        PermissionScreen(
                            permanentlyDenied = askedOnce && !hasPermission,
                            onRequestPermission = {
                                askedOnce = true
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun isCameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}
