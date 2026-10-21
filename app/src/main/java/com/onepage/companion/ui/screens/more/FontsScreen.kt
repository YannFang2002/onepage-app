package com.onepage.companion.ui.screens.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onepage.companion.core.model.FontFamily
import com.onepage.companion.core.model.FontLibrary
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.ui.components.ConfirmDialog
import com.onepage.companion.ui.components.EmptyState
import com.onepage.companion.ui.components.FormSheet
import com.onepage.companion.ui.components.Hairline
import com.onepage.companion.ui.components.LoadingRow
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.components.OnePageTextField
import com.onepage.companion.ui.components.Pill
import com.onepage.companion.ui.components.PillTone
import com.onepage.companion.ui.components.PrimaryButton
import com.onepage.companion.ui.screens.files.formatSize
import com.onepage.companion.ui.theme.onePageColors
import kotlinx.coroutines.launch

private data class PendingFont(val family: String, val fileName: String, val bytes: ByteArray)

@Composable
fun FontsScreen(api: DeviceApi, onBack: () -> Unit) {
    val c = onePageColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var library by remember { mutableStateOf<FontLibrary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUpload by remember { mutableStateOf<PendingFont?>(null) }
    var uploadBusy by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FontFamily?>(null) }

    suspend fun reload() {
        loading = true
        runCatching { api.fonts() }
            .onSuccess { library = it; error = null }
            .onFailure { error = it.message ?: "读取字体失败" }
        loading = false
    }

    LaunchedEffect(api) { reload() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "font.cpfont"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            val family = name.substringBeforeLast('_').substringBeforeLast('.').ifBlank { "Custom" }
            pendingUpload = PendingFont(family, name, bytes)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 112.dp)
    ) {
        SubHeader(
            eyebrow = "内容",
            title = "字体",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.Upload, "上传字体", tint = c.accent, modifier = Modifier.size(20.dp))
                }
            },
        )

        Spacer(Modifier.height(12.dp))

        if (error != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.dangerSoft)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.danger)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (loading) {
            LoadingRow("读取字体列表…")
        } else {
            val lib = library
            if (lib == null || lib.families.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.TextFields,
                    title = "还没有自定义字体",
                    body = "上传 .cpfont 文件后，设备会在「设置 → 字体」里列出来。",
                    action = {
                        PrimaryButton(
                            text = "上传 .cpfont",
                            icon = Icons.Rounded.Upload,
                            onClick = { picker.launch(arrayOf("*/*")) },
                        )
                    },
                )
            } else {
                Text(
                    "${lib.families.size} / ${lib.maxFamilies} 个字体家族",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.ink3,
                )
                Spacer(Modifier.height(10.dp))
                OnePageCard(padding = 0.dp) {
                    lib.families.forEachIndexed { index, family ->
                        FontRow(family = family, onDelete = { deleteTarget = family })
                        if (index != lib.families.lastIndex) Hairline(inset = 18.dp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "提示：设备最多加载 ${lib.maxFamilies} 个 SD 字体家族，字号以文件名中的 _<size> 区分。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    pendingUpload?.let { pending ->
        var familyName by remember(pending) { mutableStateOf(pending.family) }
        FormSheet(
            title = "安装字体",
            subtitle = "${pending.bytes.size / 1024} KB · 上传后需在设备设置里选择",
            onDismiss = { pendingUpload = null },
        ) {
            OnePageTextField(
                value = familyName,
                onValueChange = { familyName = it },
                label = "字体家族名称",
                placeholder = "Noto Serif",
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = if (uploadBusy) "上传中…" else "上传到设备",
                loading = uploadBusy,
                enabled = familyName.isNotBlank(),
                onClick = {
                    uploadBusy = true
                    scope.launch {
                        runCatching { api.uploadFont(familyName.trim(), pending.fileName, pending.bytes) }
                            .onSuccess {
                                uploadBusy = false
                                pendingUpload = null
                                reload()
                            }
                            .onFailure {
                                uploadBusy = false
                                error = it.message ?: "上传失败"
                            }
                    }
                },
            )
        }
    }

    deleteTarget?.let { family ->
        ConfirmDialog(
            title = "删除 ${family.name}？",
            message = "该家族下 ${family.files.size} 个字体文件会从存储卡删除。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    runCatching { api.deleteFontFamily(family.name) }
                        .onFailure { error = it.message ?: "删除失败" }
                    reload()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun FontRow(family: FontFamily, onDelete: () -> Unit) {
    val c = onePageColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.TextFields, null, tint = c.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                family.name,
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "字号 ${family.sizeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatSize(family.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                )
            }
        }
        Pill("${family.files.size} 个文件", PillTone.Neutral)
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, "删除", tint = c.ink3, modifier = Modifier.size(18.dp))
        }
    }
}
