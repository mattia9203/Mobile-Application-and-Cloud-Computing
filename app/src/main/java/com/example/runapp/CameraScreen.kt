package com.example.runapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    // 1. Setup CameraX variables
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // We need an "ImageCapture" object to actually take the photo
    val imageCapture = remember { ImageCapture.Builder().build() }

    // 2. The View for the Camera Preview
    // AndroidView lets us use "Old Android" views (like Camera) inside Compose
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture // Bind our capture object
                        )
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Binding failed", e)
                    }
                }, executor)
                previewView
            }
        )

        // 3. The "Shutter" Button
        IconButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 50.dp)
                .size(80.dp)
                .background(Color.White, CircleShape)
                .border(4.dp, Color.Gray, CircleShape),
            onClick = {
                takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    onImageCaptured = onImageCaptured,
                    onError = onError
                )
            }
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = "Take Photo",
                tint = Color.Black,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

// 4. Helper function to capture the image
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            // Convert the raw image to a Bitmap we can use
            val bitmap = imageProxyToBitmap(image)
            image.close() // ALWAYS close the image!
            onImageCaptured(bitmap)
        }

        override fun onError(exception: ImageCaptureException) {
            onError(exception)
        }
    })
}

// 5. Helper to convert format
fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    // Rotate the image if needed (Samsung phones often need this)
    val matrix = Matrix()
    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}