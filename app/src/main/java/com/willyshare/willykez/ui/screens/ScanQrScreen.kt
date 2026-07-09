package com.willyshare.willykez.ui.screens

import android.Manifest
import android.graphics.ImageFormat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.willyshare.willykez.ui.PulseViewModel
import com.willyshare.willykez.ui.InPageHeader
import com.willyshare.willykez.ui.theme.SleekBg
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant
import com.willyshare.willykez.ui.theme.SleekPrimary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanQrScreen(
    viewModel: PulseViewModel,
    onNavigate: (String) -> Unit
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var scanResultMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (cameraPermission.status.isGranted) {
                CameraPreviewWithScanner(
                    isProcessing = isProcessing,
                    onDecoded = { raw ->
                        if (!isProcessing) {
                            isProcessing = true
                            val ok = viewModel.applyScannedPayload(raw)
                            if (ok) {
                                // This screen is only ever reached from Receive now - a
                                // successful scan means "pull the sender's queued cart,"
                                // never "push mine" (Send shows its own QR instead of
                                // scanning). Progress flows into receiveProgress, which the
                                // Receive screen already displays, so just hand off and go
                                // back there instead of routing through Transferring/Select.
                                viewModel.startPullSession { }
                                onNavigate("receive")
                            } else {
                                scanResultMessage = "That QR isn't a valid Pulse pairing code."
                                isProcessing = false
                            }
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(240.dp)
                        .border(3.dp, SleekPrimary, RoundedCornerShape(24.dp))
                )

                InPageHeader(
                    title = "Scan pairing QR",
                    showBack = true,
                    onBack = { onNavigate("receive") },
                    modifier = Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.35f)),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = scanResultMessage ?: "Point your camera at the sender's pairing QR code",
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                InPageHeader(
                    title = "Scan pairing QR",
                    showBack = true,
                    onBack = { onNavigate("receive") },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(com.willyshare.willykez.ui.PulseIcons.Camera, contentDescription = null, tint = SleekOnSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Camera permission needed", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Pulse needs camera access to scan pairing QR codes.",
                        fontSize = 13.sp,
                        color = SleekOnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { cameraPermission.launchPermissionRequest() },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) {
                        Text("Allow camera", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithScanner(
    isProcessing: Boolean,
    onDecoded: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reader = remember { MultiFormatReader() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                    decodeFrame(imageProxy, reader, isProcessing, onDecoded)
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }
}

private fun decodeFrame(
    imageProxy: ImageProxy,
    reader: MultiFormatReader,
    isProcessing: Boolean,
    onDecoded: (String) -> Unit
) {
    if (isProcessing || imageProxy.format != ImageFormat.YUV_420_888) {
        imageProxy.close()
        return
    }
    try {
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val data = ByteArray(yBuffer.remaining())
        yBuffer.get(data)
        val source = PlanarYUVLuminanceSource(
            data, imageProxy.width, imageProxy.height,
            0, 0, imageProxy.width, imageProxy.height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decode(bitmap)
        onDecoded(result.text)
    } catch (_: NotFoundException) {
        // No QR code in this frame - expected most of the time, keep scanning.
    } catch (_: Exception) {
    } finally {
        reader.reset()
        imageProxy.close()
    }
}
