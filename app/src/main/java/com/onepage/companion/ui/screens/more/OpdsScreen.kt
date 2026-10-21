package com.onepage.companion.ui.screens.more

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onepage.companion.core.model.OpdsServer
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
import com.onepage.companion.ui.theme.onePageColors
import kotlinx.coroutines.launch

@Composable
fun OpdsScreen(api: DeviceApi, onBack: () -> Unit) {
    val c = onePageColors
    val scope = rememberCoroutineScope()

    var servers by remember { mutableStateOf<List<OpdsServer>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<OpdsServer?>(null) }

    suspend fun reload() {
        loading = true
        runCatching { api.opdsServers() }
            .onSuccess { servers = it; error = null }
            .onFailure { error = it.message ?: "读取 OPDS 失败" }
        loading = false
    }

    LaunchedEffect(api) { reload() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 112.dp)
    ) {
        SubHeader(
            eyebrow = "内容",
            title = "OPDS 订阅源",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { adding = true }) {
                    Icon(Icons.Rounded.Add, "添加", tint = c.accent, modifier = Modifier.size(22.dp))
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

        when {
            loading -> LoadingRow("读取订阅源…")
            servers.isNullOrEmpty() -> EmptyState(
                icon = Icons.Rounded.Cloud,
                title = "还没有订阅源",
                body = "添加 Calibre 内容服务器或公共 OPDS 地址后，设备就能直接下载书。",
                action = {
                    PrimaryButton(text = "添加订阅源", icon = Icons.Rounded.Add, onClick = { adding = true })
                },
            )
            else -> OnePageCard(padding = 0.dp) {
                servers!!.forEachIndexed { index, server ->
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
                                .background(c.cardSunken),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Cloud, null, tint = c.ink2, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                server.name.ifBlank { "未命名" },
                                style = MaterialTheme.typography.titleSmall,
                                color = c.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (server.hasPassword) {
                            Pill("有密码", PillTone.Neutral)
                        }
                        IconButton(onClick = { deleteTarget = server }) {
                            Icon(Icons.Rounded.Delete, "删除", tint = c.ink3, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index != servers!!.lastIndex) Hairline(inset = 18.dp)
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("http://") }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }

        FormSheet(title = "添加订阅源", onDismiss = { adding = false }) {
            OnePageTextField(name, { name = it }, label = "名称", placeholder = "Calibre")
            Spacer(Modifier.height(10.dp))
            OnePageTextField(
                url,
                { url = it },
                label = "地址",
                placeholder = "http://192.168.1.10:8080/opds",
                keyboardType = KeyboardType.Uri,
            )
            Spacer(Modifier.height(10.dp))
            OnePageTextField(username, { username = it }, label = "用户名（可选）")
            Spacer(Modifier.height(10.dp))
            OnePageTextField(password, { password = it }, label = "密码（可选）", password = true)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "保存",
                loading = busy,
                enabled = name.isNotBlank() && url.startsWith("http"),
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { api.saveOpds(name.trim(), url.trim(), username.trim(), password) }
                            .onSuccess {
                                busy = false
                                adding = false
                                reload()
                            }
                            .onFailure {
                                busy = false
                                error = it.message ?: "保存失败"
                            }
                    }
                },
            )
        }
    }

    deleteTarget?.let { server ->
        ConfirmDialog(
            title = "删除 ${server.name}？",
            message = "设备上的这个订阅源配置会被移除。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    runCatching { api.deleteOpds(server.index) }
                        .onFailure { error = it.message ?: "删除失败" }
                    reload()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
