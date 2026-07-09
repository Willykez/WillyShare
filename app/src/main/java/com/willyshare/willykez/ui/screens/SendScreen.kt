package com.willyshare.willykez.ui.screens

import android.Manifest
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.willyshare.willykez.ui.AuroraBackground
import com.willyshare.willykez.ui.InPageHeader
import com.willyshare.willykez.ui.PulseViewModel
import com.willyshare.willykez.ui.RadarPulseRing
import com.willyshare.willykez.ui.SleekFloatingPillButton
import com.willyshare.willykez.ui.TargetSource
import com.willyshare.willykez.ui.theme.SleekCard
import com.willyshare.willykez.ui.theme.SleekOnSurface
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant
import com.willyshare.willykez.ui.theme.SleekOutline
import com.willyshare.willykez.ui.theme.SleekPrimary
import com.willyshare.willykez.ui.theme.SleekPrimaryContainer
import com.willyshare.willykez.util.WifiEnableHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SendScreen(
    viewModel: PulseViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionsState = rememberMultiplePermissionsState(requiredPermissions)

    val peers by viewModel.discoveredDevices.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val targetIp by viewModel.targetIp.collectAsState()
    val targetSource by viewModel.targetSource.collectAsState()
    val hasPendingCart by viewModel.hasPendingCart.collectAsState()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var connectingTo by remember { mutableStateOf<String?>(null) }
    var wifiEnabled by remember { mutableStateOf(WifiEnableHelper.isWifiEnabled(context)) }

    // Re-check whenever the screen resumes (e.g. coming back from the Wi-Fi panel/Settings).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                wifiEnabled = WifiEnableHelper.isWifiEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startPeerDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPeerDiscovery() }
    }

    LaunchedEffect(targetIp, targetSource) {
        if (targetIp != null && targetSource == TargetSource.WIFI_DIRECT) {
            // "Pick files first" flow: the cart already has something queued (picked from
            // Choose Files, the folder browser, or a share-sheet hand-off) - skip the picker
            // entirely and go straight to sending, exactly like Quick Share does once a
            // target is found. Otherwise, this is the original "connect first" flow: go pick
            // files now that we know who we're sending to.
            onNavigate(if (hasPendingCart) "transfer" else "select")
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        AuroraBackground(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    InPageHeader(
                        title = "Send",
                        subtitle = if (isDiscovering) "Scanning for nearby devices\u2026" else "Nearby devices",
                        showBack = true,
                        onBack = { onNavigate("dashboard") }
                    )
                    if (!permissionsState.allPermissionsGranted) {
                        PermissionRationaleCard(
                            showSettingsHint = permissionsState.permissions.any { it.status.shouldShowRationale },
                            onRequest = { permissionsState.launchMultiplePermissionRequest() }
                        )
                        return@Column
                    }

                    if (!wifiEnabled) {
                        WifiOffBanner(
                            onEnable = { context.startActivity(WifiEnableHelper.requestEnable(context)) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDiscovering) {
                            RadarPulseRing(160, 0)
                            RadarPulseRing(115, 600)
                            RadarPulseRing(75, 1200)
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(SleekPrimaryContainer)
                                .border(2.dp, SleekPrimary, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDiscovering) {
                                CircularProgressIndicator(color = SleekPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            } else {
                                Icon(com.willyshare.willykez.ui.PulseIcons.Broadcasting, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(30.dp))
                            }
                        }
                    }

                    statusMessage?.let {
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            color = SleekOnSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    if (peers.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isDiscovering) "Looking for nearby devices\u2026" else "No devices found yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SleekOnSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Make sure Wi-Fi is on and the receiver has Pulse open on the Receive screen.",
                                fontSize = 12.sp,
                                color = SleekOnSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.startPeerDiscovery() },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                            ) {
                                Text("Scan again", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(peers, key = { it.deviceAddress }) { device ->
                                PeerRow(
                                    device = device,
                                    isConnecting = connectingTo == device.deviceAddress,
                                    onClick = {
                                        connectingTo = device.deviceAddress
                                        viewModel.connectToPeer(device) { msg -> statusMessage = msg }
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(90.dp)) }
                        }
                    }
                }

                if (permissionsState.allPermissionsGranted) {
                    SleekFloatingPillButton(
                        text = "Show my QR",
                        icon = Icons.Default.QrCode2,
                        onClick = { onNavigate("my_qr") },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerRow(device: WifiP2pDevice, isConnecting: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SleekCard)
            .border(1.dp, SleekOutline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(enabled = !isConnecting) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SleekPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(com.willyshare.willykez.ui.PulseIcons.Device, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(device.deviceName.ifBlank { "Unknown device" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                Text("Wi-Fi Direct \u00B7 ${device.deviceAddress}", fontSize = 11.sp, color = SleekOnSurfaceVariant)
            }
        }
        if (isConnecting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SleekPrimary)
        }
    }
}

@Composable
private fun WifiOffBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SleekCard)
            .border(1.dp, SleekOutline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Wi-Fi is off", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
            Text(
                "Wi-Fi Direct needs Wi-Fi turned on to find nearby devices.",
                fontSize = 11.sp,
                color = SleekOnSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Button(
            onClick = onEnable,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
        ) {
            Text("Enable", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermissionRationaleCard(showSettingsHint: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(com.willyshare.willykez.ui.PulseIcons.TargetPin, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Nearby device permission needed",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = SleekOnSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Pulse needs this permission to discover nearby devices over Wi-Fi Direct.",
            fontSize = 13.sp,
            color = SleekOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
        ) {
            Text(if (showSettingsHint) "Grant permission" else "Allow", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
