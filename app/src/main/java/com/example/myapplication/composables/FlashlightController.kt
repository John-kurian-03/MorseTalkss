package com.example.myapplication.composables

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FlashlightController(private val context: Context) {

    private var cameraControl: androidx.camera.core.CameraControl? = null

    init {
        initializeCameraControl()
    }

    private fun initializeCameraControl() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val camera = cameraProvider.bindToLifecycle(
                context as androidx.lifecycle.LifecycleOwner,
                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            )
            cameraControl = camera.cameraControl
        }, ContextCompat.getMainExecutor(context))
    }

    fun turnOnFlashlight() {
        cameraControl?.enableTorch(true)
    }

    fun turnOffFlashlight() {
        cameraControl?.enableTorch(false)
    }

    fun transmitMorseCode(morseCode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            for (char in morseCode) {
                when (char) {
                    '.' -> blink(200)
                    '-' -> blink(600)
                    ' ' -> delay(600)
                }
            }
        }
    }

    private suspend fun blink(duration: Long) {
        turnOnFlashlight()
        delay(duration)
        turnOffFlashlight()
        delay(200)
    }
}
