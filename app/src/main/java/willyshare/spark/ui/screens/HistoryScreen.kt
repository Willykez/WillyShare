package willyshare.spark.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import willyshare.spark.ui.InPageHeader
import willyshare.spark.ui.PulseViewModel
import willyshare.spark.ui.SleekBottomNav
import willyshare.spark.ui.theme.SleekOnSurface
import willyshare.spark.ui.theme.SleekOnSurfaceVariant
import willyshare.spark.ui.theme.SleekPrimary
import willyshare.spark.ui.theme.SleekSurfaceContainer

private enum class HistoryTab { ALL, RECEIVED, SENT }

@Composable
fun HistoryScreen(
    viewModel: PulseViewModel,
    onNavigate: (String) -> Unit
) {
    val transfers by viewModel.transfers.collectAsState()
    var selectedTab by remember { mutableStateOf(HistoryTab.ALL) }

    val visibleTransfers = when (selectedTab) {
        HistoryTab.ALL -> transfers
        HistoryTab.RECEIVED -> transfers.filter { !it.isSend }
        HistoryTab.SENT -> transfers.filter { it.isSend }
    }

    val now = System.currentTimeMillis()
    val dayMillis = 86400000L

    val todayTransfers = visibleTransfers.filter { (now - it.timestamp) < dayMillis }
    val olderTransfers = visibleTransfers.filter { (now - it.timestamp) >= dayMillis }

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SleekSurfaceContainer),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HistoryTab.entries.forEach { tab ->
                    val label = when (tab) {
                        HistoryTab.ALL -> "All"
                        HistoryTab.RECEIVED -> "Received"
                        HistoryTab.SENT -> "Sent"
                    }
                    val selected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) SleekPrimary else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else SleekOnSurfaceVariant
                        )
                    }
                }
            }

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
                        text = when (selectedTab) {
                            HistoryTab.ALL -> "All transfers"
                            HistoryTab.RECEIVED -> "Received files"
                            HistoryTab.SENT -> "Sent files"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnSurface
                    )
                    Text(
                        text = "${visibleTransfers.size} record${if (visibleTransfers.size != 1) "s" else ""} stored on this device",
                        fontSize = 13.sp,
                        color = SleekOnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (visibleTransfers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(willyshare.spark.ui.PulseIcons.EmptyInbox, contentDescription = null, tint = SleekOnSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
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
