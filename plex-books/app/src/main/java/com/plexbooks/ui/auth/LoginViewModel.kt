package com.plexbooks.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plexbooks.data.api.model.PlexResource
import com.plexbooks.data.prefs.PlexPreferences
import com.plexbooks.data.repository.PlexAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class WaitingForBrowser(val authUrl: String) : LoginUiState
    object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

sealed interface ServerSelectState {
    object Loading : ServerSelectState
    data class Servers(val list: List<PlexResource>) : ServerSelectState
    data class Error(val message: String) : ServerSelectState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepo: PlexAuthRepository,
    private val prefs: PlexPreferences
) : ViewModel() {

    val uiState: StateFlow<LoginUiState> get() = _uiState
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)

    fun startLogin() {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            runCatching {
                val pin = authRepo.createPin()
                val clientId = prefs.ensureClientId()
                val url = authRepo.buildAuthUrl(clientId, pin.code)
                _uiState.value = LoginUiState.WaitingForBrowser(url)
                val token = authRepo.pollForToken(pin.id, pin.code)
                if (token != null) _uiState.value = LoginUiState.Success
                else _uiState.value = LoginUiState.Error("Sign-in timed out. Please try again.")
            }.onFailure { e ->
                _uiState.value = LoginUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reset() { _uiState.value = LoginUiState.Idle }
}

@HiltViewModel
class ServerSelectViewModel @Inject constructor(
    private val authRepo: PlexAuthRepository
) : ViewModel() {

    val state: StateFlow<ServerSelectState> get() = _state
    private val _state = MutableStateFlow<ServerSelectState>(ServerSelectState.Loading)

    init { loadServers() }

    private fun loadServers() {
        viewModelScope.launch {
            _state.value = ServerSelectState.Loading
            runCatching { authRepo.getServers() }
                .onSuccess { servers ->
                    if (servers.size == 1) {
                        authRepo.selectServer(servers.first())
                        _state.value = ServerSelectState.Servers(servers)
                    } else {
                        _state.value = ServerSelectState.Servers(servers)
                    }
                }
                .onFailure { _state.value = ServerSelectState.Error(it.message ?: "Failed to load servers") }
        }
    }

    fun selectServer(resource: PlexResource, onDone: () -> Unit) {
        viewModelScope.launch {
            authRepo.selectServer(resource)
            onDone()
        }
    }
}
