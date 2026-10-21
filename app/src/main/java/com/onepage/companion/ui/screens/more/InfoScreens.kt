package com.onepage.companion.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.onepage.companion.BuildConfig
import com.onepage.companion.ui.SessionUiState
import com.onepage.companion.ui.components.Hairline
import com.onepage.companion.ui.components.InfoLine
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.theme.onePageColors

@Composable
fun DeviceInfoScreen(state: SessionUiState, onBack: () -> Unit) {
    val c = onePageColors
    val status = state.status

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 112.dp)
    ) {
        SubHeader(eyebrow = "设备", title = "设备信息", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        OnePageCard(padding = 18.dp) {
            InfoLine("名称", state.device?.name ?: "—")
            Hairline()
            InfoLine("型号", status?.device?.ifBlank { "OnePage" } ?: "—")
            Hairline()
            InfoLine("固件版本", status?.version?.ifBlank { "—" } ?: "—")
        }

        Spacer(Modifier.height(12.dp))
        OnePageCard(padding = 18.dp) {
            InfoLine("地址", state.device?.address ?: "—")
            Hairline()
            InfoLine("WebSocket", "端口 ${state.device?.wsPort ?: 81}")
            Hairline()
            InfoLine("网络模式", status?.let { if (it.isAp) "设备热点 (AP)" else "局域网 (STA)" } ?: "—")
            Hairline()
            InfoLine("IP", status?.ip?.ifBlank { "—" } ?: "—")
            Hairline()
            InfoLine("信号", status?.let { if (it.isAp) "热点不适用" else "${it.rssi} dBm" } ?: "—")
        }

        Spacer(Modifier.height(12.dp))
        OnePageCard(padding = 18.dp) {
            InfoLine("空闲内存", status?.let { "${it.freeHeapKb} KB" } ?: "—")
            Hairline()
            InfoLine("运行时长", status?.uptimeLabel ?: "—")
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "内存与时长来自设备 /api/status，每 6 秒刷新一次。",
            style = MaterialTheme.typography.bodySmall,
            color = c.ink3,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = onePageColors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 112.dp)
    ) {
        SubHeader(eyebrow = "关于", title = "OnePage App", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.ink)
                .padding(24.dp)
        ) {
            Column {
                Text("OnePage", style = MaterialTheme.typography.headlineMedium, color = c.card)
                Spacer(Modifier.height(6.dp))
                Text(
                    "阅读器的手机遥控器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.card.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.card.copy(alpha = 0.85f),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        OnePageCard(padding = 18.dp) {
            Text("V1 的边界", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "App 只调用阅读器固件已经提供的 HTTP / WebSocket 接口，不修改任何设备端代码。"
                    + " 因此功能范围与设备自带的网页版一致：设备状态、设置、文件、字体、OPDS、Wi-Fi 凭据。",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink2,
            )
        }

        Spacer(Modifier.height(12.dp))
        OnePageCard(padding = 18.dp) {
            Text("连接方式", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "· 局域网：手机与阅读器连同一个路由器\n"
                    + "· 热点：设备「文件传输 → 创建热点」，SSID CrossPoint-Reader，默认 192.168.4.1\n"
                    + "· 自动发现：UDP 8134 广播，无需知道 IP",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink2,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "V1 不支持蓝牙直连：固件当前只把 BLE 用作外接翻页器（HID Host），没有可供手机连接的服务。",
            style = MaterialTheme.typography.bodySmall,
            color = c.ink3,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
