package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.composables.CameraPreviewScreen
import com.example.myapplication.composables.FlashlightController
import com.example.myapplication.composables.TextToMorse
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("CameraPermission", "Permission granted")
                // Implement camera-related code (set camera preview)
                setCameraPreview()
            } else {
                Log.d("CameraPermission", "Permission denied")
                // Handle denied permission
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {  // Use your original theme here
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen { requestCameraPermission() }
                }
            }
        }
    }

    private fun requestCameraPermission() {
        Log.d("CameraPermission", "Checking for permission")
        when (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        )) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d("CameraPermission", "Permission already granted")
                setCameraPreview()
            }
            else -> {
                Log.d("CameraPermission", "Permission not granted, requesting")
                cameraPermissionRequest.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setCameraPreview() {
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen { requestCameraPermission() }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onRequestPermission: () -> Unit) {
    var isCameraPreviewVisible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    var messages by remember { mutableStateOf(listOf<String>()) }

    Column(
        modifier = Modifier
            .background(Color.DarkGray)
            .padding(top = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Morse Talk",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (isCameraPreviewVisible) {
            CameraPreviewScreen { cameraControl ->
                // Handle camera control here if needed
                Log.d("HomeScreen", "CameraControl received: $cameraControl")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .padding(8.dp)
            ) {
                items(messages) { message ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .background(Color.Green, shape = RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(bottom = 75.dp)
    ) {
        OutlinedTextField(
            value = text,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = { text = it },
            label = { Text("Enter message", color = Color.White) }
        )
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 12.5.dp)
    ) {
        Button(
            onClick = {
                if (text.isNotEmpty()) {
                    val textToMorse = TextToMorse()
                    val morseCode = textToMorse.translateToMorse(text)
                    val flashlightController = FlashlightController(context)
                    flashlightController.transmitMorseCode(morseCode)
                    messages = messages + text
                    text = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
        ) {
            Text(text = "Transmit", color = Color.DarkGray, fontSize = 16.sp)
        }

        Button(
            onClick = {
                onRequestPermission()  // Request camera permission
                isCameraPreviewVisible = true  // Show the camera preview
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
        ) {
            Text(text = "Receive", color = Color.DarkGray, fontSize = 16.sp)
        }
    }
}




@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MyApplicationTheme {  // Use your original theme here
        HomeScreen { }
    }
}
