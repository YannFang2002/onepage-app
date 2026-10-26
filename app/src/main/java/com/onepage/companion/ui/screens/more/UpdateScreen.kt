package com.onepage.companion.ui.screens.more

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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onepage.companion.core.update.AppRelease
import com.onepage.companion.core.update.UpdateSource
import com.onepage.companion.core.update.notesPreview
import com.onepage.companion.ui.UpdatePhase
import com.onepage.companion.ui.UpdateUiState
import com.onepage.companion.ui.components.Hairline
import com.onepage.companion.ui.components.InfoLine
import com.onepage.companion.ui.components.LoadingRow
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.components.Pill
import com.onepage.companion.ui.components.PillTone
import com.onepage.companion.ui.components.PrimaryButton
import com.onepage.companion.ui.components.SecondaryButton
import com.onepage.companion.ui.components.formatSize
import com.onepage.companion.ui.components.openUrl
import com.onepage.companion.ui.theme.onePageColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 软件更新页：向 Gitee 镜像 / GitHub Releases 查询本项目的最新版本，并给出下载入口。
 *
 * 下载交给系统浏览器 / 下载管理器：App 不申请「安装未知应用」权限，也不绕过系统
 * 安装器。后者既需要额外的高危权限，也违背 Android 的更新惯例。
 */
@Composable
fun UpdateScreen(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onBack: () -> Unit,
) {
    val c = onePageColors
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 112.dp)
    ) {
        SubHeader(eyebrow = "应用", title = "软件更新", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        OnePageCard(padding = 18.dp) {
            InfoLine("当前版本", "v${state.currentVersion}")
            Hairline()
            InfoLine(
                label = "最新版本",
                value = state.latestVersion?.let { "v$it" } ?: "—",
                valueColor = if (state.hasUpdate) c.accent else c.ink,
            )
            Hairline()
            InfoLine("发布渠道", state.sourceLabel)
            if (state.checkedAt > 0) {
                Hairline()
                InfoLine("上次检查", formatClock(state.checkedAt))
            }
        }

        Spacer(Modifier.height(14.dp))

        when (state.phase) {
            UpdatePhase.Idle -> IdleCard(onCheck = onCheck)

            UpdatePhase.Checking -> OnePageCard(padding = 18.dp) {
                LoadingRow("正在向 Gitee / GitHub 查询最新版本…")
            }

            UpdatePhase.Failed -> OnePageCard(padding = 18.dp) {
                Text("检查失败", style = MaterialTheme.typography.titleSmall, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    state.error ?: "未知错误",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.danger,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryButton(text = "重试", icon = Icons.Rounded.Refresh, onClick = onCheck)
            }

            UpdatePhase.UpToDate -> OnePageCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("已是最新", PillTone.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "v${state.currentVersion}",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.ink,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Gitee 镜像与 GitHub 上都没有比当前更新的正式版本。设备固件不在这个更新通道里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryButton(text = "再次检查", icon = Icons.Rounded.Refresh, onClick = onCheck)
            }

            UpdatePhase.Available -> {
                val release = state.release
                if (release != null) {
                    AvailableCard(
                        release = release,
                        currentVersion = state.currentVersion,
                        fallbackUrl = state.downloadUrl ?: UpdateSource.GitHub.releasesPageUrl,
                    ) { url -> openUrl(context, url) }
                } else {
                    IdleCard(onCheck = onCheck)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "更新信息优先读 Gitee 镜像（${UpdateSource.Gitee.repoUrl}），不可用时回退 GitHub（${UpdateSource.GitHub.repoUrl}）。"
                + " 安装包由系统浏览器下载，App 不申请「安装未知应用」权限；"
                + " 两个站点在部分网络下可能需要代理。",
            style = MaterialTheme.typography.bodySmall,
            color = c.ink3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun IdleCard(onCheck: () -> Unit) {
    val c = onePageColors
    OnePageCard(padding = 18.dp) {
        Text("还没检查过", style = MaterialTheme.typography.titleSmall, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "每次启动会自动查一次；也可以现在手动检查。",
            style = MaterialTheme.typography.bodyMedium,
            color = c.ink2,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "检查更新", icon = Icons.Rounded.Refresh, onClick = onCheck)
    }
}

@Composable
private fun AvailableCard(
    release: AppRelease,
    currentVersion: String,
    fallbackUrl: String,
    onOpenUrl: (String) -> Unit,
) {
    val c = onePageColors
    val apk = release.apk

    OnePageCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("发现新版本", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.width(10.dp))
            Pill(release.tag, PillTone.Accent)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "当前 v$currentVersion → 最新 v${release.version}",
            style = MaterialTheme.typography.bodyMedium,
            color = c.ink2,
        )
        if (release.publishedDate.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            InfoLine("发布日期", release.publishedDate)
        }
        if (apk != null) {
            InfoLine("安装包", "${apk.name} · ${formatSize(apk.size)}")
        }

        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            Text("更新说明", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                notesPreview(release.notes),
                style = MaterialTheme.typography.bodySmall,
                color = c.ink2,
            )
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = if (apk != null) "下载安装包" else "打开 Release 页面",
            icon = Icons.Rounded.Download,
            onClick = { onOpenUrl(apk?.url ?: release.pageUrl.ifBlank { fallbackUrl }) },
        )
        if (release.pageUrl.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "查看 Release 页面",
                icon = Icons.Rounded.OpenInNew,
                onClick = { onOpenUrl(release.pageUrl) },
            )
        }
    }
}

private fun formatClock(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
