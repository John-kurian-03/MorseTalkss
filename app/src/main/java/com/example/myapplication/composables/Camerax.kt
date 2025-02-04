package com.example.myapplication.composables

import android.content.Context
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color


@Composable
fun CameraPreviewScreen(onCameraControlReady: (CameraControl) -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }

    // State to hold CameraControl
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // State to hold brightness level
    var brightnessLevel by remember { mutableStateOf(0.0) }

    // Set up CameraX
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()

        // Add Image Analysis to capture frames
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    brightnessLevel = analyzeBrightness(imageProxy)
                    imageProxy.close()
                }
            }

        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.Builder().requireLensFacing(lensFacing).build(),
            preview,
            imageAnalyzer // Add the analyzer here
        )

        cameraControl = camera.cameraControl
        preview.setSurfaceProvider(previewView.surfaceProvider)

        cameraControl?.let(onCameraControlReady)
    }

    // Display camera preview and brightness
    Column {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight / 3)
                .offset(y = 140.dp)
        )
        Text(
            text = if (brightnessLevel > 50) "Flash Detected!" else "No Flash Detected",
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

private fun analyzeBrightness(imageProxy: ImageProxy): Double {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val width = imageProxy.width
    val height = imageProxy.height

    // Convert to OpenCV Mat (YUV to Gray)
    val mat = Mat(height, width, CvType.CV_8UC1)
    mat.put(0, 0, bytes)

    val grayMat = Mat()
    Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_YUV2GRAY_420)

    // Focus on the center of the frame (ROI)
    val centerX = width / 2
    val centerY = height / 2
    val roiSize = 100 // Size of the region to focus on
    val roi = grayMat.submat(
        centerY - roiSize / 2,
        centerY + roiSize / 2,
        centerX - roiSize / 2,
        centerX + roiSize / 2
    )

    // Apply thresholding to detect bright spots (flashlight)
    val thresholdMat = Mat()
    Imgproc.threshold(roi, thresholdMat, 200.0, 255.0, Imgproc.THRESH_BINARY)

    // Count non-zero pixels (indicates brightness spikes)
    val brightPixels = Core.countNonZero(thresholdMat)

    // Return the number of bright pixels as brightness indicator
    return brightPixels.toDouble()
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }
