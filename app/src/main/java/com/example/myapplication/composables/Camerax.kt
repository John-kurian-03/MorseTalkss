package com.example.myapplication.composables

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import org.opencv.imgproc.Imgproc
import org.opencv.core.*

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraPreviewScreen(onCameraControlReady: (CameraControl) -> Unit) {
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // Zoom
    var currentZoom by remember { mutableFloatStateOf(1.5f) }
    val maxZoom = 6f

    // ROI size
    var roiX by remember { mutableFloatStateOf(0f) }
    var roiY by remember { mutableFloatStateOf(0f) }
    var roiSize by remember { mutableFloatStateOf(0f) }
    var roiSizeRatio by remember { mutableIntStateOf(2) }

    var brightness by remember { mutableDoubleStateOf(0.0) }
    var flashStartTime by remember { mutableStateOf<Long?>(null) }
    var flashEndTime by remember { mutableStateOf<Long?>(null) }
    var flashDurations by remember { mutableStateOf(listOf<Long>()) }
    var isFlashOn by remember { mutableStateOf(false) }
    var previousBrightness by remember { mutableDoubleStateOf(0.0) }

    var maxTemporalDelta by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    CoroutineScope(Dispatchers.Default).launch {
                        val result = analyzeBrightness(imageProxy, roiSizeRatio)
                        brightness = result.brightness
                        roiX = result.roiX.toFloat()
                        roiY = result.roiY.toFloat()
                        roiSize = result.roiSize.toFloat()

                        if (previousBrightness == 0.0) {
                            previousBrightness = brightness
                        }

                        val temporalDelta = brightness - previousBrightness
                        maxTemporalDelta = max(maxTemporalDelta, temporalDelta)

                        previousBrightness = brightness

                        val deltaThreshold = 20.0

                        if (abs(temporalDelta) > deltaThreshold) {
                            val currentTime = System.currentTimeMillis()

                            if (temporalDelta > deltaThreshold && !isFlashOn) {
                                flashEndTime?.let { endTime ->
                                    val offDuration = currentTime - endTime
                                    if (offDuration > 100) {
                                        flashDurations = flashDurations + (-offDuration)
                                        flashStartTime = currentTime
                                        isFlashOn = true
                                    }
                                } ?: run {
                                    flashStartTime = currentTime
                                    isFlashOn = true
                                }

                            } else if (temporalDelta < -deltaThreshold && isFlashOn) {
                                flashStartTime?.let { startTime ->
                                    val onDuration = currentTime - startTime
                                    flashDurations = flashDurations + onDuration
                                }

                                flashEndTime = currentTime
                                isFlashOn = false
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
        cameraControl?.setZoomRatio(currentZoom)
        preview.setSurfaceProvider(previewView.surfaceProvider)
        cameraControl?.let(onCameraControlReady)

        // Disable auto adjustments
        val camera2Control = Camera2CameraControl.from(cameraControl!!)
        val captureRequestOptions = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 200)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 100000000L)
            //.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            //.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
            .build()

        camera2Control.captureRequestOptions = captureRequestOptions
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    val newZoom = (currentZoom * zoomChange).coerceIn(1f, maxZoom)
                    if (newZoom != currentZoom) {
                        currentZoom = newZoom
                        cameraControl?.setZoomRatio(newZoom)
                    }
                }
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())

        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                color = Color.Red,
                topLeft = Offset(roiX, roiY),
                size = Size(roiSize, roiSize),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }

    Column {
        Text(
            text = if (isFlashOn) "Flash Detected!" else "No Flash Detected (new)",
            color = Color.White
        )
        Text("Brightness: $brightness")
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = "Flash Durations (ms):", color = Color.White)
            flashDurations.forEach { duration ->
                Text(text = "$duration ms", color = Color.White)
            }
        }
    }
}

data class BrightnessResult(val brightness: Double, val roiX: Int, val roiY: Int, val roiSize: Int)

private fun analyzeBrightness(imageProxy: ImageProxy, roiSizeRatio: Int): BrightnessResult {
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

        val clahe = Imgproc.createCLAHE(3.0)
        val enhancedMat = Mat()
        clahe.apply(grayMat, enhancedMat)

        Imgproc.GaussianBlur(enhancedMat, enhancedMat, Size(5.0, 5.0), 0.0)

        val roiSize = minOf(width, height) / roiSizeRatio
        val roiX = (width - roiSize) / 2
        val roiY = (height - roiSize) / 2

        if (roiX < 0 || roiY < 0 || roiX + roiSize > enhancedMat.cols() || roiY + roiSize > enhancedMat.rows()) {
            val brightness = Core.mean(enhancedMat).`val`[0]
            enhancedMat.release()
            grayMat.release()
            mat.release()
            return BrightnessResult(brightness, roiX, roiY, roiSize)
        }

        val roi = enhancedMat.submat(Rect(roiX, roiY, roiSize, roiSize))
        val brightness = Core.mean(roi).`val`[0]

        // Find the brightest pixel in the ROI
        val minMax = Core.minMaxLoc(roi)
        val maxBrightness = minMax.maxVal

        // Convert to 1D array
        val roiPixels = ByteArray(roi.total().toInt())
        roi.get(0, 0, roiPixels)

        // Define threshold (e.g., 70% of max brightness)
        val minBrightnessThreshold = maxBrightness * 0.7

        // Filter pixels above threshold and compute mean
        val brightPixels = roiPixels.filter { it.toInt() and 0xFF > minBrightnessThreshold }
        val avgBrightness = if (brightPixels.isNotEmpty()) {
            brightPixels.sumOf { it.toInt() and 0xFF } / brightPixels.size.toDouble()
        } else {
            0.0
        }

        roi.release()
        enhancedMat.release()
        grayMat.release()
        mat.release()

        return BrightnessResult(avgBrightness, roiX, roiY, roiSize)

    } catch (e: Exception) {
        BrightnessResult(0.0, 0, 0, 0)
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