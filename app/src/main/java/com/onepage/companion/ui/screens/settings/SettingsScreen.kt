package com.onepage.companion.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onepage.companion.core.model.SettingGroup
import com.onepage.companion.core.model.SettingItem
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.ui.PageHeader
import com.onepage.companion.ui.components.EmptyState
import com.onepage.companion.ui.components.Hairline
import com.onepage.companion.ui.components.LoadingRow
import com.onepage.companion.ui.components.OnePageCard
import com.onepage.companion.ui.components.Pill
import com.onepage.companion.ui.components.PillTone
import com.onepage.companion.ui.components.PromptDialog
import com.onepage.companion.ui.theme.onePageColors
import androidx.compose.material.icons.rounded.Tune
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(api: DeviceApi) {
    val vm: SettingsViewModel = viewModel(key = "settings:${api.baseUrl}", factory = SettingsViewModel.factory(api))
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onToggle = vm::toggle,
        onSelect = vm::select,
        onRange = vm::setRange,
        onSetText = vm::setText,
        onDismissError = vm::dismissError,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onToggle: (SettingItem, Boolean) -> Unit,
    onSelect: (SettingItem, Int) -> Unit,
    onRange: (SettingItem, Int) -> Unit,
    onSetText: (SettingItem, String) -> Unit,
    onDismissError: () -> Unit,
) {
    val c = onePageColors
    val listState = rememberLazyListState()
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var textEdit by remember { mutableStateOf<SettingItem?>(null) }

    val groups = state.groups
    val categories = groups.map { it.category }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 22.dp, end = 22.dp, top = 8.dp, bottom = 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                PageHeader(
                    eyebrow = "参数",
                    title = "设置",
                    trailing = {
                        if (state.pendingKeys.isNotEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.8.dp,
                                color = c.accent,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = c.accent, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("已同步", style = MaterialTheme.typography.labelMedium, color = c.accent)
                            }
                        }
                    },
                )
            }

            if (state.loading) {
                item { LoadingRow("正在读取设备设置…") }
            } else if (state.groups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Tune,
                        title = "没有读到设置项",
                        body = "设备可能尚未连接，或该固件版本不提供设置接口。",
                    )
                }
            } else {
                item {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(null) + categories) { cat ->
                            val selected = activeCategory == cat
                            val label = cat ?: "全部"
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) c.ink else c.card)
                                    .then(
                                        if (selected) Modifier else Modifier
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .clickable { activeCategory = cat }
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) c.card else c.ink2,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                val visible = groups.filter { activeCategory == null || it.category == activeCategory }
                visible.forEach { group ->
                    item(key = "h-${group.category}") {
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                group.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = c.ink3,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${group.items.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.ink3,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    item(key = "g-${group.category}") {
                        OnePageCard(padding = 0.dp) {
                            SettingGroupCard(
                                group = group,
                                pending = state.pendingKeys,
                                onToggle = onToggle,
                                onSelect = onSelect,
                                onRange = onRange,
                                onText = { textEdit = it },
                            )
                        }
                    }
                }
            }
        }

        // save / error banners float above the bottom bar
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(tween(150)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically { it / 2 },
            ) {
                val message = state.error ?: ""
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.dangerSoft)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .clickable { onDismissError() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = c.danger)
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedVisibility(
                visible = state.justSaved,
                enter = fadeIn(tween(150)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically { it / 2 },
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(c.ink)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = c.card, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("已保存到设备", style = MaterialTheme.typography.labelMedium, color = c.card)
                }
            }
        }
    }

    textEdit?.let { item ->
        PromptDialog(
            title = item.name,
            label = item.name,
            initial = item.stringValue,
            confirmText = "保存",
            onConfirm = { value ->
                textEdit = null
                onSetText(item, value)
            },
            onDismiss = { textEdit = null },
        )
    }
}

@Composable
private fun SettingGroupCard(
    group: SettingGroup,
    pending: Set<String>,
    onToggle: (SettingItem, Boolean) -> Unit,
    onSelect: (SettingItem, Int) -> Unit,
    onRange: (SettingItem, Int) -> Unit,
    onText: (SettingItem) -> Unit,
) {
    group.items.forEachIndexed { index, item ->
        SettingRow(
            item = item,
            pending = item.key in pending,
            onToggle = { onToggle(item, it) },
            onSelect = { onSelect(item, it) },
            onRange = { onRange(item, it) },
            onText = { onText(item) },
        )
        if (index != group.items.lastIndex) Hairline(inset = 18.dp)
    }
}

@Composable
private fun SettingRow(
    item: SettingItem,
    pending: Boolean,
    onToggle: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onRange: (Int) -> Unit,
    onText: () -> Unit,
) {
    val c = onePageColors
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            when {
                item.isToggle -> Switch(
                    checked = item.intValue != 0,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = c.accent,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = c.card,
                        uncheckedTrackColor = c.hairline,
                        uncheckedBorderColor = c.ink3.copy(alpha = 0.25f),
                    ),
                )
                item.isText -> Text(
                    item.valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.cardSunken)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { onText() },
                )
                item.isRange -> Text(
                    item.valueLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                )
                item.isEnum && item.options.size > 4 -> Pill(item.valueLabel, PillTone.Neutral)
                else -> if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.6.dp,
                        color = c.ink3,
                    )
                } else Unit
            }
        }

        if (item.isEnum && item.options.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SegmentedOptions(item = item, onSelect = onSelect)
        }
        if (item.isRange) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.min}", style = MaterialTheme.typography.labelSmall, color = c.ink3)
                Slider(
                    value = item.intValue.toFloat(),
                    onValueChange = { onRange(it.roundToInt()) },
                    valueRange = item.min.toFloat()..item.max.coerceAtLeast(item.min + 1).toFloat(),
                    steps = if (item.step <= 1) 0 else ((item.max - item.min) / item.step) - 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = c.accent,
                        activeTrackColor = c.accent,
                        inactiveTrackColor = c.hairline,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                )
                Text("${item.max}", style = MaterialTheme.typography.labelSmall, color = c.ink3)
            }
        }
    }
}

@Composable
private fun SegmentedOptions(item: SettingItem, onSelect: (Int) -> Unit) {
    val c = onePageColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.cardSunken)
            .padding(3.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item.options.forEachIndexed { index, option ->
            val selected = item.intValue == index
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) c.card else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) c.ink else c.ink2,
                    maxLines = 1,
                )
            }
        }
    }
}
