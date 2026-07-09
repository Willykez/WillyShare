package com.willyshare.willykez.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willyshare.willykez.data.TransferEntity
import com.willyshare.willykez.ui.GroupPosition
import com.willyshare.willykez.ui.GroupedListColumn
import com.willyshare.willykez.ui.GroupedListItem
import com.willyshare.willykez.ui.InPageHeader
import com.willyshare.willykez.ui.PulseViewModel
import com.willyshare.willykez.ui.SleekBottomNav
import com.willyshare.willykez.ui.formatBytes
import com.willyshare.willykez.ui.groupPositionFor
import com.willyshare.willykez.ui.theme.BlueThumb
import com.willyshare.willykez.ui.theme.CyanBright
import com.willyshare.willykez.ui.theme.SleekBg
import com.willyshare.willykez.ui.theme.GreenThumb
import com.willyshare.willykez.ui.theme.PinkThumb
import com.willyshare.willykez.ui.theme.SleekOnPrimaryContainer
import com.willyshare.willykez.ui.theme.SleekOnSecondaryContainer
import com.willyshare.willykez.ui.theme.SleekOnSurface
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant
import com.willyshare.willykez.ui.theme.SleekOutline
import com.willyshare.willykez.ui.theme.SleekPrimary
import com.willyshare.willykez.ui.theme.SleekPrimaryContainer
import com.willyshare.willykez.ui.theme.SleekSecondary
import com.willyshare.willykez.ui.theme.SleekSecondaryContainer
import com.willyshare.willykez.ui.theme.SleekSurfaceContainer

/**
 * Home, restructured to mirror the Settings screen's grouped-list pattern: a device
 * summary card up top, then labeled sections (QUICK ACTIONS, RECENT ACTIVITY) each
 * rendered as one seamless rounded group via GroupedListColumn/GroupedListItem,
 * instead of individually floating cards.
 */
@Composable
fun DashboardScreen(
    viewModel: PulseViewModel,
    onNavigate: (String) -> Unit
) {
    val transfers by viewModel.transfers.collectAsState()
    val deviceName by viewModel.thisDeviceName.collectAsState()
    val linkState by viewModel.linkState.collectAsState()
    val targetName by viewModel.targetName.collectAsState()
    val totalBytes = transfers.sumOf { it.sizeBytes }
    val sentCount = transfers.count { it.isSend }
    val receivedCount = transfers.count { !it.isSend }

    Scaffold(
        bottomBar = {
            SleekBottomNav(currentRoute = "dashboard", onNavigate = onNavigate)
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            InPageHeader(title = "Sparks", subtitle = deviceName)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (linkState != com.willyshare.willykez.ui.LinkState.IDLE) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        ConnectionStatusBanner(
                            linkState = linkState,
                            peerName = targetName,
                            onDisconnect = { viewModel.resetConnection() }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    DeviceSummaryCard(
                        deviceName = deviceName,
                        sentCount = sentCount,
                        receivedCount = receivedCount,
                        totalBytes = totalBytes
                    )
                }

                item {
                    DashboardSection(title = "QUICK ACTIONS") {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            QuickActionRow(
                                icon = com.willyshare.willykez.ui.PulseIcons.Send,
                                title = "Send",
                                subtitle = "Find a nearby device, then pick files",
                                iconTint = SleekPrimary,
                                iconBg = SleekPrimaryContainer,
                                onClick = { onNavigate("send") }
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            QuickActionRow(
                                icon = com.willyshare.willykez.ui.PulseIcons.FolderOpenEmpty,
                                title = "Choose Files",
                                subtitle = "Pick files first, connect after",
                                iconTint = SleekPrimary,
                                iconBg = SleekPrimaryContainer,
                                onClick = { onNavigate("select") }
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            QuickActionRow(
                                icon = com.willyshare.willykez.ui.PulseIcons.Receive,
                                title = "Receive",
                                subtitle = "Wait for an incoming transfer",
                                iconTint = SleekSecondary,
                                iconBg = SleekSecondaryContainer,
                                onClick = { onNavigate("receive") }
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT ACTIVITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekOnSurfaceVariant,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Text(
                            text = "View all",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekPrimary,
                            modifier = Modifier.clickable { onNavigate("history") }
                        )
                    }
                }

                if (transfers.isEmpty()) {
                    item {
                        GroupedListColumn {
                            GroupedListItem(position = GroupPosition.ONLY) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        com.willyshare.willykez.ui.PulseIcons.EmptyInbox,
                                        contentDescription = null,
                                        tint = SleekOnSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("No transfers yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                                    Text("Tap Send or Receive to get started", fontSize = 12.sp, color = SleekOnSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        GroupedListColumn {
                            val shown = transfers.take(6)
                            shown.forEachIndexed { index, transfer ->
                                GroupedListItem(position = groupPositionFor(index, shown.size)) {
                                    TransferItemRow(
                                        transfer = transfer,
                                        onDelete = { viewModel.deleteTransfer(transfer) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceSummaryCard(
    deviceName: String,
    sentCount: Int,
    receivedCount: Int,
    totalBytes: Long
) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = SleekPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "THIS DEVICE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SleekOnPrimaryContainer.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = deviceName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = SleekOnPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("$sentCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SleekOnPrimaryContainer)
                    Text("Sent", fontSize = 12.sp, color = SleekOnPrimaryContainer.copy(alpha = 0.7f))
                }
                Column {
                    Text("$receivedCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SleekOnPrimaryContainer)
                    Text("Received", fontSize = 12.sp, color = SleekOnPrimaryContainer.copy(alpha = 0.7f))
                }
                Column {
                    Text(formatBytes(totalBytes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SleekOnPrimaryContainer)
                    Text("Transferred", fontSize = 12.sp, color = SleekOnPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun DashboardSection(
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
private fun QuickActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                Text(subtitle, fontSize = 12.sp, color = SleekOnSurfaceVariant)
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SleekOnSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun TransferItemRow(
    transfer: TransferEntity,
    onDelete: () -> Unit
) {
    val categoryIcon = com.willyshare.willykez.ui.PulseIcons.forCategory(transfer.category)
    val iconBg = when (transfer.category) {
        "VIDEO" -> CyanBright.copy(alpha = 0.2f)
        "PHOTO" -> PinkThumb
        "AUDIO" -> GreenThumb
        else -> BlueThumb
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val canOpen = !transfer.isSend && !transfer.savedPath.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canOpen) {
                    Modifier.clickable {
                        com.willyshare.willykez.util.FileOpener.open(context, transfer.savedPath!!, transfer.fileName)
                    }
                } else Modifier
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon, contentDescription = null, tint = SleekOnSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = transfer.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SleekOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (transfer.isSend) "\u2191" else "\u2193",
                        fontSize = 11.sp,
                        color = SleekOnSurfaceVariant,
                        modifier = Modifier.alpha(0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${transfer.deviceName} \u00B7 ${formatBytes(transfer.sizeBytes)}",
                        fontSize = 12.sp,
                        color = SleekOnSurfaceVariant,
                        modifier = Modifier.alpha(0.85f)
                    )
                }
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SleekOnSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The real "panic button" for a stuck or active connection - reachable from Home no matter
 * which screen originally created the connection (Send, Receive, QR, whatever). Previously
 * there was no way to back out of a half-formed pairing short of restarting the app.
 */
@Composable
private fun ConnectionStatusBanner(
    linkState: com.willyshare.willykez.ui.LinkState,
    peerName: String?,
    onDisconnect: () -> Unit
) {
    val label = when (linkState) {
        com.willyshare.willykez.ui.LinkState.TRANSFERRING -> "Transferring with ${peerName ?: "a nearby device"}"
        com.willyshare.willykez.ui.LinkState.CONNECTED -> "Connected to ${peerName ?: "a nearby device"}"
        com.willyshare.willykez.ui.LinkState.IDLE -> ""
    }
    val dotColor = if (linkState == com.willyshare.willykez.ui.LinkState.TRANSFERRING) SleekPrimary else Color(0xFF2E7D32)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(SleekSurfaceContainer)
            .border(1.dp, SleekOutline.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = SleekOnSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Disconnect",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD32F2F),
            modifier = Modifier.clickable { onDisconnect() }.padding(start = 8.dp)
        )
    }
}
