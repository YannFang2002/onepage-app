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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onepage.companion.core.model.WifiNetwork
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
fun WifiScreen(api: DeviceApi, onBack: () -> Unit) {
    val c = onePageColors
    val scope = rememberCoroutineScope()

    var networks by remember { mutableStateOf<List<WifiNetwork>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WifiNetwork?>(null) }

    suspend fun reload() {
        loading = true
        runCatching { api.wifiNetworks() }
            .onSuccess { networks = it; error = null }
            .onFailure { error = it.message ?: "读取 Wi-Fi 失败" }
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
            eyebrow = "网络",
            title = "Wi-Fi 凭据",
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
            loading -> LoadingRow("读取网络列表…")
            networks.isNullOrEmpty() -> EmptyState(
                icon = Icons.Rounded.Wifi,
                title = "设备还没保存任何 Wi-Fi",
                body = "提前写入家里的 Wi-Fi，阅读器开机后就不用再输密码。",
                action = {
                    PrimaryButton(text = "添加 Wi-Fi", icon = Icons.Rounded.Add, onClick = { adding = true })
                },
            )
            else -> {
                OnePageCard(padding = 0.dp) {
                    networks!!.forEachIndexed { index, network ->
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
                                    .background(if (network.isLastConnected) c.accentSoft else c.cardSunken),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Wifi,
                                    null,
                                    tint = if (network.isLastConnected) c.accent else c.ink2,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    network.ssid,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = c.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    buildString {
                                        append(if (network.hasPassword) "已保存密码" else "开放网络")
                                        if (network.isLastConnected) append(" · 上次连接")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.ink3,
                                )
                            }
                            if (network.isLastConnected) {
                                Pill("当前", PillTone.Accent)
                            }
                            IconButton(onClick = { deleteTarget = network }) {
                                Icon(Icons.Rounded.Delete, "删除", tint = c.ink3, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (index != networks!!.lastIndex) Hairline(inset = 18.dp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "密码只写入设备，App 不会保存，也不会从设备读回明文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (adding) {
        var ssid by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }

        FormSheet(title = "添加 Wi-Fi", onDismiss = { adding = false }) {
            OnePageTextField(ssid, { ssid = it }, label = "网络名称（SSID）", placeholder = "Home-2.4G")
            Spacer(Modifier.height(10.dp))
            OnePageTextField(password, { password = it }, label = "密码", password = true)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = "保存到设备",
                loading = busy,
                enabled = ssid.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { api.saveWifi(ssid.trim(), password) }
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

    deleteTarget?.let { network ->
        ConfirmDialog(
            title = "删除 ${network.ssid}？",
            message = "设备将忘记这个网络的密码。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    runCatching { api.deleteWifi(network.index) }
                        .onFailure { error = it.message ?: "删除失败" }
                    reload()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
