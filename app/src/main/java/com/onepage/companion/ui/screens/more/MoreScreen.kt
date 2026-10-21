package com.onepage.companion.ui.screens.more

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.ui.PageHeader
import com.onepage.companion.ui.SessionUiState
import com.onepage.companion.ui.components.ActionRow
import com.onepage.companion.ui.components.Hairline
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.theme.onePageColors

private enum class MorePage { Root, Fonts, Opds, Wifi, Device, About }

@Composable
fun MoreScreen(
    api: DeviceApi,
    state: SessionUiState,
    onDisconnect: () -> Unit,
) {
    var page by remember { mutableStateOf(MorePage.Root) }
    BackHandler(enabled = page != MorePage.Root) { page = MorePage.Root }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(tween(200)) { it / 6 } + fadeOut(tween(160)))
        },
        label = "more",
    ) { target ->
        when (target) {
            MorePage.Root -> MoreHome(
                api = api,
                state = state,
                onOpen = { page = it },
                onDisconnect = onDisconnect,
            )
            MorePage.Fonts -> FontsScreen(api = api, onBack = { page = MorePage.Root })
            MorePage.Opds -> OpdsScreen(api = api, onBack = { page = MorePage.Root })
            MorePage.Wifi -> WifiScreen(api = api, onBack = { page = MorePage.Root })
            MorePage.Device -> DeviceInfoScreen(state = state, onBack = { page = MorePage.Root })
            MorePage.About -> AboutScreen(onBack = { page = MorePage.Root })
        }
    }
}

@Composable
private fun MoreHome(
    api: DeviceApi,
    state: SessionUiState,
    onOpen: (MorePage) -> Unit,
    onDisconnect: () -> Unit,
) {
    val c = onePageColors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 112.dp)
    ) {
        PageHeader(eyebrow = "管理", title = "更多")

        Spacer(Modifier.height(14.dp))
        Text("内容", style = MaterialTheme.typography.labelMedium, color = c.ink3)
        Spacer(Modifier.height(8.dp))
        OnePageCard(padding = 10.dp) {
            ActionRow(
                icon = Icons.Rounded.TextFields,
                title = "字体管理",
                subtitle = "查看、上传、删除 SD 卡字体",
                onClick = { onOpen(MorePage.Fonts) },
            )
            Hairline(inset = 10.dp)
            ActionRow(
                icon = Icons.Rounded.Cloud,
                title = "OPDS 订阅源",
                subtitle = "添加书库地址与账号",
                onClick = { onOpen(MorePage.Opds) },
            )
        }

        Spacer(Modifier.height(18.dp))
        Text("网络", style = MaterialTheme.typography.labelMedium, color = c.ink3)
        Spacer(Modifier.height(8.dp))
        OnePageCard(padding = 10.dp) {
            ActionRow(
                icon = Icons.Rounded.Wifi,
                title = "Wi-Fi 凭据",
                subtitle = "设备保存的无线网络",
                onClick = { onOpen(MorePage.Wifi) },
            )
        }

        Spacer(Modifier.height(18.dp))
        Text("设备", style = MaterialTheme.typography.labelMedium, color = c.ink3)
        Spacer(Modifier.height(8.dp))
        OnePageCard(padding = 10.dp) {
            ActionRow(
                icon = Icons.Rounded.Memory,
                title = "设备信息",
                subtitle = "型号、固件、内存、网络",
                onClick = { onOpen(MorePage.Device) },
            )
            Hairline(inset = 10.dp)
            ActionRow(
                icon = Icons.Rounded.Info,
                title = "关于 OnePage App",
                subtitle = "版本与说明",
                onClick = { onOpen(MorePage.About) },
            )
        }

        Spacer(Modifier.height(18.dp))
        OnePageCard(padding = 10.dp) {
            ActionRow(
                icon = Icons.Rounded.PowerSettingsNew,
                title = "断开连接",
                subtitle = state.device?.address.orEmpty(),
                tint = c.danger,
                background = c.dangerSoft,
                onClick = onDisconnect,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "V1 只调用设备已有的接口，不修改任何固件行为。",
            style = MaterialTheme.typography.bodySmall,
            color = c.ink3,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
