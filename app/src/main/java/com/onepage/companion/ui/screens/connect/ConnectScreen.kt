package com.onepage.companion.ui.screens.connect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onepage.companion.core.model.DiscoveredDevice
import com.onepage.companion.core.model.SavedDevice
import com.onepage.companion.ui.Phase
import com.onepage.companion.ui.SessionUiState
import com.onepage.companion.ui.components.FormSheet
import com.onepage.companion.ui.components.IconBubble
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.components.OnePageTextField
import com.onepage.companion.ui.components.Pill
import com.onepage.companion.ui.components.PillTone
import com.onepage.companion.ui.components.PrimaryButton
import com.onepage.companion.ui.components.PromptDialog
import com.onepage.companion.ui.components.SecondaryButton
import com.onepage.companion.ui.theme.onePageColors

@Composable
fun ConnectScreen(
    state: SessionUiState,
    onScan: () -> Unit,
    onScanQr: () -> Unit,
    onConnect: (host: String, port: Int, wsPort: Int) -> Unit,
    onConnectSaved: (SavedDevice) -> Unit,
    onForget: (SavedDevice) -> Unit,
    onRenameSaved: (SavedDevice, String) -> Unit,
) {
    val c = onePageColors
    var manualSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedDevice?>(null) }

    LaunchedEffect(Unit) { if (state.saved.isEmpty()) onScan() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 14.dp, bottom = 30.dp)
        ) {
            Brand()
            Spacer(Modifier.height(30.dp))

            Text(
                "连接阅读器",
                style = MaterialTheme.typography.displayMedium,
                color = c.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "手机与 OnePage 处于同一 Wi-Fi，或先让设备创建热点。",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink2,
            )

            Spacer(Modifier.height(26.dp))

            // Primary path: the device prints a Wi-Fi QR and a URL QR on its screen.
            OnePageCard(onClick = onScanQr, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = if (c.isDark) Color(0xFF06231C) else Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "扫描设备二维码",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.ink,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "扫 Wi-Fi 码自动加入热点，扫网址码直接连接",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.ink3,
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = c.ink3,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            RadarCard(
                scanning = state.scanning || state.phase == Phase.Connecting,
                connecting = state.phase == Phase.Connecting,
                found = state.discovered.size,
                onScan = onScan,
            )

            if (state.error != null) {
                Spacer(Modifier.height(14.dp))
                ErrorBanner(state.error)
            }

            if (state.discovered.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("发现的设备", style = MaterialTheme.typography.labelMedium, color = c.ink3)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.discovered.forEach { d ->
                        DiscoveredRow(d) {
                            onConnect(d.host, 80, d.wsPort)
                        }
                    }
                }
            }

            if (state.saved.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Text("已保存", style = MaterialTheme.typography.labelMedium, color = c.ink3)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.saved.sortedByDescending { it.lastSeen }.forEach { saved ->
                        SavedRow(
                            saved = saved,
                            onClick = { onConnectSaved(saved) },
                            onRename = { renameTarget = saved },
                            onForget = { onForget(saved) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SecondaryButton(
                text = "手动输入 IP",
                onClick = { manualSheet = true },
                icon = Icons.Rounded.Keyboard,
            )

            Spacer(Modifier.height(22.dp))
            Text(
                "设备端：设置 → 文件传输 → 创建热点（CrossPoint-Reader），或选择加入你的 Wi-Fi。",
                style = MaterialTheme.typography.bodySmall,
                color = c.ink3,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (manualSheet) {
        ManualConnectSheet(
            onDismiss = { manualSheet = false },
            onConfirm = { host, port ->
                manualSheet = false
                onConnect(host, port, 81)
            },
        )
    }

    renameTarget?.let { target ->
        PromptDialog(
            title = "重命名设备",
            label = "名称",
            initial = target.name,
            onConfirm = { name ->
                renameTarget = null
                if (name.isNotBlank()) onRenameSaved(target, name.trim())
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(onePageColors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.EditNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "ONEPAGE",
            style = MaterialTheme.typography.labelMedium,
            color = onePageColors.ink3,
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
        )
    }
}

@Composable
private fun RadarCard(
    scanning: Boolean,
    connecting: Boolean,
    found: Int,
    onScan: () -> Unit,
) {
    val c = onePageColors
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )

    OnePageCard(padding = 0.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension * 0.46f
                repeat(3) { i ->
                    val phase = (pulse + i / 3f) % 1f
                    val radius = maxRadius * (0.32f + phase * 0.68f)
                    drawCircle(
                        color = c.accent.copy(alpha = (1f - phase) * 0.28f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(c.card),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Radar,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                AnimatedContent(
                    targetState = when {
                        connecting -> "正在连接…"
                        scanning -> "正在搜索设备…"
                        found > 0 -> "发现 $found 台设备"
                        else -> "未发现设备"
                    },
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "radarText",
                ) { text ->
                    Text(text, style = MaterialTheme.typography.titleSmall, color = c.ink)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (!scanning && found == 0) "点击右下角重新扫描" else "保持设备屏幕亮着",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                )
            }

            Surface(
                onClick = onScan,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .size(42.dp),
                shape = CircleShape,
                color = c.cardSunken,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Wifi, null, tint = c.ink2, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun DiscoveredRow(device: DiscoveredDevice, onClick: () -> Unit) {
    val c = onePageColors
    OnePageCard(onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Router, null, tint = c.accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.hostname.ifBlank { "OnePage 阅读器" },
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                )
                Text(device.host, style = MaterialTheme.typography.bodySmall, color = c.ink3)
            }
            Pill("可连接", PillTone.Accent)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ArrowForward, null, tint = c.ink3, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SavedRow(
    saved: SavedDevice,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onForget: () -> Unit,
) {
    val c = onePageColors
    var menu by remember { mutableStateOf(false) }
    OnePageCard(onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.cardSunken),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Wifi, null, tint = c.ink2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(saved.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                Text(saved.address, style = MaterialTheme.typography.bodySmall, color = c.ink3)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.MoreHoriz, null, tint = c.ink3, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("移除") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onForget() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val c = onePageColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.dangerSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(c.danger)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = c.danger,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ManualConnectSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    FormSheet(title = "手动连接", onDismiss = onDismiss) {
        Text("手动连接", style = MaterialTheme.typography.headlineSmall, color = onePageColors.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "输入设备屏幕上显示的 IP。热点模式通常是 192.168.4.1。",
            style = MaterialTheme.typography.bodyMedium,
            color = onePageColors.ink3,
        )
        Spacer(Modifier.height(18.dp))
        OnePageTextField(
            value = host,
            onValueChange = { host = it },
            label = "IP 地址",
            placeholder = "192.168.4.1",
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(12.dp))
        OnePageTextField(
            value = port,
            onValueChange = { port = it.filter { ch -> ch.isDigit() } },
            label = "端口",
            placeholder = "80",
            keyboardType = KeyboardType.Number,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "连接",
            enabled = host.isNotBlank(),
            onClick = { onConfirm(host.trim(), port.toIntOrNull() ?: 80) },
        )
    }
}

