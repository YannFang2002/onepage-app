package com.onepage.companion.ui.screens.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.ui.PageHeader
import com.onepage.companion.ui.SessionUiState
import com.onepage.companion.ui.components.IconBubble
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.components.Pill
import com.onepage.companion.ui.components.PillTone
import com.onepage.companion.ui.components.PromptDialog
import com.onepage.companion.ui.components.SignalBars
import com.onepage.companion.ui.components.StatCell
import com.onepage.companion.ui.theme.onePageColors

@Composable
fun HomeScreen(
    api: DeviceApi,
    state: SessionUiState,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onRename: (String) -> Unit,
    onOpenTab: (Int) -> Unit,
) {
    val c = onePageColors
    val status = state.status
    var renameOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 112.dp)
    ) {
        PageHeader(
            eyebrow = "设备",
            title = state.device?.name ?: "OnePage",
            trailing = {
                Pill(
                    text = when {
                        status?.isAp == true -> "热点"
                        status != null -> "已连接"
                        else -> "离线"
                    },
                    tone = if (status != null) PillTone.Accent else PillTone.Danger,
                )
            },
        )

        Spacer(Modifier.height(14.dp))

        if (state.error != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.dangerSoft)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.danger,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- hero status card
        OnePageCard(padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (c.isDark) c.accentSoft else c.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (status?.isAp == true) Icons.Rounded.WifiTethering else Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = if (c.isDark) c.accent else c.card,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        status?.let { if (it.isAp) "设备热点已开启" else "局域网已连接" } ?: "等待响应",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        status?.ip?.takeIf { it.isNotBlank() } ?: state.device?.host.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.ink3,
                    )
                }
                SignalBars(bars = status?.signalBars ?: 0)
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth()) {
                StatCell(
                    label = "信号",
                    value = status?.let { if (it.isAp) "热点" else "${it.rssi}" } ?: "—",
                    hint = if (status != null && !status.isAp) "dBm" else null,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "空闲内存",
                    value = status?.freeHeapKb?.toString() ?: "—",
                    hint = "KB",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                StatCell(
                    label = "运行时长",
                    value = status?.uptimeLabel ?: "—",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "固件版本",
                    value = status?.version?.takeIf { it.isNotBlank() } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("快捷操作", style = MaterialTheme.typography.labelMedium, color = c.ink3)
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(
                icon = Icons.Rounded.FolderOpen,
                title = "传输文件",
                subtitle = "上传 / 下载",
                modifier = Modifier.weight(1f),
                onClick = { onOpenTab(2) },
            )
            QuickAction(
                icon = Icons.Rounded.Tune,
                title = "阅读设置",
                subtitle = "字体 / 排版",
                modifier = Modifier.weight(1f),
                onClick = { onOpenTab(1) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(
                icon = Icons.Rounded.TextFields,
                title = "字体管理",
                subtitle = "SD 字体",
                modifier = Modifier.weight(1f),
                onClick = { onOpenTab(3) },
            )
            QuickAction(
                icon = Icons.Rounded.Memory,
                title = "设备信息",
                subtitle = "型号 / 网络",
                modifier = Modifier.weight(1f),
                onClick = { onOpenTab(3) },
            )
        }

        Spacer(Modifier.height(22.dp))
        Text("连接", style = MaterialTheme.typography.labelMedium, color = c.ink3)
        Spacer(Modifier.height(10.dp))
        OnePageCard(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.device?.address ?: "—", style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "HTTP 80 · WebSocket ${state.device?.wsPort ?: 81}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.ink3,
                    )
                }
                Text(
                    "重命名",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.accentSoft)
                        .clickable { renameOpen = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "断开连接",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.danger,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.dangerSoft)
                        .clickable { onDisconnect() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "刷新",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.cardSunken)
                        .clickable { onRefresh() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, null, tint = c.ink3, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("每 6 秒自动同步设备状态", style = MaterialTheme.typography.bodySmall, color = c.ink3)
        }
    }

    if (renameOpen) {
        PromptDialog(
            title = "重命名设备",
            label = "显示名称",
            initial = state.device?.name.orEmpty(),
            onConfirm = { name ->
                renameOpen = false
                if (name.isNotBlank()) onRename(name.trim())
            },
            onDismiss = { renameOpen = false },
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = onePageColors
    OnePageCard(modifier = modifier, onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon = icon, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                    maxLines = 1,
                )
            }
        }
    }
}
