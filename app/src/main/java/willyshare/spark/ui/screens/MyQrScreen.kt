package willyshare.spark.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import willyshare.spark.net.QrPairing
import willyshare.spark.ui.AuroraBackground
import willyshare.spark.ui.PulseViewModel
import willyshare.spark.ui.InPageHeader
import willyshare.spark.ui.theme.SleekOnSurface
import willyshare.spark.ui.theme.SleekOnSurfaceVariant
import willyshare.spark.ui.theme.SleekOutline
import willyshare.spark.ui.theme.SleekPrimary

@Composable
fun MyQrScreen(viewModel: PulseViewModel, onNavigate: (String) -> Unit) {
    val payload by viewModel.myQrPayload.collectAsState()
    val senderConnected by viewModel.senderConnected.collectAsState()
    val deviceName by viewModel.thisDeviceName.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshMyQrPayload() }
    LaunchedEffect(senderConnected) { if (senderConnected) onNavigate("receive") }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        AuroraBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                InPageHeader(title = "My Pairing QR", showBack = true, onBack = { onNavigate("receive") })
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val currentPayload = payload
                if (currentPayload == null) {
                    Text(
                        text = "Couldn't determine this device's local IP address.\nMake sure Wi-Fi is connected, then retry.",
                        fontSize = 14.sp, color = SleekOnSurfaceVariant, textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refreshMyQrPayload() },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) { Text("Retry", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    val qrBitmap by produceState(initialValue = null as android.graphics.Bitmap?, currentPayload) {
                        value = QrPairing.generateQrBitmap(currentPayload)
                    }

                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .border(1.dp, SleekOutline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let {
                            Image(bitmap = it.asImageBitmap(), contentDescription = "Pairing QR code", modifier = Modifier.fillMaxSize())
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(deviceName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (senderConnected) "\u2705 Sender connected \u2014 opening receive screen\u2026" else "Have the sender open Pulse \u2192 Send \u2192 Pair via QR",
                        fontSize = 13.sp,
                        color = if (senderConnected) Color(0xFF2E7D32) else SleekOnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }
        }
    }
}