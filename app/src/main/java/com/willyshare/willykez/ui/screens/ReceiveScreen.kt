package com.willyshare.willykez.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willyshare.willykez.ui.AuroraBackground
import com.willyshare.willykez.ui.FileProgressRow
import com.willyshare.willykez.ui.GlassCard
import com.willyshare.willykez.ui.PulseViewModel
import com.willyshare.willykez.ui.RadarPulseRing
import com.willyshare.willykez.ui.InPageHeader
import com.willyshare.willykez.ui.SleekFloatingPillButton
import com.willyshare.willykez.ui.formatBytes
import com.willyshare.willykez.ui.theme.CyanBright
import com.willyshare.willykez.ui.theme.SleekOnSurface
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant
import com.willyshare.willykez.ui.theme.SleekOutline
import com.willyshare.willykez.ui.theme.SleekPrimary
import com.willyshare.willykez.ui.theme.SleekPrimaryContainer
import com.willyshare.willykez.ui.theme.VioletAccent
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ReceiveScreen(viewModel: PulseViewModel, onNavigate: (String) -> Unit) {
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
    } else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val permissionsState = rememberMultiplePermissionsState(requiredPermissions)

    val isListening by viewModel.isListening.collectAsState()
    val senderConnected by viewModel.senderConnected.collectAsState()
    val progress by viewModel.receiveProgress.collectAsState()
    val deviceName by viewModel.thisDeviceName.collectAsState()

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) permissionsState.launchMultiplePermissionRequest()
        // Receiving itself is started once, app-wide, in the ViewModel's init{} - it no
        // longer needs (or should) start/stop with this screen's lifecycle.
    }
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) viewModel.startPeerDiscovery()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPeerDiscovery() }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        AuroraBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    InPageHeader(
                        title = "Receive",
                        subtitle = when {
                            !permissionsState.allPermissionsGranted -> "Permission needed"
                            isListening -> "Visible as \u201C$deviceName\u201D"
                            else -> "Starting listener\u2026"
                        },
                        showBack = true,
                        onBack = { onNavigate("dashboard") }
                    )
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!permissionsState.allPermissionsGranted) {
                            Spacer(modifier = Modifier.height(60.dp))
                            Icon(com.willyshare.willykez.ui.PulseIcons.TargetPin, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Nearby device permission needed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Pulse needs this permission so nearby senders can find and connect to this device.",
                                fontSize = 13.sp, color = SleekOnSurfaceVariant, textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { permissionsState.launchMultiplePermissionRequest() },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                            ) {
                                Text(
                                    if (permissionsState.permissions.any { it.status.shouldShowRationale }) "Grant permission" else "Allow",
                                    color = Color.White, fontWeight = FontWeight.Bold
                                )
                            }
                            return@Column
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        val outlineColor = SleekOutline
                        val primaryColor = SleekPrimary
                        val fraction = if (progress.overallTotal > 0) (progress.overallBytes.toFloat() / progress.overallTotal.toFloat()).coerceIn(0f, 1f) else 0f

                        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                            if (!senderConnected) {
                                RadarPulseRing(200, 0); RadarPulseRing(150, 700); RadarPulseRing(100, 1400)
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(SleekPrimaryContainer)
                                        .border(2.dp, SleekPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { Icon(com.willyshare.willykez.ui.PulseIcons.SignalBars, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(42.dp)) }
                            } else {
                                Canvas(modifier = Modifier.size(180.dp)) {
                                    drawCircle(color = outlineColor.copy(alpha = 0.25f), style = Stroke(width = 14.dp.toPx()))
                                    drawArc(
                                        brush = Brush.sweepGradient(listOf(VioletAccent, primaryColor, CyanBright, VioletAccent)),
                                        startAngle = -90f, sweepAngle = fraction * 360f, useCenter = false,
                                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${(fraction * 100).toInt()}%", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = SleekPrimary)
                                    Text("${formatBytes(progress.overallSpeed.toLong())}/s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SleekOnSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                !senderConnected -> "Waiting to receive"
                                progress.isComplete -> "Transfer complete"
                                progress.overallTotal > 0 -> "Receiving\u2026"
                                else -> "Connected"
                            },
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (!senderConnected) {
                            Text(
                                "Ask the sender to pick \u201C$deviceName\u201D from their Send screen,\nor scan their QR code.",
                                fontSize = 13.sp, color = SleekOnSurfaceVariant, textAlign = TextAlign.Center
                            )
                        } else if (progress.overallTotal == 0L) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E7D32))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Sender connected \u2014 waiting for files\u2026",
                                    fontSize = 13.sp, color = SleekOnSurfaceVariant, textAlign = TextAlign.Center
                                )
                            }
                        }

                        progress.error?.let {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(com.willyshare.willykez.ui.PulseIcons.Warning, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(it, fontSize = 12.sp, color = Color(0xFFD32F2F))
                            }
                        }

                        if (progress.files.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            GlassCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                                Text("Files", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(progress.files, key = { it.key }) { item -> FileProgressRow(item) }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(if (!senderConnected) 90.dp else 20.dp))
                    }
                }

                if (!senderConnected && permissionsState.allPermissionsGranted) {
                    SleekFloatingPillButton(
                        text = "Scan QR",
                        icon = Icons.Default.QrCodeScanner,
                        onClick = { onNavigate("scan_qr") },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}
