package com.onepage.companion.ui.screens.files

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onepage.companion.core.model.FileEntry
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.ui.PageHeader
import com.onepage.companion.ui.components.ActionRow
import com.onepage.companion.ui.components.EmptyState
import com.onepage.companion.ui.components.FormSheet
import com.onepage.companion.ui.components.Hairline
import com.onepage.companion.ui.components.LoadingRow
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.components.Pill
import com.onepage.companion.ui.components.PillTone
import com.onepage.companion.ui.components.PrimaryButton
import com.onepage.companion.ui.components.PromptDialog
import com.onepage.companion.ui.components.SlimProgress
import com.onepage.companion.ui.components.formatSize
import com.onepage.companion.ui.theme.onePageColors
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FilesScreen(api: DeviceApi) {
    val vm: FilesViewModel = viewModel(key = "files:${api.baseUrl}", factory = FilesViewModel.factory(api))
    val state by vm.state.collectAsStateWithLifecycle()
    FilesContent(
        state = state,
        onOpen = vm::open,
        onUp = vm::up,
        onRefresh = vm::refresh,
        onMkdir = vm::createFolder,
        onRename = vm::rename,
        onMove = vm::move,
        onDelete = vm::delete,
        onUpload = vm::upload,
        onDownload = vm::download,
        onClearUploads = vm::clearFinishedUploads,
        onDismissError = vm::dismissError,
    )
}

@Composable
fun FilesContent(
    state: FilesUiState,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onMkdir: (String) -> Unit,
    onRename: (FileEntry, String) -> Unit,
    onMove: (FileEntry, String) -> Unit,
    onDelete: (FileEntry) -> Unit,
    onUpload: (List<Uri>) -> Unit,
    onDownload: suspend (FileEntry) -> Result<File>,
    onClearUploads: () -> Unit,
    onDismissError: () -> Unit,
) {
    val c = onePageColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var moveTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var actionTarget by remember { mutableStateOf<FileEntry?>(null) }
    var downloadBusy by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> if (uris.isNotEmpty()) onUpload(uris) },
    )

    val folderName = if (state.path == "/") "根目录" else state.path.substringAfterLast('/')

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 22.dp, end = 22.dp, top = 8.dp, bottom = 112.dp
        ),
    ) {
        item {
            PageHeader(
                eyebrow = run {
                    val parent = state.path.substringBeforeLast('/')
                    if (parent.isBlank()) "存储卡" else "存储卡 $parent"
                },
                title = folderName,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onUp, enabled = state.path != "/") {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = "上一层",
                                tint = if (state.path != "/") c.ink2 else c.hairline,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, "刷新", tint = c.ink2, modifier = Modifier.size(20.dp))
                        }
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    PrimaryButton(
                        text = "上传文件",
                        icon = Icons.Rounded.Upload,
                        onClick = { picker.launch(arrayOf("*/*")) },
                    )
                }
                PillAction(
                    icon = Icons.Rounded.CreateNewFolder,
                    text = "新建",
                    onClick = { newFolder = true },
                )
            }

            if (state.uploads.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                UploadPanel(
                    tasks = state.uploads,
                    onClear = onClearUploads,
                )
            }

            AnimatedVisibility(visible = state.error != null) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.dangerSoft)
                            .clickable { onDismissError() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(state.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.danger)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (state.entries.isNotEmpty()) {
                Text(
                    "${state.entries.count { it.isDirectory }} 个文件夹 · ${state.entries.count { !it.isDirectory }} 个文件",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.ink3,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.loading) {
            item { LoadingRow("读取目录…") }
        } else if (state.entries.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "这个文件夹是空的",
                    body = "点「上传文件」把 EPUB、字体或图片放进阅读器。",
                )
            }
        } else {
            item {
                OnePageCard(padding = 0.dp) {
                    state.entries.forEachIndexed { index, entry ->
                        FileRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) {
                                    onOpen(if (state.path == "/") "/${entry.name}" else "${state.path}/${entry.name}")
                                } else {
                                    actionTarget = entry
                                }
                            },
                            onLongClick = { actionTarget = entry },
                        )
                        if (index != state.entries.lastIndex) Hairline(inset = 18.dp)
                    }
                }
            }
        }
    }

    // ---- dialogs & sheets
    if (newFolder) {
        PromptDialog(
            title = "新建文件夹",
            label = "文件夹名称",
            placeholder = "Books",
            onConfirm = { name ->
                newFolder = false
                if (name.isNotBlank()) onMkdir(name.trim())
            },
            onDismiss = { newFolder = false },
        )
    }

    renameTarget?.let { target ->
        PromptDialog(
            title = "重命名",
            label = "新名称",
            initial = target.name,
            onConfirm = { name ->
                renameTarget = null
                if (name.isNotBlank() && name != target.name) onRename(target, name.trim())
            },
            onDismiss = { renameTarget = null },
        )
    }

    moveTarget?.let { target ->
        PromptDialog(
            title = "移动到",
            label = "目标路径",
            initial = "/",
            placeholder = "/Books",
            helper = "填写设备上的目录，例如 /Books 或 /fonts",
            onConfirm = { dest ->
                moveTarget = null
                if (dest.isNotBlank()) onMove(target, dest.trim())
            },
            onDismiss = { moveTarget = null },
        )
    }

    deleteTarget?.let { target ->
        com.onepage.companion.ui.components.ConfirmDialog(
            title = "删除 ${target.name}？",
            message = if (target.isDirectory) "文件夹及其内容会被永久删除。" else "文件会从存储卡上永久删除。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                onDelete(target)
            },
            onDismiss = { deleteTarget = null },
        )
    }

    actionTarget?.let { target ->
        FormSheet(
            title = target.name,
            subtitle = if (target.isDirectory) "文件夹" else formatSize(target.size),
            onDismiss = { actionTarget = null },
        ) {
            if (!target.isDirectory) {
                ActionRow(
                    icon = Icons.Rounded.Download,
                    title = if (downloadBusy == target.name) "正在下载…" else "下载到手机",
                    subtitle = "保存后可直接分享或用其他应用打开",
                    onClick = {
                        if (downloadBusy != null) return@ActionRow
                        downloadBusy = target.name
                        scope.launch {
                            onDownload(target)
                                .onSuccess { file ->
                                    downloadBusy = null
                                    actionTarget = null
                                    shareFile(context, file)
                                }
                                .onFailure { downloadBusy = null }
                        }
                    },
                )
                Hairline(inset = 6.dp)
            }
            ActionRow(
                icon = Icons.Rounded.DriveFileMove,
                title = "重命名",
                onClick = { actionTarget = null; renameTarget = target },
            )
            Hairline(inset = 6.dp)
            ActionRow(
                icon = Icons.Rounded.FolderOpen,
                title = "移动到…",
                onClick = { actionTarget = null; moveTarget = target },
            )
            Hairline(inset = 6.dp)
            ActionRow(
                icon = Icons.Rounded.ErrorOutline,
                title = "删除",
                tint = c.danger,
                background = c.dangerSoft,
                onClick = { actionTarget = null; deleteTarget = target },
            )
        }
    }
}

@Composable
private fun PillAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    val c = onePageColors
    Row(
        Modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.ink2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = c.ink)
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = onePageColors
    val (icon, tint, bg) = when {
        entry.isDirectory -> Triple(Icons.Rounded.Folder, c.accent, c.accentSoft)
        entry.isEpub -> Triple(Icons.Rounded.AutoStories, c.amber, c.amberSoft)
        entry.extension == "cpfont" -> Triple(Icons.Rounded.TextFields, c.accent, c.accentSoft)
        entry.isImage -> Triple(Icons.Rounded.Image, c.ink2, c.cardSunken)
        else -> Triple(Icons.Rounded.Description, c.ink2, c.cardSunken)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (entry.isDirectory) "文件夹" else formatSize(entry.size),
                style = MaterialTheme.typography.bodySmall,
                color = c.ink3,
            )
        }
        if (entry.isEpub) {
            Pill("EPUB", PillTone.Warning)
        } else if (entry.extension == "cpfont") {
            Pill("字体", PillTone.Accent)
        }
    }
}

@Composable
private fun UploadPanel(tasks: List<UploadTask>, onClear: () -> Unit) {
    val c = onePageColors
    OnePageCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.UploadFile, null, tint = c.accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("上传队列", style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
            Text(
                "清除",
                style = MaterialTheme.typography.labelMedium,
                color = c.ink3,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        tasks.forEach { task ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    when (task.state) {
                        UploadState.Done -> Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = c.accent,
                            modifier = Modifier.size(16.dp),
                        )
                        UploadState.Failed -> Icon(
                            Icons.Rounded.ErrorOutline,
                            null,
                            tint = c.danger,
                            modifier = Modifier.size(16.dp),
                        )
                        else -> Text(
                            "${(task.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.ink3,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val animated by animateFloatAsState(task.fraction, label = "up")
                SlimProgress(
                    fraction = if (task.state == UploadState.Done) 1f else animated,
                    color = when (task.state) {
                        UploadState.Failed -> c.danger
                        else -> c.accent
                    },
                )
                if (task.message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(task.message, style = MaterialTheme.typography.bodySmall, color = c.danger)
                }
            }
        }
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    runCatching {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开或分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
