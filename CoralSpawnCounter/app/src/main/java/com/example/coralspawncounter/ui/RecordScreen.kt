package com.example.coralspawncounter.ui

import android.view.Surface
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Composable
fun RecordScreen(onPreviewViewAvailable: (PreviewView) -> Unit) {
    CameraPermissionsRequester {
        Row {
            Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                Surface(modifier = Modifier.fillMaxHeight(0.8f)) {
                    CameraPreviewView(onPreviewViewAvailable)
                }
                Surface {
                    Text(text = "Secondary View")
                }
            }

            Surface {
                Text(text = "Some Camera Controls")
            }
        }

    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionsRequester(content: @Composable() () -> Unit){
    // Camera permission state
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    if (cameraPermissionState.status.isGranted) {
        content()
    } else {
        Column {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                // If the user has denied the permission but the rationale can be shown,
                // then gently explain why the app requires this permission
                "The camera is important for this app. Please grant the permission."
            } else {
                // If it's the first time the user lands on this feature, or the user
                // doesn't want to be asked again for this permission, explain that the
                // permission is required
                "Camera permission required for this feature to be available. Please grant the permission"
            }
            Text(textToShow)
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }
        }
    }
}

@Composable
fun CameraPreviewView(onPreviewViewAvailable: (PreviewView) -> Unit) {
    val coroutineScope = rememberCoroutineScope { Dispatchers.IO }
    AndroidView(
        factory = { context ->
            val previewView = PreviewView(context).apply {
                this.scaleType = PreviewView.ScaleType.FIT_CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }

            coroutineScope.launch {
                onPreviewViewAvailable(previewView)
            }

            previewView
        }
    )
}
