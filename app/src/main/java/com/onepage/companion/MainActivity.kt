package com.onepage.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.core.update.notesPreview
import com.onepage.companion.ui.MainShell
import com.onepage.companion.ui.Phase
import com.onepage.companion.ui.SessionViewModel
import com.onepage.companion.ui.UpdateUiState
import com.onepage.companion.ui.UpdateViewModel
import com.onepage.companion.ui.components.ConfirmDialog
import com.onepage.companion.ui.components.installApk
import com.onepage.companion.ui.screens.connect.ConnectScreen
import com.onepage.companion.ui.screens.more.AboutScreen
import com.onepage.companion.ui.screens.more.UpdateScreen
import com.onepage.companion.ui.screens.scan.QrScannerScreen
import com.onepage.companion.ui.theme.OnePageTheme
import com.onepage.companion.ui.theme.onePageColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OnePageTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val session: SessionViewModel = viewModel()
    val updater: UpdateViewModel = viewModel()
    val state by session.state.collectAsStateWithLifecycle()
    val updateState by updater.state.collectAsStateWithLifecycle()
    val api = session.api
    val connected = state.phase == Phase.Connected && api != null
    var scanning by rememberSaveable { mutableStateOf(false) }
    var updatePromptDismissed by rememberSaveable { mutableStateOf(false) }
    var infoPage by rememberSaveable { mutableStateOf(InfoPage.None) }
    val context = LocalContext.current

    // Check for a new APK as soon as the app opens; the result pops a dialog
    // below but never blocks connecting to a reader.
    LaunchedEffect(Unit) { updater.autoCheck() }

    // Reaching a reader closes the 关于/更新 overlay opened from the connect screen.
    LaunchedEffect(connected) { if (connected) infoPage = InfoPage.None }

    BackHandler(enabled = infoPage != InfoPage.None) {
        infoPage = if (infoPage == InfoPage.Update) InfoPage.About else InfoPage.None
    }

    // The target carries its own DeviceApi: AnimatedContent keeps the outgoing
    // shell composed for the exit animation, and a plain `api!!` would read the
    // already-cleared field (NPE) the moment the user disconnects.
    val target = ShellTarget(connected = connected, api = if (connected) api else null)
    AnimatedContent(
        targetState = target,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
        label = "root",
    ) { shell ->
        val shellApi = shell.api
        if (shell.connected && shellApi != null) {
            MainShell(
                api = shellApi,
                state = state,
                updates = updateState,
                onCheckUpdate = updater::check,
                onDownloadUpdate = updater::download,
                onDisconnect = session::disconnect,
                onRefresh = session::refreshStatus,
                onRename = { name ->
                    state.device?.let { session.renameSaved(it, name) }
                },
            )
        } else {
            ConnectScreen(
                state = state,
                onScan = session::scan,
                onScanQr = { scanning = true },
                onConnect = { host, port, wsPort -> session.connect(host, port, wsPort) },
                onConnectSaved = session::connectTo,
                onForget = session::forget,
                onRenameSaved = session::renameSaved,
                onOpenAbout = { infoPage = InfoPage.About },
            )
        }
    }

    if (infoPage != InfoPage.None) {
        InfoOverlay(
            page = infoPage,
            updateState = updateState,
            onCheckUpdate = updater::check,
            onDownloadUpdate = updater::download,
            onNavigate = { infoPage = it },
        )
    }

    // A downloaded APK is handed to the system installer as soon as it exists,
    // so updating never leaves the app.
    val downloadedApk = updateState.download.apk
    LaunchedEffect(downloadedApk) {
        if (downloadedApk != null) installApk(context, downloadedApk)
    }

    if (scanning) {
        QrScannerScreen(
            onDetected = { raw ->
                scanning = false
                session.connectViaQr(raw)
            },
            onClose = { scanning = false },
        )
    }

    if (state.phase == Phase.Connecting) {
        ConnectingOverlay(label = state.connectingLabel)
    }

    val release = updateState.release
    if (updateState.hasUpdate && release != null && !updatePromptDismissed && state.phase != Phase.Connecting) {
        ConfirmDialog(
            title = "发现新版本 ${release.tag}",
            message = buildString {
                append("当前 v${updateState.currentVersion}，${updateState.sourceLabel} 上已有 ${release.tag}。")
                if (release.notes.isNotBlank()) {
                    append("\n\n")
                    append(notesPreview(release.notes, limit = 240))
                }
            },
            confirmText = "下载并安装",
            dismissText = "稍后",
            onConfirm = {
                updatePromptDismissed = true
                infoPage = InfoPage.Update
                updater.download()
            },
            onDismiss = { updatePromptDismissed = true },
        )
    }
}

/** What the root cross-fade renders; carrying [api] keeps the exit frame safe. */
private data class ShellTarget(val connected: Boolean, val api: DeviceApi?)

private enum class InfoPage { None, About, Update }

/** 关于/软件更新, reachable before any reader is connected. */
@Composable
private fun InfoOverlay(
    page: InfoPage,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onNavigate: (InfoPage) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(200)) { it / 6 } + fadeOut(tween(160)))
            },
            label = "info",
        ) { target ->
            when (target) {
                InfoPage.About -> AboutScreen(
                    onBack = { onNavigate(InfoPage.None) },
                    onOpenUpdate = { onNavigate(InfoPage.Update) },
                )
                InfoPage.Update -> UpdateScreen(
                    state = updateState,
                    onCheck = onCheckUpdate,
                    onDownload = onDownloadUpdate,
                    onBack = { onNavigate(InfoPage.About) },
                )
                InfoPage.None -> Unit
            }
        }
    }
}

@Composable
private fun ConnectingOverlay(label: String?) {
    val c = onePageColors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(c.card)
                .padding(horizontal = 30.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.4.dp,
                color = c.accent,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                label ?: "正在连接设备…",
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "首次加入热点时，系统会弹窗确认",
                style = MaterialTheme.typography.bodySmall,
                color = c.ink3,
            )
        }
    }
}
