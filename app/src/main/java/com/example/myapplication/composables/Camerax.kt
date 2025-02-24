package com.example.myapplication.composables

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraPreviewScreen(onCameraControlReady: (CameraControl) -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableFloatStateOf(1f) }
    val maxZoom = 5f  // Adjust this value based on your camera's capabilities

    // Existing flash detection states
    var brightnessLevel by remember { mutableDoubleStateOf(0.0) }
    var flashStartTime by remember { mutableStateOf<Long?>(null) }
    var flashEndCandidateTime by remember { mutableStateOf<Long?>(null) }
    var flashDurations by remember { mutableStateOf(listOf<Long>()) }
    var isFlashOn by remember { mutableStateOf(false) }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    val brightness = analyzeBrightness(imageProxy)
                    brightnessLevel = brightness

                    val threshold = 110  // Increased threshold for daylight usage
                    val debounceDuration = 50L // Debounce time in milliseconds
                    val currentTime = System.currentTimeMillis()

                    if (brightness > threshold) {
                        flashEndCandidateTime = null
                        if (!isFlashOn) {
                            flashStartTime = currentTime
                            isFlashOn = true
                        }
                    } else {
                        if (isFlashOn && flashStartTime != null) {
                            if (flashEndCandidateTime == null) {
                                flashEndCandidateTime = currentTime
                            } else if (currentTime - flashEndCandidateTime!! > debounceDuration) {
                                val duration = currentTime - flashStartTime!!
                                flashDurations = flashDurations + duration
                                flashStartTime = null
                                isFlashOn = false
                                flashEndCandidateTime = null
                            }
                        }
                    }
                    imageProxy.close()
                }
            }

        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.Builder().requireLensFacing(lensFacing).build(),
            preview,
            imageAnalyzer
        )

        cameraControl = camera.cameraControl
        preview.setSurfaceProvider(previewView.surfaceProvider)
        cameraControl?.let(onCameraControlReady)
    }

    // Wrap the camera preview in a Box with gesture detection and full screen toggle
    Box(
        modifier = Modifier
            .then(
                if (isFullScreen)
                    Modifier.fillMaxSize()
                else
                    Modifier
                        .fillMaxWidth()
                        .height(screenHeight / 3)
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    val newZoom = (currentZoom * zoomChange).coerceIn(1f, maxZoom)
                    if (newZoom != currentZoom) {
                        currentZoom = newZoom
                        cameraControl?.setZoomRatio(newZoom)
                    }
                }
            }
            .clickable {
                if (!isFullScreen) {
                    isFullScreen = true
                }
            }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.matchParentSize()
        )
        if (isFullScreen) {
            // Display an X icon in the top-right corner to exit full screen mode.
            IconButton(
                onClick = { isFullScreen = false },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close full screen",
                    tint = Color.White
                )
            }
        }
    }

    // Show additional UI (flash detection texts) only when not in full screen mode.
    if (!isFullScreen) {
        Column {
            Text(
                text = if (isFlashOn) "Flash Detected!" else "No Flash Detected",
                color = Color.White
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = "Flash Durations (ms):", color = Color.White)
                flashDurations.forEach { duration ->
                    Text(text = "$duration ms", color = Color.White)
                }
            }
        }
    }
}

private fun analyzeBrightness(imageProxy: ImageProxy): Double {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val width = imageProxy.width
    val height = imageProxy.height

    val mat = Mat(height, width, CvType.CV_8UC1)
    mat.put(0, 0, bytes)
    val grayMat = Mat()
    Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_YUV2GRAY_420)

    return Core.mean(grayMat).`val`[0]
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }
