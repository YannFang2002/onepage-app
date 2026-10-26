package com.onepage.companion.ui.screens.firmware

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onepage.companion.core.firmware.FirmwareSource
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.core.update.notesPreview
import com.onepage.companion.ui.components.Hairline
import com.onepage.companion.ui.components.InfoLine
import com.onepage.companion.ui.components.LoadingRow
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.components.Pill
import com.onepage.companion.ui.components.PillTone
import com.onepage.companion.ui.components.PrimaryButton
import com.onepage.companion.ui.components.SecondaryButton
import com.onepage.companion.ui.components.SlimProgress
import com.onepage.companion.ui.components.formatSize
import com.onepage.companion.ui.screens.more.SubHeader
import com.onepage.companion.ui.theme.onePageColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 固件更新（OTA）：只对 `onepage-hub-*` 固件开放。
 *
 * 升级包从 Gitee 镜像 / GitHub Releases 拉取 `firmware.bin`，再 POST 到设备的
 * `/api/ota`；设备落 SD、校验、写下一分区后自行重启，App 只负责轮询状态。
 */
@Composable
fun FirmwareScreen(
    api: DeviceApi,
    currentVersion: String,
    onBack: () -> Unit,
) {
    val vm: FirmwareViewModel = viewModel(
        key = "firmware:${api.baseUrl}",
        factory = FirmwareViewModel.factory(api, currentVersion),
    )
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (vm.supported && state.phase == FirmwarePhase.Idle) vm.check()
    }

    FirmwareContent(
        state = state,
        supported = vm.supported,
        onCheck = vm::check,
        onInstall = vm::install,
        onBack = onBack,
    )
}

@Composable
fun FirmwareContent(
    state: FirmwareUiState,
    supported: Boolean,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onBack: () -> Unit,
) {
    val c = onePageColors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 112.dp)
    ) {
        SubHeader(eyebrow = "设备", title = "固件更新", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        if (!supported) {
            OnePageCard(padding = 18.dp) {
                Text("此固件不支持 App 内升级", style = MaterialTheme.typography.titleSmall, color = c.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "设备当前固件是「${state.currentVersion.ifBlank { "未知" }}」。"
                        + " 只有在 onepage-hub 固件上（版本号以 onepage-hub 开头）才开放 OTA，"
                        + " 其它固件请用 SD 卡或设备自带入口升级。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                )
            }
            return@Column
        }

        OnePageCard(padding = 18.dp) {
            InfoLine("当前固件", state.currentVersion)
            Hairline()
            InfoLine(
                label = "最新固件",
                value = state.latestVersion?.let { "v$it" } ?: "—",
                valueColor = if (state.hasUpdate) c.accent else c.ink,
            )
            Hairline()
            InfoLine("发布渠道", state.sourceLabel)
            if (state.firmwareBytes > 0) {
                Hairline()
                InfoLine("升级包", formatSize(state.firmwareBytes))
            }
            if (state.checkedAt > 0) {
                Hairline()
                InfoLine("上次检查", formatClock(state.checkedAt))
            }
        }

        Spacer(Modifier.height(14.dp))

        when (state.phase) {
            FirmwarePhase.Idle -> OnePageCard(padding = 18.dp) {
                Text("检查固件更新", style = MaterialTheme.typography.titleSmall, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "从 Gitee 镜像拉取最新固件清单，找不到时回退 GitHub。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "检查更新", icon = Icons.Rounded.Refresh, onClick = onCheck)
            }

            FirmwarePhase.Checking -> OnePageCard(padding = 18.dp) {
                LoadingRow("正在查询固件版本…")
            }

            FirmwarePhase.UpToDate -> OnePageCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("已是最新", PillTone.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(state.currentVersion, style = MaterialTheme.typography.titleSmall, color = c.ink)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Gitee 镜像与 GitHub 上都没有比设备更新的固件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryButton(text = "再次检查", icon = Icons.Rounded.Refresh, onClick = onCheck)
            }

            FirmwarePhase.Available -> OnePageCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("发现新固件", style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Spacer(Modifier.width(10.dp))
                    Pill(state.release?.tag ?: "新固件", PillTone.Accent)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${state.currentVersion} → ${state.release?.tag.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                )
                val published = state.release?.publishedDate.orEmpty()
                if (published.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    InfoLine("发布日期", published)
                }
                val notes = state.release?.notes.orEmpty()
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Hairline()
                    Spacer(Modifier.height(14.dp))
                    Text("更新说明", style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        notesPreview(notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.ink2,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "升级会写入备用分区并让设备重启，约 1–3 分钟。过程中请保持设备通电、手机留在同一网络。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(text = "下载并升级", icon = Icons.Rounded.Upgrade, onClick = onInstall)
            }

            FirmwarePhase.Downloading,
            FirmwarePhase.Uploading,
            FirmwarePhase.Flashing,
            -> OnePageCard(padding = 18.dp) {
                Text("正在升级", style = MaterialTheme.typography.titleSmall, color = c.ink)
                Spacer(Modifier.height(10.dp))
                Text(state.progressLabel, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                Spacer(Modifier.height(12.dp))
                SlimProgress(fraction = state.progress)
                Spacer(Modifier.height(12.dp))
                Text(
                    "请勿断电、勿关闭 App。设备写入完成后会自行重启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                )
            }

            FirmwarePhase.Done -> OnePageCard(padding = 18.dp) {
                Pill("升级完成", PillTone.Accent)
                Spacer(Modifier.height(10.dp))
                Text(
                    buildString {
                        append("固件已写入")
                        if (state.resultVersion.isNotBlank()) append("：${state.resultVersion}")
                        if (state.resultSlot.isNotBlank()) append("（${state.resultSlot} 分区）")
                        append("。设备正在重启，连接会短暂中断，回到设备页重新连接即可。")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryButton(text = "重新检查版本", icon = Icons.Rounded.Refresh, onClick = onCheck)
            }

            FirmwarePhase.Failed -> OnePageCard(padding = 18.dp) {
                Text("升级没有完成", style = MaterialTheme.typography.titleSmall, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    state.error ?: "未知错误",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.danger,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryButton(
                    text = if (state.release != null) "重试升级" else "重试检查",
                    icon = Icons.Rounded.Refresh,
                    onClick = if (state.release != null) onInstall else onCheck,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "固件来自 Gitee 镜像（${FirmwareSource.Gitee.repoUrl}），不可用时回退 GitHub"
                + "（${FirmwareSource.GitHub.repoUrl}）。升级包必须是 Release 里名为 firmware.bin 的附件；"
                + " 设备会用 ESP32 OTA 分区校验后写入。",
            style = MaterialTheme.typography.bodySmall,
            color = c.ink3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}

private fun formatClock(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
