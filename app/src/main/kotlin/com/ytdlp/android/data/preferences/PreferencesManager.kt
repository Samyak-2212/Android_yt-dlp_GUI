package com.ytdlp.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ytdlp.android.data.model.DownloadTask
import com.ytdlp.android.data.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ytdlp_settings")

class PreferencesManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val KEY_DEFAULT_CUSTOM_FORMAT = stringPreferencesKey("default_custom_format")
        val KEY_DEFAULT_OUTPUT_DIR = stringPreferencesKey("default_output_dir")
        val KEY_RECENT_PATHS = stringSetPreferencesKey("recent_paths")
        val KEY_MAX_CONCURRENT = intPreferencesKey("max_concurrent_downloads")
        val KEY_DEFAULT_EMBED_SUBS = booleanPreferencesKey("default_embed_subs")
        val KEY_DEFAULT_SUB_LANG = stringPreferencesKey("default_sub_lang")
        val KEY_DEFAULT_AUTO_SUBS = booleanPreferencesKey("default_auto_subs")
        val KEY_DEFAULT_DOWNLOAD_LYRICS = booleanPreferencesKey("default_download_lyrics")
        val KEY_IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        val KEY_DOWNLOAD_HISTORY = stringPreferencesKey("download_history_json")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    val defaultQuality: Flow<VideoQuality> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_DEFAULT_QUALITY] ?: VideoQuality.P720.name
        try {
            VideoQuality.valueOf(name)
        } catch (_: Exception) {
            VideoQuality.P720
        }
    }

    suspend fun setDefaultQuality(quality: VideoQuality) {
        context.dataStore.edit { it[KEY_DEFAULT_QUALITY] = quality.name }
    }

    val defaultCustomFormat: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_CUSTOM_FORMAT] ?: ""
    }

    suspend fun setDefaultCustomFormat(format: String) {
        context.dataStore.edit { it[KEY_DEFAULT_CUSTOM_FORMAT] = format }
    }

    val defaultOutputDir: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_OUTPUT_DIR] ?: ""
    }

    suspend fun setDefaultOutputDir(path: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_OUTPUT_DIR] = path
            val currentRecent = prefs[KEY_RECENT_PATHS]?.toMutableSet() ?: mutableSetOf()
            if (path.isNotBlank()) {
                currentRecent.add(path)
                prefs[KEY_RECENT_PATHS] = currentRecent
            }
        }
    }

    val recentPaths: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_RECENT_PATHS] ?: emptySet()
    }

    val maxConcurrentDownloads: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAX_CONCURRENT] ?: 2
    }

    suspend fun setMaxConcurrentDownloads(max: Int) {
        context.dataStore.edit { it[KEY_MAX_CONCURRENT] = max.coerceIn(1, 5) }
    }

    val defaultEmbedSubtitles: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_EMBED_SUBS] ?: true
    }

    suspend fun setDefaultEmbedSubtitles(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEFAULT_EMBED_SUBS] = enabled }
    }

    val defaultSubtitleLang: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_SUB_LANG] ?: "en"
    }

    suspend fun setDefaultSubtitleLang(lang: String) {
        context.dataStore.edit { it[KEY_DEFAULT_SUB_LANG] = lang }
    }

    val defaultAutoSubtitles: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_AUTO_SUBS] ?: true
    }

    suspend fun setDefaultAutoSubtitles(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEFAULT_AUTO_SUBS] = enabled }
    }

    val defaultDownloadLyrics: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_DOWNLOAD_LYRICS] ?: false
    }

    suspend fun setDefaultDownloadLyrics(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEFAULT_DOWNLOAD_LYRICS] = enabled }
    }

    val isSetupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_SETUP_COMPLETE] ?: false
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_IS_SETUP_COMPLETE] = complete }
    }

    val downloadHistory: Flow<List<DownloadTask>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[KEY_DOWNLOAD_HISTORY]
        if (jsonStr.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString<List<DownloadTask>>(jsonStr)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveDownloadHistory(history: List<DownloadTask>) {
        context.dataStore.edit { prefs ->
            val jsonStr = json.encodeToString(history)
            prefs[KEY_DOWNLOAD_HISTORY] = jsonStr
        }
    }
}
