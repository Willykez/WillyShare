package com.willyshare.willykez.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willyshare.willykez.ui.GroupPosition
import com.willyshare.willykez.ui.GroupedListColumn
import com.willyshare.willykez.ui.GroupedListItem
import com.willyshare.willykez.ui.PulseIcons
import com.willyshare.willykez.ui.PulseViewModel
import com.willyshare.willykez.ui.SleekBottomNav
import com.willyshare.willykez.ui.InPageHeader
import com.willyshare.willykez.ui.formatBytes
import com.willyshare.willykez.ui.groupPositionFor
import com.willyshare.willykez.ui.settings.AppearanceSection
import com.willyshare.willykez.ui.theme.LocalSnackbarHostState
import com.willyshare.willykez.ui.theme.SleekBg
import com.willyshare.willykez.ui.theme.LocalThemePrefs
import com.willyshare.willykez.ui.theme.LocalThemeState
import com.willyshare.willykez.ui.theme.SleekOnSurface
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant
import com.willyshare.willykez.ui.theme.SleekPrimary
import com.willyshare.willykez.ui.theme.SleekPrimaryContainer

/**
 * Settings, restyled around a grouped-list layout: sections read as one seamless
 * rounded card (GroupedListColumn/GroupedListItem) instead of separate floating
 * cards per row, with section headers as small caps labels above each group -
 * matches the reference app's Settings screen structure.
 */
@Composable
fun SettingsScreen(
    viewModel: PulseViewModel,
    onNavigate: (String) -> Unit
) {
    val transfers by viewModel.transfers.collectAsState()
    val deviceName by viewModel.thisDeviceName.collectAsState()
    val wifiEnabled by viewModel.wifiDirect.isWifiP2pEnabled.collectAsState()
    var clearHistoryExpanded by remember { mutableStateOf(false) }
    val themePrefs = LocalThemePrefs.current
    val themeState = LocalThemeState.current
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current

    val receiveTreeUri by viewModel.receiveTreeUri.collectAsState()
    val destinationLabel = remember(receiveTreeUri) { viewModel.receiveDestinationLabel(receiveTreeUri) }
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setReceiveDestination(uri)
        }
    }

    val totalBytesTransferred = transfers.sumOf { it.sizeBytes }

    Scaffold(
        bottomBar = {
            SleekBottomNav(currentRoute = "settings", onNavigate = onNavigate)
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            InPageHeader(title = "Settings", showBack = true, onBack = { onNavigate("dashboard") })
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = SleekPrimaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(SleekPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                deviceName.take(2).uppercase(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(deviceName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                            Text("Wi-Fi Direct \u2022 Local transfer only", fontSize = 13.sp, color = SleekOnSurfaceVariant)
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "CONNECTIVITY") {
                    val items = listOf<@Composable (GroupPosition) -> Unit> { position ->
                        GroupedListItem(position = position) {
                            SettingsRow(
                                icon = Icons.Default.Wifi,
                                title = "Wi-Fi Direct",
                                subtitle = if (wifiEnabled) "Enabled and ready" else "Turn on Wi-Fi to use nearby discovery",
                                trailing = {
                                    Switch(
                                        checked = wifiEnabled,
                                        onCheckedChange = null,
                                        enabled = false,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = SleekPrimary,
                                        ),
                                    )
                                }
                            )
                        }
                    }
                    items.forEachIndexed { index, row -> row(groupPositionFor(index, items.size)) }
                }
            }

            item {
                SettingsSection(title = "APPEARANCE") {
                    AppearanceSection(
                        prefs = themePrefs,
                        state = themeState,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }

            item {
                SettingsSection(title = "STORAGE") {
                    val count = if (receiveTreeUri != null) 4 else 3
                    var idx = 0
                    GroupedListItem(position = groupPositionFor(idx++, count)) {
                        SettingsRow(
                            icon = Icons.Default.FolderOpen,
                            title = "Save received files to",
                            subtitle = destinationLabel,
                            trailing = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = SleekOnSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            onClick = { pickFolderLauncher.launch(null) }
                        )
                    }
                    if (receiveTreeUri != null) {
                        GroupedListItem(position = groupPositionFor(idx++, count)) {
                            SettingsRow(
                                icon = Icons.Default.Storage,
                                title = "Reset to default folder",
                                subtitle = "Downloads/PulseReceived",
                                onClick = { viewModel.setReceiveDestination(null) }
                            )
                        }
                    }
                    GroupedListItem(position = groupPositionFor(idx++, count)) {
                        SettingsRow(
                            icon = Icons.Default.Storage,
                            title = "Transfer history",
                            subtitle = "${transfers.size} record${if (transfers.size != 1) "s" else ""} \u00B7 ${formatBytes(totalBytesTransferred)} total",
                            trailing = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = SleekOnSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            onClick = { onNavigate("history") }
                        )
                    }
                    GroupedListItem(position = groupPositionFor(idx, count)) {
                        Column {
                            SettingsRow(
                                icon = Icons.Default.DeleteForever,
                                title = "Clear transfer history",
                                subtitle = "Removes all local transfer records",
                                iconTint = Color(0xFFD32F2F),
                                onClick = { clearHistoryExpanded = !clearHistoryExpanded }
                            )
                            // Inline confirm instead of a separate dialog - expands in place,
                            // which reads calmer than a modal for a reversible-feeling action.
                            AnimatedVisibility(
                                visible = clearHistoryExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.clearAllHistory()
                                            clearHistoryExpanded = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text("Confirm clear", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "ABOUT") {
                    GroupedListItem(position = GroupPosition.ONLY) {
                        SettingsRow(
                            icon = Icons.Default.Info,
                            title = "Sparks",
                            subtitle = "Direct offline file sharing over Wi-Fi Direct"
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SleekOnSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        GroupedListColumn {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = SleekPrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                Text(subtitle, fontSize = 12.sp, color = SleekOnSurfaceVariant, modifier = Modifier.alpha(0.85f))
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}
