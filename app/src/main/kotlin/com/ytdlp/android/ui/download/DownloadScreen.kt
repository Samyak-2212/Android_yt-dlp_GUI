package com.ytdlp.android.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ytdlp.android.ui.download.components.DownloadItemCard
import com.ytdlp.android.ui.download.components.LyricsToggle
import com.ytdlp.android.ui.download.components.QualityDropdown
import com.ytdlp.android.ui.download.components.SaveLocationPicker
import com.ytdlp.android.ui.download.components.SubtitleOptions
import com.ytdlp.android.ui.download.components.UrlInputBar

@Composable
fun DownloadScreen(
    initialUrl: String? = null,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = viewModel()
) {
    val url by viewModel.url.collectAsState()
    val videoInfo by viewModel.videoInfo.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val customFormat by viewModel.customFormat.collectAsState()
    val subtitleConfig by viewModel.subtitleConfig.collectAsState()
    val downloadLyrics by viewModel.downloadLyrics.collectAsState()
    val outputDir by viewModel.outputDir.collectAsState()
    val recentPaths by viewModel.recentPaths.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            viewModel.onUrlChanged(initialUrl)
        } else {
            viewModel.checkClipboardForUrl()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // URL Input
            item {
                UrlInputBar(
                    url = url,
                    onUrlChange = { viewModel.onUrlChanged(it) }
                )
            }

            // Video metadata preview card
            if (isExtracting) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("Fetching video details...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (videoInfo != null) {
                item {
                    val info = videoInfo!!
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = info.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Uploader: ${info.uploader}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (info.duration > 0) {
                                    val minutes = info.duration / 60
                                    val seconds = info.duration % 60
                                    Text(
                                        text = String.format("Duration: %02d:%02d", minutes, seconds),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = { viewModel.fetchVideoInfo() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh info")
                            }
                        }
                    }
                }
            }

            // Quality Selection
            item {
                QualityDropdown(
                    selectedQuality = selectedQuality,
                    onQualitySelected = { viewModel.onQualitySelected(it) },
                    customFormat = customFormat,
                    onCustomFormatChange = { viewModel.onCustomFormatChanged(it) }
                )
            }

            // Subtitles Configuration
            item {
                SubtitleOptions(
                    config = subtitleConfig,
                    onConfigChange = { viewModel.onSubtitleConfigChanged(it) }
                )
            }

            // Lyrics Toggle
            item {
                LyricsToggle(
                    enabled = downloadLyrics,
                    onEnabledChange = { viewModel.onLyricsToggleChanged(it) }
                )
            }

            // Save Location Picker
            item {
                SaveLocationPicker(
                    currentPath = outputDir,
                    onPathSelected = { path, uri -> viewModel.onPathSelected(path, uri) },
                    recentPaths = recentPaths
                )
            }

            // Start Download Button
            item {
                Button(
                    onClick = { viewModel.startDownload() },
                    enabled = url.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedQuality.isAudioOnly()) "Download Audio" else "Download Video",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            // Active Downloads Header
            if (activeTasks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Active Downloads (${activeTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Active Downloads List
                items(activeTasks, key = { it.id }) { task ->
                    DownloadItemCard(
                        task = task,
                        onCancel = { viewModel.cancelTask(it) },
                        onDismiss = {}
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}
