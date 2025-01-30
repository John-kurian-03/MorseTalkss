package com.example.myapplication
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.LottieConstants
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieComposition
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.composables.CameraPreviewScreen
import com.example.myapplication.composables.FlashlightController
import com.example.myapplication.composables.TextToMorse
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader


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
        if (OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "OpenCV initialization succeeded")
        } else {
            Log.d("OpenCV", "OpenCV initialization failed")
        }

        setTheme(android.R.style.Theme_NoTitleBar_Fullscreen)
        setContent {
            var showSplashScreen by remember { mutableStateOf(true) }
            MyApplicationTheme {  // Use your original theme here
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplashScreen) {
                        MyLottieSplashScreen { showSplashScreen = false }
                    } else {
                        HomeScreen { requestCameraPermission() }
                    }
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
                 // Request camera permission
                isCameraPreviewVisible = true
                onRequestPermission() // Show the camera preview
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

@Composable
fun MyLottie() {

    val preLoaderLottie by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.splash_animation)
    )

    val preLoaderProgress by animateLottieCompositionAsState(
        preLoaderLottie,
        isPlaying = true,
        iterations = LottieConstants.IterateForever,
        speed = 1.5f
    )

    LottieAnimation(
        composition = preLoaderLottie,
        progress = { preLoaderProgress.coerceIn(0f, 0.9f) },
        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f, translationY = (-300f))
    )
}

@Composable
fun MyLottieSplashScreen(onSplashComplete: () -> Unit) {
    // Use a custom font
    val customFont = FontFamily(Font(R.font.del)) // Replace with your font file name

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White), // Optional background color
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        // Fancy Gradient Text
        Text(
            text = "Morse Talk",
            style = TextStyle(
                fontSize = 60.sp, // Large font size
                fontFamily = customFont, // Apply the custom font
                fontWeight = FontWeight.Bold, // Bold text
                brush = Brush.linearGradient( // Gradient effect
                    colors = listOf(Color.Cyan, Color.Magenta)
                ),
                shadow = Shadow( // Add shadow for depth
                    color = Color.Gray,
                    //offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                    blurRadius = 4f
                )
            ),

            modifier = Modifier.padding(bottom = 2.dp, top = 90.dp)

            // Space between text and animation
        )

        // Lottie animation
        MyLottie()
    }

    // Navigate to HomeScreen after a delay
    LaunchedEffect(Unit) {
        delay(2750) // Adjust delay as needed
        onSplashComplete() // Signal to move to HomeScreen
    }
}
