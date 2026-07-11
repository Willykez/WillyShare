package willyshare.spark.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import willyshare.spark.net.LocalFileNode
import willyshare.spark.net.StorageRoot
import willyshare.spark.ui.InPageHeader
import willyshare.spark.ui.PulseViewModel
import willyshare.spark.ui.formatBytes
import willyshare.spark.ui.theme.SleekCard
import willyshare.spark.ui.theme.SleekOnSurface
import willyshare.spark.ui.theme.SleekOnSurfaceVariant
import willyshare.spark.ui.theme.SleekOutline
import willyshare.spark.ui.theme.SleekPrimary
import willyshare.spark.ui.theme.SleekSurfaceContainer

private enum class BrowseSortOption { NAME, SIZE_LARGEST, NEWEST }

private fun sortEntries(entries: List<LocalFileNode>, option: BrowseSortOption): List<LocalFileNode> {
    // Folders first regardless of sort choice - matches how every OS file browser behaves,
    // and how the file picker screen already sorts.
    val (dirs, files) = entries.partition { it.isDirectory }
    val comparator = when (option) {
        BrowseSortOption.NAME -> compareBy<LocalFileNode> { it.name.lowercase() }
        BrowseSortOption.SIZE_LARGEST -> compareByDescending { it.sizeBytes }
        BrowseSortOption.NEWEST -> compareByDescending { it.lastModified }
    }
    return dirs.sortedBy { it.name.lowercase() } + files.sortedWith(comparator)
}

@Composable
fun BrowseFilesScreen(
    viewModel: PulseViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current

    fun hasFullStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    var storageAccessGranted by remember { mutableStateOf(hasFullStorageAccess()) }

    // MANAGE_EXTERNAL_STORAGE is granted via a Settings screen, not a runtime dialog -
    // re-check when the user comes back to the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) storageAccessGranted = hasFullStorageAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pathStack by viewModel.browsePathStack.collectAsState()
    val entriesRaw by viewModel.browseEntries.collectAsState()
    val loading by viewModel.browseLoading.collectAsState()
    val selectedFiles by viewModel.browseSelectedFiles.collectAsState()
    val selectedFolders by viewModel.browseSelectedFolders.collectAsState()
    val summary by viewModel.browseSelectionSummary.collectAsState()

    var isGridView by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(BrowseSortOption.NAME) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val entries = remember(entriesRaw, sortOption) { sortEntries(entriesRaw, sortOption) }
    val allVisibleSelected = entries.isNotEmpty() && entries.all {
        if (it.isDirectory) it.path in selectedFolders else it.path in selectedFiles
    }

    // Folder-up takes priority over leaving the screen while we're a level deep.
    BackHandler(enabled = pathStack.isNotEmpty()) { viewModel.browseUp() }

    val currentTitle = pathStack.lastOrNull()?.substringAfterLast('/') ?: "Browse folders"

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                InPageHeader(
                    title = currentTitle,
                    subtitle = if (pathStack.isNotEmpty()) pathStack.joinToString(" / ") { it.substringAfterLast('/') } else "Internal storage & SD card",
                    showBack = true,
                    onBack = { if (!viewModel.browseUp()) onNavigate("select") }
                )

                if (storageAccessGranted && pathStack.isNotEmpty() && !loading && entries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setBrowseSelectionForVisible(entries, !allVisibleSelected) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (allVisibleSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = if (allVisibleSelected) "Deselect all" else "Select all",
                                tint = SleekOnSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Sort", tint = SleekOnSurfaceVariant)
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)") },
                                    onClick = { sortOption = BrowseSortOption.NAME; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Largest first") },
                                    onClick = { sortOption = BrowseSortOption.SIZE_LARGEST; sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Newest first") },
                                    onClick = { sortOption = BrowseSortOption.NEWEST; sortMenuExpanded = false }
                                )
                            }
                        }
                        IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                                tint = SleekOnSurfaceVariant
                            )
                        }
                    }
                }

                if (!storageAccessGranted) {
                    StoragePermissionRationale(
                        onRequest = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                } else if (pathStack.isEmpty()) {
                    // Top level: pick a storage volume.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(viewModel.storageRoots, key = { it.path }) { root ->
                            StorageRootRow(root = root, onClick = { viewModel.browseIntoRoot(root) })
                        }
                    }
                } else if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SleekPrimary)
                    }
                } else if (entries.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(willyshare.spark.ui.PulseIcons.FolderOpenEmpty, contentDescription = null, tint = SleekOnSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("This folder is empty", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = if (summary.first > 0) 130.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(entries, key = { it.path }) { node ->
                            BrowseEntryGridCell(
                                node = node,
                                isChecked = if (node.isDirectory) node.path in selectedFolders else node.path in selectedFiles,
                                onCheckToggle = {
                                    if (node.isDirectory) viewModel.toggleBrowseFolder(node.path) else viewModel.toggleBrowseFile(node.path)
                                },
                                onOpen = { if (node.isDirectory) viewModel.browseInto(node) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = if (summary.first > 0) 130.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        items(entries, key = { it.path }) { node ->
                            BrowseEntryRow(
                                node = node,
                                isChecked = if (node.isDirectory) node.path in selectedFolders else node.path in selectedFiles,
                                onCheckToggle = {
                                    if (node.isDirectory) viewModel.toggleBrowseFolder(node.path) else viewModel.toggleBrowseFile(node.path)
                                },
                                onOpen = { if (node.isDirectory) viewModel.browseInto(node) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(90.dp)) }
                    }
                }
            }

            if (summary.first > 0) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(SleekSurfaceContainer)
                        .border(1.dp, SleekOutline.copy(alpha = 0.3f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${summary.first} item${if (summary.first > 1) "s" else ""} selected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SleekOnSurface
                            )
                            Text("Total size: ${formatBytes(summary.second)}", fontSize = 12.sp, color = SleekOnSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.clearBrowseSelection() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = SleekOnSurface)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onNavigate("select") },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) {
                        Text("ADD TO SELECTION", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageRootRow(root: StorageRoot, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SleekCard)
            .border(1.dp, SleekOutline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(SleekPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(willyshare.spark.ui.PulseIcons.FolderClosed, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(root.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface)
            Text(root.path, fontSize = 11.sp, color = SleekOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BrowseEntryRow(
    node: LocalFileNode,
    isChecked: Boolean,
    onCheckToggle: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SleekCard)
            .border(
                width = if (isChecked) 2.dp else 1.dp,
                color = if (isChecked) SleekPrimary else SleekOutline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { if (node.isDirectory) onOpen() else onCheckToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isChecked) SleekPrimary else Color.Transparent)
                .border(1.5.dp, if (isChecked) SleekPrimary else SleekOutline.copy(alpha = 0.5f), CircleShape)
                .clickable { onCheckToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            if (node.isDirectory) willyshare.spark.ui.PulseIcons.FolderClosed else willyshare.spark.ui.PulseIcons.forFileName(node.name),
            contentDescription = null,
            tint = SleekOnSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(node.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SleekOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!node.isDirectory) {
                Text(formatBytes(node.sizeBytes), fontSize = 11.sp, color = SleekOnSurfaceVariant)
            } else {
                Text("Folder \u2022 tap to open, checkmark to select all", fontSize = 11.sp, color = SleekOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (node.isDirectory) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SleekOnSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BrowseEntryGridCell(
    node: LocalFileNode,
    isChecked: Boolean,
    onCheckToggle: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SleekCard)
            .border(
                width = if (isChecked) 2.dp else 1.dp,
                color = if (isChecked) SleekPrimary else SleekOutline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { if (node.isDirectory) onOpen() else onCheckToggle() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Icon(
                if (node.isDirectory) willyshare.spark.ui.PulseIcons.FolderClosed else willyshare.spark.ui.PulseIcons.forFileName(node.name),
                contentDescription = null,
                tint = SleekOnSurfaceVariant,
                modifier = Modifier.size(34.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isChecked) SleekPrimary else Color.Transparent)
                    .border(1.5.dp, if (isChecked) SleekPrimary else SleekOutline.copy(alpha = 0.5f), CircleShape)
                    .clickable { onCheckToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            node.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SleekOnSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        if (!node.isDirectory) {
            Text(formatBytes(node.sizeBytes), fontSize = 9.sp, color = SleekOnSurfaceVariant)
        }
    }
}

@Composable
private fun StoragePermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(willyshare.spark.ui.PulseIcons.FolderClosed, contentDescription = null, tint = SleekPrimary, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Full storage access needed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "To browse every folder on internal storage and your SD card, Sparks needs the \"All files access\" permission. You'll be taken to a Settings screen to turn it on.",
            fontSize = 13.sp,
            color = SleekOnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
        ) {
            Text("Grant in Settings", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
