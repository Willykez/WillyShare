package com.willyshare.willykez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willyshare.willykez.ui.InPageHeader
import com.willyshare.willykez.ui.PulseViewModel
import com.willyshare.willykez.ui.SleekBottomNav
import com.willyshare.willykez.ui.theme.SleekBg
import com.willyshare.willykez.ui.theme.SleekOnSurface
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant

@Composable
fun HistoryScreen(
    viewModel: PulseViewModel,
    onNavigate: (String) -> Unit
) {
    val transfers by viewModel.transfers.collectAsState()

    val now = System.currentTimeMillis()
    val dayMillis = 86400000L

    val todayTransfers = transfers.filter { (now - it.timestamp) < dayMillis }
    val olderTransfers = transfers.filter { (now - it.timestamp) >= dayMillis }

    Scaffold(
        bottomBar = {
            SleekBottomNav(currentRoute = "history", onNavigate = onNavigate)
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            InPageHeader(
                title = "Transfer History",
                showBack = true,
                onBack = { onNavigate("dashboard") },
                rightIcon = if (transfers.isNotEmpty()) Icons.Default.DeleteSweep else null,
                onRightClick = { viewModel.clearAllHistory() }
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Column {
                    Text(
                        text = "All transfers",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnSurface
                    )
                    Text(
                        text = "${transfers.size} record${if (transfers.size != 1) "s" else ""} stored on this device",
                        fontSize = 13.sp,
                        color = SleekOnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (transfers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(com.willyshare.willykez.ui.PulseIcons.EmptyInbox, contentDescription = null, tint = SleekOnSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Transfer History Yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                        Text("Files sent or received will appear here", fontSize = 13.sp, color = SleekOnSurfaceVariant)
                    }
                }
            }

            if (todayTransfers.isNotEmpty()) {
                item {
                    Text(
                        text = "TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(todayTransfers, key = { it.id }) { transfer ->
                    TransferItemRow(
                        transfer = transfer,
                        onDelete = { viewModel.deleteTransfer(transfer) }
                    )
                }
            }

            if (olderTransfers.isNotEmpty()) {
                item {
                    Text(
                        text = "EARLIER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                items(olderTransfers, key = { it.id }) { transfer ->
                    TransferItemRow(
                        transfer = transfer,
                        onDelete = { viewModel.deleteTransfer(transfer) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        }
    }
}
