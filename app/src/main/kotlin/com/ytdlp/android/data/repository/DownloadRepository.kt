package com.ytdlp.android.data.repository

import android.content.Context
import com.ytdlp.android.data.model.DownloadStatus
import com.ytdlp.android.data.model.DownloadTask
import com.ytdlp.android.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DownloadRepository(
    private val context: Context,
    private val preferencesManager: PreferencesManager = PreferencesManager(context)
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeTasks: StateFlow<List<DownloadTask>> = _activeTasks.asStateFlow()

    private val _historyTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val historyTasks: StateFlow<List<DownloadTask>> = _historyTasks.asStateFlow()

    init {
        scope.launch {
            val saved = preferencesManager.downloadHistory.first()
            _historyTasks.value = saved
        }
    }

    fun addTask(task: DownloadTask) {
        val current = _activeTasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            current[index] = task
        } else {
            current.add(0, task)
        }
        _activeTasks.value = current
    }

    fun updateTask(task: DownloadTask) {
        val current = _activeTasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            current[index] = task
            _activeTasks.value = current
        }

        // If completed or failed or cancelled, also add or update in history
        if (task.status == DownloadStatus.COMPLETE ||
            task.status == DownloadStatus.ERROR ||
            task.status == DownloadStatus.CANCELLED
        ) {
            val history = _historyTasks.value.toMutableList()
            val histIndex = history.indexOfFirst { it.id == task.id }
            if (histIndex >= 0) {
                history[histIndex] = task
            } else {
                history.add(0, task)
            }
            _historyTasks.value = history
            scope.launch {
                preferencesManager.saveDownloadHistory(history)
            }
        }
    }

    fun removeActiveTask(taskId: String) {
        _activeTasks.value = _activeTasks.value.filterNot { it.id == taskId }
    }

    fun deleteHistoryTask(taskId: String) {
        val updated = _historyTasks.value.filterNot { it.id == taskId }
        _historyTasks.value = updated
        scope.launch {
            preferencesManager.saveDownloadHistory(updated)
        }
    }

    fun clearHistory() {
        _historyTasks.value = emptyList()
        scope.launch {
            preferencesManager.saveDownloadHistory(emptyList())
        }
    }

    fun getTaskById(taskId: String): DownloadTask? {
        return _activeTasks.value.find { it.id == taskId }
            ?: _historyTasks.value.find { it.id == taskId }
    }

    companion object {
        @Volatile
        private var instance: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository {
            return instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
