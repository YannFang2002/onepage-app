package com.onepage.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onepage.companion.ui.MainShell
import com.onepage.companion.ui.Phase
import com.onepage.companion.ui.SessionViewModel
import com.onepage.companion.ui.screens.connect.ConnectScreen
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
    val state by session.state.collectAsStateWithLifecycle()
    val api = session.api
    val connected = state.phase == Phase.Connected && api != null
    var scanning by rememberSaveable { mutableStateOf(false) }

    AnimatedContent(
        targetState = connected,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
        label = "root",
    ) { isConnected ->
        if (isConnected) {
            MainShell(
                api = api!!,
                state = state,
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
            )
        }
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
