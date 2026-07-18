package com.medicineboxnotes.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID

@Composable
fun CameraCaptureDialog(onDismiss: () -> Unit, onCaptured: (Uri) -> Unit) {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var cameraReady by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    DisposableEffect(Unit) { onDispose { cameraProvider?.unbindAll() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.camera_title)) },
        text = {
            Column {
                if (!granted) Text(stringResource(R.string.camera_permission))
                else AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { view ->
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                runCatching {
                                    val provider = providerFuture.get()
                                    cameraProvider = provider
                                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                                    imageCapture.targetRotation = view.display.rotation
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                                    cameraReady = true
                                }.onFailure { cameraError = it.message ?: context.getString(R.string.camera_unavailable) }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    }, modifier = Modifier.fillMaxWidth().height(420.dp),
                )
                if (granted && !cameraReady && cameraError == null) {
                    Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.camera_starting), style = MaterialTheme.typography.bodySmall)
                }
                if (capturing) {
                    Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.photo_saving), style = MaterialTheme.typography.bodySmall)
                }
                cameraError?.let { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.camera_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                capturing = true; cameraError = null
                val file = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
                imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        capturing = false
                        if (file.length() > 0L) onCaptured(Uri.fromFile(file))
                        else cameraError = context.getString(R.string.photo_empty)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        capturing = false; file.delete(); cameraError = exception.message
                    }
                })
            }, enabled = granted && cameraReady && !capturing) { Icon(Icons.Rounded.Camera, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.capture)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
