package com.example.myapplication.composables

import android.content.Context
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FlashlightController(private val context: Context) {

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraId: String? by lazy {
        cameraManager.cameraIdList.find { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    // Turns the flashlight on
    fun turnOnFlashlight() {
        cameraId?.let {
            cameraManager.setTorchMode(it, true)
        }
    }

    // Turns the flashlight off
    fun turnOffFlashlight() {
        cameraId?.let {
            cameraManager.setTorchMode(it, false)
        }
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