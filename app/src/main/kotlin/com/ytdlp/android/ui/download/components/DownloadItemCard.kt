package com.ytdlp.android.ui.download.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ytdlp.android.data.model.DownloadStatus
import com.ytdlp.android.data.model.DownloadTask
import com.ytdlp.android.ui.theme.AccentGreen
import java.io.File

@Composable
fun DownloadItemCard(
    task: DownloadTask,
    onCancel: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title & Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (task.title.isNotBlank()) task.title else task.url,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                when (task.status) {
                    DownloadStatus.COMPLETE -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = AccentGreen
                        )
                    }
                    DownloadStatus.ERROR -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Badges row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(task.quality.label) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(task.status.name) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar (for downloading / extracting)
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.EXTRACTING_INFO || task.status == DownloadStatus.MERGING) {
                if (task.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val percent = (task.progress * 100).toInt()
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (task.speed > 0) {
                        val kb = task.speed / 1024.0
                        val speedStr = if (kb >= 1024.0) String.format("%.1f MB/s", kb / 1024.0) else String.format("%.0f KB/s", kb)
                        Text(
                            text = speedStr,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (task.eta > 0) {
                        val min = task.eta / 60
                        val sec = task.eta % 60
                        Text(
                            text = String.format("ETA: %02d:%02d", min, sec),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Error message
            if (task.status == DownloadStatus.ERROR && !task.error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PENDING || task.status == DownloadStatus.EXTRACTING_INFO) {
                    OutlinedButton(
                        onClick = { onCancel(task.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                } else if (task.status == DownloadStatus.COMPLETE && task.filePath.isNotBlank()) {
                    val file = File(task.filePath)
                    if (file.exists()) {
                        IconButton(onClick = {
                            try {
                                val uri: Uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (task.quality.isAudioOnly()) "audio/*" else "video/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }

                        OutlinedButton(onClick = {
                            try {
                                val uri: Uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, if (task.quality.isAudioOnly()) "audio/*" else "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(viewIntent)
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open")
                        }
                    }
                }
            }
        }
    }
}
