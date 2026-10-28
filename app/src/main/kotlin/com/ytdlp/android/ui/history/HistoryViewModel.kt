package com.ytdlp.android.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytdlp.android.data.model.DownloadTask
import com.ytdlp.android.data.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.getInstance(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val historyTasks: StateFlow<List<DownloadTask>> = combine(
        repository.historyTasks,
        _searchQuery
    ) { tasks, query ->
        if (query.isBlank()) {
            tasks
        } else {
            tasks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.url.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteTask(taskId: String) {
        repository.deleteHistoryTask(taskId)
    }

    fun clearAllHistory() {
        repository.clearHistory()
    }
}
