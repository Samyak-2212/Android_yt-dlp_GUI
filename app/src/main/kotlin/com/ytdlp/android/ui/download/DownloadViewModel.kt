package com.ytdlp.android.ui.download

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytdlp.android.data.model.DownloadStatus
import com.ytdlp.android.data.model.DownloadTask
import com.ytdlp.android.data.model.SubtitleConfig
import com.ytdlp.android.data.model.VideoInfo
import com.ytdlp.android.data.model.VideoQuality
import com.ytdlp.android.data.preferences.PreferencesManager
import com.ytdlp.android.data.repository.DownloadRepository
import com.ytdlp.android.engine.YtDlpEngine
import com.ytdlp.android.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = YtDlpEngine.getInstance(application)
    private val repository = DownloadRepository.getInstance(application)
    private val preferencesManager = PreferencesManager(application)

    val activeTasks: StateFlow<List<DownloadTask>> = repository.activeTasks

    val recentPaths: StateFlow<Set<String>> = preferencesManager.recentPaths
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _selectedQuality = MutableStateFlow(VideoQuality.P720)
    val selectedQuality: StateFlow<VideoQuality> = _selectedQuality.asStateFlow()

    private val _customFormat = MutableStateFlow("")
    val customFormat: StateFlow<String> = _customFormat.asStateFlow()

    private val _subtitleConfig = MutableStateFlow(SubtitleConfig())
    val subtitleConfig: StateFlow<SubtitleConfig> = _subtitleConfig.asStateFlow()

    private val _downloadLyrics = MutableStateFlow(false)
    val downloadLyrics: StateFlow<Boolean> = _downloadLyrics.asStateFlow()

    private val _outputDir = MutableStateFlow("")
    val outputDir: StateFlow<String> = _outputDir.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            preferencesManager.defaultQuality.collect { defaultQ ->
                _selectedQuality.value = defaultQ
            }
        }
        viewModelScope.launch {
            preferencesManager.defaultCustomFormat.collect { defaultFormat ->
                if (_customFormat.value.isBlank()) {
                    _customFormat.value = defaultFormat
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.defaultOutputDir.collect { dir ->
                _outputDir.value = dir
            }
        }
        viewModelScope.launch {
            preferencesManager.defaultEmbedSubtitles.collect { embed ->
                _subtitleConfig.value = _subtitleConfig.value.copy(embedInVideo = embed)
            }
        }
    }

    private var extractJob: Job? = null

    fun onUrlChanged(newUrl: String) {
        _url.value = newUrl
        extractJob?.cancel()
        if (newUrl.isNotBlank() && (newUrl.startsWith("http://") || newUrl.startsWith("https://"))) {
            extractJob = viewModelScope.launch {
                delay(600)
                fetchVideoInfoInternal(newUrl)
            }
        } else {
            _isExtracting.value = false
            _videoInfo.value = null
        }
    }

    fun fetchVideoInfo(urlToFetch: String = _url.value) {
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            fetchVideoInfoInternal(urlToFetch)
        }
    }

    private suspend fun fetchVideoInfoInternal(urlToFetch: String) {
        if (urlToFetch.isBlank()) return
        _isExtracting.value = true
        val result = engine.extractInfo(urlToFetch.trim())
        _isExtracting.value = false
        result.onSuccess { info ->
            _videoInfo.value = info
        }.onFailure { err ->
            _snackbarEvent.emit("Could not fetch info: ${err.message}")
        }
    }

    fun onQualitySelected(quality: VideoQuality) {
        _selectedQuality.value = quality
    }

    fun onCustomFormatChanged(format: String) {
        _customFormat.value = format
    }

    fun onSubtitleConfigChanged(config: SubtitleConfig) {
        _subtitleConfig.value = config
    }

    fun onLyricsToggleChanged(enabled: Boolean) {
        _downloadLyrics.value = enabled
    }

    fun onPathSelected(path: String, uri: Uri?) {
        _outputDir.value = path
        viewModelScope.launch {
            preferencesManager.setDefaultOutputDir(path)
        }
    }

    fun startDownload() {
        val targetUrl = _url.value.trim()
        if (targetUrl.isBlank()) return

        val title = _videoInfo.value?.title ?: "Download"
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            url = targetUrl,
            title = title,
            quality = _selectedQuality.value,
            customFormat = _customFormat.value,
            subtitleConfig = _subtitleConfig.value,
            downloadLyrics = _downloadLyrics.value,
            outputDir = _outputDir.value,
            status = DownloadStatus.PENDING
        )

        repository.addTask(task)
        DownloadService.startDownload(getApplication(), task)

        // Clear URL input field for the next link
        _url.value = ""
        _videoInfo.value = null

        viewModelScope.launch {
            _snackbarEvent.emit("Download started for: $title")
        }
    }

    fun cancelTask(taskId: String) {
        DownloadService.cancelDownload(getApplication(), taskId)
    }

    fun checkClipboardForUrl() {
        try {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                if (clipText.isNotBlank() &&
                    (clipText.startsWith("http://") || clipText.startsWith("https://")) &&
                    clipText != _url.value
                ) {
                    viewModelScope.launch {
                        _snackbarEvent.emit("Found link on clipboard: $clipText")
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
