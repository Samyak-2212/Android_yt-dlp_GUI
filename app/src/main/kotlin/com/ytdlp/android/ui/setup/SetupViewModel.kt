package com.ytdlp.android.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytdlp.android.data.preferences.PreferencesManager
import com.ytdlp.android.updater.BinaryUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SetupUiState {
    object Idle : SetupUiState()
    data class Downloading(val progress: Float) : SetupUiState()
    object Ready : SetupUiState()
    data class Error(val message: String) : SetupUiState()
}

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val binaryUpdater = BinaryUpdater.getInstance(application)
    private val preferencesManager = PreferencesManager(application)

    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        checkStatus()
    }

    fun checkStatus() {
        if (binaryUpdater.isFfmpegReady()) {
            _uiState.value = SetupUiState.Ready
        } else {
            _uiState.value = SetupUiState.Idle
        }
    }

    fun startDownload() {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Downloading(0f)
            val result = binaryUpdater.downloadFfmpeg { progress ->
                _uiState.value = SetupUiState.Downloading(progress)
            }
            result.onSuccess {
                preferencesManager.setSetupComplete(true)
                _uiState.value = SetupUiState.Ready
            }.onFailure { err ->
                _uiState.value = SetupUiState.Error(err.message ?: "Failed to download FFmpeg")
            }
        }
    }

    fun skipSetup() {
        viewModelScope.launch {
            preferencesManager.setSetupComplete(true)
            _uiState.value = SetupUiState.Ready
        }
    }
}
