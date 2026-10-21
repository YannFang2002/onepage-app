package com.onepage.companion.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.onepage.companion.core.model.DeviceStatus
import com.onepage.companion.core.model.DiscoveredDevice
import com.onepage.companion.core.model.FileEntry
import com.onepage.companion.core.model.SavedDevice
import com.onepage.companion.core.model.SettingGroup
import com.onepage.companion.core.model.SettingItem
import com.onepage.companion.core.model.SettingType
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.ui.Phase
import com.onepage.companion.ui.SessionUiState
import com.onepage.companion.ui.screens.connect.ConnectScreen
import com.onepage.companion.ui.screens.files.FilesContent
import com.onepage.companion.ui.screens.files.FilesUiState
import com.onepage.companion.ui.screens.files.UploadState
import com.onepage.companion.ui.screens.files.UploadTask
import com.onepage.companion.ui.screens.home.HomeScreen
import com.onepage.companion.ui.screens.more.AboutScreen
import com.onepage.companion.ui.screens.more.DeviceInfoScreen
import com.onepage.companion.ui.screens.settings.SettingsContent
import com.onepage.companion.ui.screens.settings.SettingsUiState
import com.onepage.companion.ui.theme.OnePageTheme
import okhttp3.OkHttpClient
import java.io.IOException

private const val PHONE = "spec:width=412dp,height=915dp,dpi=420"

private val connectedState = SessionUiState(
    phase = Phase.Connected,
    device = SavedDevice(
        id = "192.168.1.42:80",
        name = "书房的 OnePage",
        host = "192.168.1.42",
        port = 80,
        wsPort = 81,
        lastSeen = System.currentTimeMillis(),
    ),
    status = DeviceStatus(
        version = "onepage-0.1.1",
        ip = "192.168.1.42",
        mode = "STA",
        rssi = -47,
        freeHeap = 132_456,
        uptime = 7_920,
        device = "X4",
    ),
    saved = emptyList(),
)

private val idleState = SessionUiState(
    phase = Phase.Idle,
    saved = listOf(
        SavedDevice("192.168.1.42:80", "书房的 OnePage", "192.168.1.42", 80, 81, 3),
        SavedDevice("192.168.4.1:80", "随身热点", "192.168.4.1", 80, 81, 1),
    ),
)

private val foundState = idleState.copy(
    discovered = listOf(
        DiscoveredDevice("192.168.1.42", 81, "crosspoint"),
        DiscoveredDevice("192.168.1.77", 81, "bedroom"),
    ),
    scanning = false,
)

private val api = DeviceApi(OkHttpClient(), "127.0.0.1")

@Composable
private fun Frame(dark: Boolean = false, content: @Composable () -> Unit) {
    OnePageTheme(darkTheme = dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) { content() }
    }
}

@Preview(name = "connect-idle", device = PHONE, showBackground = true)
@Composable
fun ConnectIdlePreview() = Frame {
    ConnectScreen(
        state = idleState,
        onScan = {},
        onScanQr = {},
        onConnect = { _, _, _ -> },
        onConnectSaved = {},
        onForget = {},
        onRenameSaved = { _, _ -> },
    )
}

@Preview(name = "connect-found", device = PHONE, showBackground = true)
@Composable
fun ConnectFoundPreview() = Frame {
    ConnectScreen(
        state = foundState,
        onScan = {},
        onScanQr = {},
        onConnect = { _, _, _ -> },
        onConnectSaved = {},
        onForget = {},
        onRenameSaved = { _, _ -> },
    )
}

@Preview(name = "connect-dark", device = PHONE, showBackground = true)
@Composable
fun ConnectDarkPreview() = Frame(dark = true) {
    ConnectScreen(
        state = foundState,
        onScan = {},
        onScanQr = {},
        onConnect = { _, _, _ -> },
        onConnectSaved = {},
        onForget = {},
        onRenameSaved = { _, _ -> },
    )
}

@Preview(name = "home", device = PHONE, showBackground = true)
@Composable
fun HomePreview() = Frame {
    HomeScreen(
        api = api,
        state = connectedState,
        onRefresh = {},
        onDisconnect = {},
        onRename = {},
        onOpenTab = {},
    )
}

@Preview(name = "home-dark", device = PHONE, showBackground = true)
@Composable
fun HomeDarkPreview() = Frame(dark = true) {
    HomeScreen(
        api = api,
        state = connectedState,
        onRefresh = {},
        onDisconnect = {},
        onRename = {},
        onOpenTab = {},
    )
}

@Preview(name = "device-info", device = PHONE, showBackground = true)
@Composable
fun DeviceInfoPreview() = Frame {
    DeviceInfoScreen(state = connectedState, onBack = {})
}

@Preview(name = "about", device = PHONE, showBackground = true)
@Composable
fun AboutPreview() = Frame {
    AboutScreen(onBack = {})
}

private val settingsState = SettingsUiState(
    loading = false,
    groups = listOf(
        SettingGroup(
            category = "阅读",
            items = listOf(
                SettingItem(
                    key = "fontFamily", name = "字体", category = "阅读", type = SettingType.ENUM,
                    intValue = 1, options = listOf("Noto Serif", "Noto Sans", "思源宋体"),
                ),
                SettingItem(
                    key = "screenMargin", name = "页边距", category = "阅读", type = SettingType.VALUE,
                    intValue = 15, min = 5, max = 40, step = 5,
                ),
                SettingItem(
                    key = "hyphenationEnabled", name = "连字符断词", category = "阅读", type = SettingType.TOGGLE,
                    intValue = 0,
                ),
                SettingItem(
                    key = "koreaderUsername", name = "KOReader 用户名", category = "阅读",
                    type = SettingType.STRING, stringValue = "",
                ),
            ),
        ),
        SettingGroup(
            category = "显示",
            items = listOf(
                SettingItem(
                    key = "refreshFrequency", name = "刷新频率", category = "显示", type = SettingType.ENUM,
                    intValue = 2, options = listOf("1 页", "5 页", "10 页", "15 页", "30 页"),
                ),
                SettingItem(
                    key = "fadingFix", name = "残影修复", category = "显示", type = SettingType.TOGGLE,
                    intValue = 1,
                ),
            ),
        ),
    ),
)

@Preview(name = "settings", device = PHONE, showBackground = true)
@Composable
fun SettingsPreview() = Frame {
    SettingsContent(
        state = settingsState,
        onToggle = { _, _ -> },
        onSelect = { _, _ -> },
        onRange = { _, _ -> },
        onSetText = { _, _ -> },
        onDismissError = {},
    )
}

@Preview(name = "settings-dark", device = PHONE, showBackground = true)
@Composable
fun SettingsDarkPreview() = Frame(dark = true) {
    SettingsContent(
        state = settingsState,
        onToggle = { _, _ -> },
        onSelect = { _, _ -> },
        onRange = { _, _ -> },
        onSetText = { _, _ -> },
        onDismissError = {},
    )
}

private val filesState = FilesUiState(
    path = "/Books",
    loading = false,
    entries = listOf(
        FileEntry("科幻", isDirectory = true),
        FileEntry("三体.epub", 4_812_344, isEpub = true),
        FileEntry("The Little Prince.epub", 1_204_112, isEpub = true),
        FileEntry("夜航船.txt", 892_331),
        FileEntry("NotoSerifSC_16.cpfont", 310_233),
    ),
    uploads = listOf(
        UploadTask("1", "沙丘.epub", 8_300_000, 4_900_000, UploadState.Uploading),
        UploadTask("2", "cover.bmp", 120_000, 120_000, UploadState.Done),
    ),
)

@Preview(name = "files", device = PHONE, showBackground = true)
@Composable
fun FilesPreview() = Frame {
    FilesContent(
        state = filesState,
        onOpen = {},
        onUp = {},
        onRefresh = {},
        onMkdir = {},
        onRename = { _, _ -> },
        onMove = { _, _ -> },
        onDelete = {},
        onUpload = {},
        onDownload = { Result.failure(IOException("preview")) },
        onClearUploads = {},
        onDismissError = {},
    )
}

@Preview(name = "files-dark", device = PHONE, showBackground = true)
@Composable
fun FilesDarkPreview() = Frame(dark = true) {
    FilesContent(
        state = filesState,
        onOpen = {},
        onUp = {},
        onRefresh = {},
        onMkdir = {},
        onRename = { _, _ -> },
        onMove = { _, _ -> },
        onDelete = {},
        onUpload = {},
        onDownload = { Result.failure(IOException("preview")) },
        onClearUploads = {},
        onDismissError = {},
    )
}
