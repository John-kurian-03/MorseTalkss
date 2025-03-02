package com.example.myapplication.composables

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.min
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.example.myapplication.composables.decodeMorse
import com.example.myapplication.composables.morseToText

@Composable
fun CameraPreviewScreen(
    onCameraControlReady: (CameraControl) -> Unit,
    onDecodedMessage: (String) -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableFloatStateOf(1f) }
    val maxZoom = 5f  // Adjust based on camera capabilities

    // Detection states
    var brightnessLevel by remember { mutableDoubleStateOf(0.0) }
    var flashDurations by remember { mutableStateOf(listOf<Long>()) }
    var gapDurations by remember { mutableStateOf(listOf<Long>()) }
    var isFlashOn by remember { mutableStateOf(false) }
    var previousBrightness by remember { mutableDoubleStateOf(0.0) }
    var lastFlashEndTime by remember { mutableStateOf<Long?>(null) }

    // Remove the in-screen decoded message display states (they will be sent back to HomeScreen)
    // var morseText by remember { mutableStateOf("") }
    // var decodedMessage by remember { mutableStateOf("") }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    CoroutineScope(Dispatchers.Default).launch {
                        // Use ROI-based brightness analysis (unchanged)
                        val brightness = analyzeBrightness(imageProxy)
                        brightnessLevel = brightness

                        // Compute temporal differential
                        val temporalDelta = brightness - previousBrightness
                        previousBrightness = brightness

                        val deltaThreshold = 20.0 // Tune as needed
                        val currentTime = System.currentTimeMillis()

                        if (brightness > 80 && temporalDelta > deltaThreshold) {
                            if (!isFlashOn && lastFlashEndTime != null) {
                                val gap = currentTime - lastFlashEndTime!!
                                gapDurations = gapDurations + gap
                            }
                            if (!isFlashOn) {
                                isFlashOn = true
                            }
                        } else {
                            if (isFlashOn) {
                                val flashDuration = currentTime - (lastFlashEndTime ?: currentTime)
                                flashDurations = flashDurations + flashDuration
                                isFlashOn = false
                                lastFlashEndTime = currentTime
                            } else {
                                lastFlashEndTime = currentTime
                            }
                        }
                        imageProxy.close()
                    }
                }
            }

        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.Builder().requireLensFacing(lensFacing).build(),
            preview,
            imageAnalyzer
        )

        cameraControl = camera.cameraControl
        cameraControl?.setZoomRatio(1.5f)
        preview.setSurfaceProvider(previewView.surfaceProvider)
        cameraControl?.let(onCameraControlReady)
    }

    // When there is a long gap (indicating end of a Morse letter/word), decode and pass the decoded message
    LaunchedEffect(gapDurations, flashDurations) {
        if (flashDurations.isNotEmpty() && gapDurations.isNotEmpty()) {
            val lastGap = gapDurations.lastOrNull() ?: 0L
            if (lastGap > 1500L) {  // Adjust threshold as needed
                val morseText = decodeMorse(flashDurations, gapDurations)
                val decodedMessage = morseToText(morseText)
                // Pass the decoded message to the parent
                onDecodedMessage(decodedMessage)
                // Clear the lists for the next message
                flashDurations = listOf()
                gapDurations = listOf()
            }
        }
    }

    // UI rendering (same as before, but without showing the decoded text)
    Box(
        modifier = Modifier
            .then(
                if (isFullScreen)
                    Modifier.fillMaxSize()
                else Modifier
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
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())

        // Fixed square ROI overlay
        Canvas(modifier = Modifier.matchParentSize()) {
            val roiSize = minOf(size.width, size.height) * 0.33f
            val roiX = (size.width - roiSize) / 2
            val roiY = (size.height - roiSize) / 2
            drawRect(
                color = Color.Red,
                topLeft = Offset(roiX, roiY),
                size = Size(roiSize, roiSize),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        if (isFullScreen) {
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
}

// Uses a fixed square ROI for brightness analysis
private fun analyzeBrightness(imageProxy: ImageProxy): Double {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val width = imageProxy.width
        val height = imageProxy.height

        val mat = Mat(height, width, CvType.CV_8UC1)
        mat.put(0, 0, bytes)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_YUV2GRAY_420)

        val roiSize = minOf(width, height) / 3
        val roiX = (width - roiSize) / 2
        val roiY = (height - roiSize) / 2

        if (roiX < 0 || roiY < 0 || roiX + roiSize > grayMat.cols() || roiY + roiSize > grayMat.rows()) {
            val brightness = Core.mean(grayMat).`val`[0]
            grayMat.release()
            mat.release()
            return brightness
        }

        val roi = grayMat.submat(Rect(roiX, roiY, roiSize, roiSize))

        // Apply CLAHE for better contrast
        val clahe = Imgproc.createCLAHE(3.0)
        val enhancedRoi = Mat()
        clahe.apply(roi, enhancedRoi)
        // Note: clahe.release() not available in OpenCV 4.9.0 for Android

        val brightness = Core.mean(enhancedRoi).`val`[0]

        roi.release()
        enhancedRoi.release()
        grayMat.release()
        mat.release()

        brightness
    } catch (e: Exception) {
        0.0
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }
