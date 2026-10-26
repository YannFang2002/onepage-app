package com.onepage.companion.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.onepage.companion.core.model.DeviceStatus
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.ui.screens.files.FilesScreen
import com.onepage.companion.ui.screens.home.HomeScreen
import com.onepage.companion.ui.screens.more.MoreScreen
import com.onepage.companion.ui.screens.settings.SettingsScreen
import com.onepage.companion.ui.theme.onePageColors

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("设备", Icons.Rounded.PhoneAndroid),
    Tab("设置", Icons.Rounded.Tune),
    Tab("文件", Icons.Rounded.Folder),
    Tab("更多", Icons.Rounded.GridView),
)

@Composable
fun MainShell(
    api: DeviceApi,
    state: SessionUiState,
    updates: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onRename: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState > initialState
                val offset = if (forward) 1 else -1
                (slideInHorizontally(tween(260)) { it / 12 * offset } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it / 12 * offset } + fadeOut(tween(160)))
            },
            label = "tab",
        ) { index ->
            when (index) {
                0 -> HomeScreen(
                    api = api,
                    state = state,
                    onRefresh = onRefresh,
                    onDisconnect = onDisconnect,
                    onRename = onRename,
                    onOpenTab = { tab = it },
                )
                1 -> SettingsScreen(api = api)
                2 -> FilesScreen(api = api)
                else -> MoreScreen(
                    api = api,
                    state = state,
                    updates = updates,
                    onCheckUpdate = onCheckUpdate,
                    onDownloadUpdate = onDownloadUpdate,
                    onDisconnect = onDisconnect,
                )
            }
        }

        BottomBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = onePageColors
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(c.card)
                .border(1.dp, c.hairline, RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = selected == index
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (active) c.accentSoft else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) c.accent else c.ink3,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) c.accent else c.ink3,
                    )
                }
            }
        }
    }
}

/** Shared page header used by tab screens. */
@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = onePageColors
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = c.ink3)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.ink)
        }
        if (trailing != null) trailing()
    }
}

fun statusSubtitle(status: DeviceStatus?): String = when {
    status == null -> "未连接"
    status.isAp -> "热点模式"
    else -> "局域网 · ${status.ip}"
}
