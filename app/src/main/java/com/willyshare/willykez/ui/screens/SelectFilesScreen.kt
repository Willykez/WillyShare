package com.willyshare.willykez.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willyshare.willykez.data.FileItemEntity
import com.willyshare.willykez.ui.PulseViewModel
import com.willyshare.willykez.ui.InPageHeader
import com.willyshare.willykez.ui.formatBytes
import com.willyshare.willykez.ui.theme.SleekBg
import com.willyshare.willykez.ui.theme.SleekCard
import com.willyshare.willykez.ui.theme.SleekOnSurface
import com.willyshare.willykez.ui.theme.SleekOnSurfaceVariant
import com.willyshare.willykez.ui.theme.SleekOutline
import com.willyshare.willykez.ui.theme.SleekPrimary
import com.willyshare.willykez.ui.theme.SleekSurfaceContainer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SelectFilesScreen(
    viewModel: PulseViewModel,
    onNavigate: (String) -> Unit,
    onGoBack: (() -> Unit)? = null
) {
    val files by viewModel.allFiles.collectAsState()
    val currentTab by viewModel.selectedCategoryTab.collectAsState()
    val isLoading by viewModel.isLoadingFiles.collectAsState()
    val targetName by viewModel.targetName.collectAsState()
    val targetIp by viewModel.targetIp.collectAsState()
    val goBack: () -> Unit = onGoBack ?: { onNavigate("dashboard") }

    // Reading photos/videos/audio/documents via MediaStore requires these at runtime,
    // not just declared in the manifest - without them queryAll() silently returns nothing,
    // which is why the picker used to look empty/missing.
    val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionsState = rememberMultiplePermissionsState(mediaPermissions)

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.loadDeviceFiles()
        }
    }

    // "List" or "Grid" - remembered per visit to the screen, defaults to Grid (thumbnails first).
    var isGridView by remember { mutableStateOf(true) }
    var sortOption by remember { mutableStateOf(SortOption.NAME) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val selectedCount = files.count { it.isSelected }
    val totalBytes = files.filter { it.isSelected }.sumOf { it.sizeBytes }
    val categoryFiltered = if (currentTab.equals("All", ignoreCase = true)) {
        files
    } else {
        files.filter { it.category.equals(currentTab, ignoreCase = true) }
    }
    val filteredFiles = when (sortOption) {
        SortOption.NAME -> categoryFiltered.sortedBy { it.name.lowercase() }
        SortOption.SIZE_LARGEST -> categoryFiltered.sortedByDescending { it.sizeBytes }
        SortOption.NEWEST -> categoryFiltered.sortedByDescending { it.dateModifiedMs }
    }
    val sharedFiles by viewModel.pendingSharedFiles.collectAsState()
    val browseSummary by viewModel.browseSelectionSummary.collectAsState()
    val browseCount = browseSummary.first
    val browseBytes = browseSummary.second
    val grandSelectedCount = selectedCount + sharedFiles.size + browseCount
    val grandTotalBytes = totalBytes + sharedFiles.sumOf { it.sizeBytes } + browseBytes

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!permissionsState.allPermissionsGranted) {
                Column(modifier = Modifier.fillMaxSize()) {
                    InPageHeader(
                        title = "Select to Send",
                        subtitle = targetName?.let { "To $it" },
                        showBack = true,
                        onBack = goBack
                    )
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(com.willyshare.willykez.ui.PulseIcons.FolderClosed, contentDescription = null, tint = SleekOnSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Files permission needed",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekOnSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Pulse needs access to your photos, videos, audio and documents to let you pick what to send.",
                        fontSize = 13.sp,
                        color = SleekOnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionsState.launchMultiplePermissionRequest() },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) {
                        Text(
                            if (permissionsState.permissions.any { it.status.shouldShowRationale }) "Grant in Settings" else "Allow access",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                }
                return@Box
            }
            Column(modifier = Modifier.fillMaxSize()) {
                InPageHeader(
                    title = "Select to Send",
                    subtitle = targetName?.let { "To $it" },
                    showBack = true,
                    onBack = goBack
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekSurfaceContainer)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("All", "Photos", "Videos", "Audio", "Documents", "Apps")
                    for (tab in tabs) {
                        val isSelected = tab.equals(currentTab, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) SleekPrimary else SleekCard)
                                .border(1.dp, if (isSelected) SleekPrimary else SleekOutline.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .clickable { viewModel.selectedCategoryTab.value = tab }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else SleekOnSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SleekCard)
                            .border(1.dp, SleekOutline.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            .clickable { onNavigate("browse") }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                com.willyshare.willykez.ui.PulseIcons.FolderClosed,
                                contentDescription = null,
                                tint = SleekOnSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse folders", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SleekOnSurfaceVariant)
                        }
                    }
                }

                if (sharedFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SleekCard)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${sharedFiles.size} file${if (sharedFiles.size > 1) "s" else ""} shared from another app",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SleekOnSurface
                        )
                        IconButton(onClick = { viewModel.setPendingSharedFiles(emptyList()) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = SleekOnSurfaceVariant)
                        }
                    }
                }

                if (!isLoading && filteredFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${filteredFiles.size} item${if (filteredFiles.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SleekOnSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.SwapVert, contentDescription = "Sort", tint = SleekOnSurfaceVariant)
                                }
                                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Name (A-Z)") },
                                        onClick = { sortOption = SortOption.NAME; sortMenuExpanded = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Largest first") },
                                        onClick = { sortOption = SortOption.SIZE_LARGEST; sortMenuExpanded = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Newest first") },
                                        onClick = { sortOption = SortOption.NEWEST; sortMenuExpanded = false }
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
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SleekPrimary)
                    }
                } else if (filteredFiles.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(com.willyshare.willykez.ui.PulseIcons.FolderOpenEmpty, contentDescription = null, tint = SleekOnSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No $currentTab found on this device", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SleekOnSurface, textAlign = TextAlign.Center)
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .padding(bottom = if (grandSelectedCount > 0) 140.dp else 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            FileGridCardItem(
                                file = file,
                                onToggle = { viewModel.toggleFileSelection(file.id, file.isSelected) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .padding(bottom = if (grandSelectedCount > 0) 140.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            FileListRowItem(
                                file = file,
                                onToggle = { viewModel.toggleFileSelection(file.id, file.isSelected) }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = grandSelectedCount > 0,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
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
                                text = "$grandSelectedCount item${if (grandSelectedCount > 1) "s" else ""} selected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SleekOnSurface
                            )
                            Text(
                                text = "Total size: ${formatBytes(grandTotalBytes)}",
                                fontSize = 12.sp,
                                color = SleekOnSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.clearSelections()
                                viewModel.clearBrowseSelection()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = SleekOnSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (targetIp != null) {
                                // Already connected (came from the connect-first flow, or a
                                // connection was already active) - send immediately.
                                onNavigate("transfer")
                            } else {
                                // Pick-first flow: cart is ready, now go find a device. Once
                                // connected, Send/Scan-QR auto-continue straight to sending.
                                onNavigate("send")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary)
                    ) {
                        Icon(
                            com.willyshare.willykez.ui.PulseIcons.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (targetIp != null) {
                                "SEND NOW${targetName?.let { " TO ${it.uppercase()}" } ?: ""}"
                            } else {
                                "CONNECT & SEND"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

private enum class SortOption { NAME, SIZE_LARGEST, NEWEST }

/**
 * Renders what the file actually looks like instead of a generic file-type icon:
 * the real photo, an actual frame from the video, or the app's real launcher icon.
 * Falls back to the icon treatment for file types that don't have a cheap-to-render
 * preview (documents, generic files).
 */
@Composable
private fun FileThumbnail(file: FileItemEntity, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 36.dp) {
    when (file.category) {
        "Photos" -> {
            AsyncImage(
                model = Uri.parse(file.uri),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        "Videos" -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                val thumb by rememberVideoThumbnail(Uri.parse(file.uri))
                val bmp = thumb
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Icon(
                        com.willyshare.willykez.ui.PulseIcons.forBrowseCategory("Videos"),
                        contentDescription = null,
                        tint = SleekOnSurfaceVariant,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
        "Apps" -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                val icon by rememberAppIcon(file)
                val bmp = icon
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(0.75f)
                    )
                } else {
                    Icon(
                        com.willyshare.willykez.ui.PulseIcons.forBrowseCategory("Apps"),
                        contentDescription = null,
                        tint = SleekOnSurfaceVariant,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
        else -> {
            val icon = if (file.category == "Documents") {
                com.willyshare.willykez.ui.PulseIcons.forFileName(file.name)
            } else {
                com.willyshare.willykez.ui.PulseIcons.forBrowseCategory(file.category)
            }
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = SleekOnSurfaceVariant, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** Actual frame from the video, not a generic film icon - uses the same thumbnail API the system gallery uses. */
@Composable
private fun rememberVideoThumbnail(uri: Uri): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                } else {
                    @Suppress("DEPRECATION")
                    val id = uri.lastPathSegment?.toLongOrNull()
                    if (id != null) {
                        @Suppress("DEPRECATION")
                        MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                        )
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** The app's real launcher icon - for an installed app via PackageManager, or parsed straight
 *  out of a standalone .apk file that isn't installed. */
@Composable
private fun rememberAppIcon(file: FileItemEntity): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, file.id) {
        value = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable: Drawable? = if (file.id.startsWith("app_")) {
                    val pkg = file.id.removePrefix("app_")
                    pm.getApplicationIcon(pkg)
                } else {
                    val path = Uri.parse(file.uri).path
                    if (path != null) {
                        @Suppress("DEPRECATION")
                        val info = pm.getPackageArchiveInfo(path, 0)
                        info?.applicationInfo?.let { appInfo ->
                            appInfo.sourceDir = path
                            appInfo.publicSourceDir = path
                            appInfo.loadIcon(pm)
                        }
                    } else null
                }
                drawable?.let { drawableToBitmap(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable, size: Int = 128): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun FileListRowItem(
    file: FileItemEntity,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SleekCard)
            .border(
                width = if (file.isSelected) 2.dp else 1.dp,
                color = if (file.isSelected) SleekPrimary else SleekOutline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SleekSurfaceContainer)
        ) {
            FileThumbnail(file, modifier = Modifier.fillMaxSize(), iconSize = 20.dp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SleekOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatBytes(file.sizeBytes),
                fontSize = 11.sp,
                color = SleekOnSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (file.isSelected) SleekPrimary else Color.Transparent)
                .border(1.dp, if (file.isSelected) SleekPrimary else SleekOutline.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (file.isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun FileGridCardItem(
    file: FileItemEntity,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(SleekCard)
            .border(
                width = if (file.isSelected) 2.5.dp else 1.dp,
                color = if (file.isSelected) SleekPrimary else SleekOutline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggle() }
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SleekSurfaceContainer)
            ) {
                FileThumbnail(file, modifier = Modifier.fillMaxSize(), iconSize = 32.dp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = file.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = SleekOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = formatBytes(file.sizeBytes),
                fontSize = 10.sp,
                color = SleekOnSurfaceVariant
            )
        }

        if (file.isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(SleekPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
