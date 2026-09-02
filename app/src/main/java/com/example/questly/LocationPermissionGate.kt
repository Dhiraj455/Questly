package com.example.questly

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun LocationPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    if (hasPermission) {
        content()
    } else {
        Column(Modifier.padding(24.dp)) {
            Text("Questly needs your location to find nearby challenges.")
            Button(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                Text("Grant location")
            }
        }
    }
}
