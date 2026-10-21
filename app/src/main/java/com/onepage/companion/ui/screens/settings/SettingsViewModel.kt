package com.onepage.companion.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onepage.companion.core.model.ApiException
import com.onepage.companion.core.model.SettingGroup
import com.onepage.companion.core.model.SettingItem
import com.onepage.companion.core.model.SettingType
import com.onepage.companion.core.net.DeviceApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class SettingsUiState(
    val loading: Boolean = true,
    val groups: List<SettingGroup> = emptyList(),
    val error: String? = null,
    val pendingKeys: Set<String> = emptySet(),
    val justSaved: Boolean = false,
)

class SettingsViewModel(private val api: DeviceApi) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Values as the device last confirmed them — used for rollback. */
    private val committed = mutableMapOf<String, SettingItem>()
    private val jobs = mutableMapOf<String, Job>()
    private var savedBannerJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { api.settings() }
                .onSuccess { items ->
                    committed.clear()
                    items.forEach { committed[it.key] = it }
                    _state.update {
                        it.copy(loading = false, groups = group(items), error = null)
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = describe(t)) }
                }
        }
    }

    fun toggle(item: SettingItem, on: Boolean) =
        updateLocal(item) { it.withInt(if (on) 1 else 0) }

    fun select(item: SettingItem, index: Int) =
        updateLocal(item) { it.withInt(index) }

    fun setRange(item: SettingItem, value: Int) =
        updateLocal(item) { it.withInt(value.coerceIn(it.min, it.max)) }

    fun setText(item: SettingItem, text: String) =
        updateLocal(item) { it.withString(text) }

    private fun updateLocal(item: SettingItem, transform: (SettingItem) -> SettingItem) {
        val updated = transform(item)
        // 1. optimistic local update
        _state.update { s ->
            s.copy(groups = s.groups.map { g ->
                g.copy(items = g.items.map { if (it.key == item.key) updated else it })
            })
        }
        // 2. debounce the write: dragging a slider should not spam the device
        jobs.remove(item.key)?.cancel()
        jobs[item.key] = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(pendingKeys = it.pendingKeys + item.key) }
            val payload: Map<String, Any> = when (updated.type) {
                SettingType.STRING -> mapOf(item.key to updated.stringValue)
                else -> mapOf(item.key to updated.intValue)
            }
            runCatching { api.updateSettings(payload) }
                .onSuccess {
                    committed[item.key] = updated
                    _state.update {
                        it.copy(pendingKeys = it.pendingKeys - item.key, justSaved = true)
                    }
                    banner()
                }
                .onFailure { t ->
                    val original = committed[item.key]
                    _state.update { s ->
                        s.copy(
                            pendingKeys = s.pendingKeys - item.key,
                            error = describe(t),
                            groups = if (original == null) s.groups else s.groups.map { g ->
                                g.copy(items = g.items.map { if (it.key == item.key) original else it })
                            },
                        )
                    }
                }
        }
    }

    private fun banner() {
        savedBannerJob?.cancel()
        savedBannerJob = viewModelScope.launch {
            delay(1600)
            _state.update { it.copy(justSaved = false) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun group(items: List<SettingItem>): List<SettingGroup> {
        val order = listOf("显示", "阅读", "控制", "系统")
        return items
            .groupBy { it.category.ifBlank { "其他" } }
            .toList()
            .sortedWith(compareBy({ order.indexOf(it.first).let { i -> if (i < 0) 99 else i } }, { it.first }))
            .map { (category, list) -> SettingGroup(category, list) }
    }

    private fun describe(t: Throwable): String = when (t) {
        is ApiException -> t.message ?: "设备返回 ${t.statusCode}"
        is IOException -> "网络错误：${t.message ?: "连接中断"}"
        else -> t.message ?: "设置保存失败"
    }

    companion object {
        fun factory(api: DeviceApi) = viewModelFactory {
            initializer { SettingsViewModel(api) }
        }
    }
}
