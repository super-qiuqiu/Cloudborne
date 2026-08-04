package com.cloudborne.android.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface SyncState {
    data object Idle : SyncState
    data object Loading : SyncState
    data class Error(val message: String) : SyncState
}

data class MainUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val selectedNode: ProxyNode? = null,
    val syncState: SyncState = SyncState.Idle,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val connectionError: String? = null,
)

enum class ConnectionState { Disconnected, Connecting, Connected, Failed }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SubscriptionRepository(application)
    private val preferences = PreferencesStore(application)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val subscriptions = repository.load()
            val selectedId = preferences.selectedNodeId()
            val selected = subscriptions.flatMap { it.nodes }.firstOrNull { it.id == selectedId }
            _state.value = _state.value.copy(subscriptions = subscriptions, selectedNode = selected)
        }
    }

    fun addSubscription(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) return
        val subscription = Subscription(UUID.randomUUID().toString(), name.trim(), url.trim())
        viewModelScope.launch {
            repository.save(subscription)
            _state.value = _state.value.copy(subscriptions = repository.load())
            refresh(subscription.id)
        }
    }

    fun refresh(id: String) {
        val subscription = _state.value.subscriptions.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(syncState = SyncState.Loading)
            runCatching { repository.refresh(subscription) }
                .onSuccess { refreshed ->
                    val updated = _state.value.subscriptions.map { if (it.id == id) refreshed else it }
                    _state.value = _state.value.copy(subscriptions = updated, syncState = SyncState.Idle)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(syncState = SyncState.Error(error.message ?: "订阅更新失败"))
                }
        }
    }

    fun selectNode(node: ProxyNode) {
        _state.value = _state.value.copy(selectedNode = node)
        viewModelScope.launch { preferences.setSelectedNodeId(node.id) }
    }

    fun connect() {
        val node = _state.value.selectedNode ?: return
        _state.value = _state.value.copy(connectionState = ConnectionState.Connecting, connectionError = null)
        viewModelScope.launch {
            val result = LocalProxyController.start(getApplication(), node)
            _state.value = _state.value.copy(
                connectionState = if (result.isSuccess) ConnectionState.Connected else ConnectionState.Failed,
                connectionError = result.exceptionOrNull()?.message,
            )
        }
    }

    fun disconnect() {
        LocalProxyController.stop(getApplication())
        _state.value = _state.value.copy(connectionState = ConnectionState.Disconnected, connectionError = null)
    }
}
