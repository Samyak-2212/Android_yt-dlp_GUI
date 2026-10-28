package com.ytdlp.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytdlp.android.data.model.VideoQuality
import com.ytdlp.android.data.preferences.PreferencesManager
import com.ytdlp.android.engine.YtDlpEngine
import com.ytdlp.android.updater.AppUpdateInfo
import com.ytdlp.android.updater.AppUpdater
import com.ytdlp.android.updater.BinaryUpdater
import com.ytdlp.android.updater.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed class UpdateCheckState<out T> {
    object Idle : UpdateCheckState<Nothing>()
    object Checking : UpdateCheckState<Nothing>()
    data class Success<T>(val data: T) : UpdateCheckState<T>()
    data class Error(val message: String) : UpdateCheckState<Nothing>()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val binaryUpdater = BinaryUpdater.getInstance(application)
    private val appUpdater = AppUpdater.getInstance(application)
    private val engine = YtDlpEngine.getInstance(application)

    val themeMode: StateFlow<String> = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val defaultQuality: StateFlow<VideoQuality> = preferencesManager.defaultQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VideoQuality.P720)

    val maxConcurrentDownloads: StateFlow<Int> = preferencesManager.maxConcurrentDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val isSetupComplete: StateFlow<Boolean> = preferencesManager.isSetupComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _ytDlpUpdateState = MutableStateFlow<UpdateCheckState<UpdateInfo>>(UpdateCheckState.Idle)
    val ytDlpUpdateState: StateFlow<UpdateCheckState<UpdateInfo>> = _ytDlpUpdateState.asStateFlow()

    private val _appUpdateState = MutableStateFlow<UpdateCheckState<AppUpdateInfo>>(UpdateCheckState.Idle)
    val appUpdateState: StateFlow<UpdateCheckState<AppUpdateInfo>> = _appUpdateState.asStateFlow()

    private val _isDownloadingAppUpdate = MutableStateFlow(false)
    val isDownloadingAppUpdate: StateFlow<Boolean> = _isDownloadingAppUpdate.asStateFlow()

    private val _appDownloadProgress = MutableStateFlow(0f)
    val appDownloadProgress: StateFlow<Float> = _appDownloadProgress.asStateFlow()

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun setDefaultQuality(quality: VideoQuality) {
        viewModelScope.launch {
            preferencesManager.setDefaultQuality(quality)
        }
    }

    fun setMaxConcurrentDownloads(max: Int) {
        viewModelScope.launch {
            preferencesManager.setMaxConcurrentDownloads(max)
        }
    }

    fun markSetupComplete() {
        viewModelScope.launch {
            preferencesManager.setSetupComplete(true)
        }
    }

    fun getYtDlpCurrentVersion(): String {
        return engine.getYtDlpVersion()
    }

    fun isFfmpegReady(): Boolean {
        return binaryUpdater.isFfmpegReady()
    }

    fun getFfmpegVersion(): String {
        return com.ytdlp.ffmpeg.FFmpegBridge.getFFmpegVersion()
    }

    fun checkForYtDlpUpdate() {
        viewModelScope.launch {
            _ytDlpUpdateState.value = UpdateCheckState.Checking
            val current = getYtDlpCurrentVersion()
            val result = binaryUpdater.checkYtDlpUpdate(current)
            result.onSuccess { info ->
                _ytDlpUpdateState.value = UpdateCheckState.Success(info)
            }.onFailure { err ->
                _ytDlpUpdateState.value = UpdateCheckState.Error(err.message ?: "Failed to check for updates")
            }
        }
    }

    fun checkForAppUpdate() {
        viewModelScope.launch {
            _appUpdateState.value = UpdateCheckState.Checking
            val result: Result<AppUpdateInfo> = appUpdater.checkForUpdate()
            result.onSuccess { info: AppUpdateInfo ->
                _appUpdateState.value = UpdateCheckState.Success(info)
            }.onFailure { err ->
                _appUpdateState.value = UpdateCheckState.Error(err.message ?: "Failed to check for updates")
            }
        }
    }

    fun downloadAndInstallAppUpdate(apkUrl: String) {
        viewModelScope.launch {
            _isDownloadingAppUpdate.value = true
            _appDownloadProgress.value = 0f
            val result: Result<File> = appUpdater.downloadApk(apkUrl) { progress: Float ->
                _appDownloadProgress.value = progress
            }
            _isDownloadingAppUpdate.value = false
            result.onSuccess { file: File ->
                appUpdater.installApk(file)
            }
        }
    }
}
