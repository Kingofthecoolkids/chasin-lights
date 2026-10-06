package com.clearscreen.app.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clearscreen.app.R

@Composable
fun PermissionScreen(permanentlyDenied: Boolean, onRequestPermission: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.padding(16.dp))

        if (permanentlyDenied) {
            Text(stringResource(R.string.permission_denied_settings), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.permission_open_settings))
            }
        } else {
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.permission_grant))
            }
        }
    }
}
