package com.ytdlp.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ytdlp.android.BuildConfig
import com.ytdlp.android.data.model.VideoQuality
import com.ytdlp.android.ui.theme.AccentGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val maxConcurrent by viewModel.maxConcurrentDownloads.collectAsState()
    val ytDlpUpdateState by viewModel.ytDlpUpdateState.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val isDownloadingAppUpdate by viewModel.isDownloadingAppUpdate.collectAsState()
    val appDownloadProgress by viewModel.appDownloadProgress.collectAsState()

    var qualityDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Section: Appearance
        item {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = themeMode == "LIGHT",
                            onClick = { viewModel.setThemeMode("LIGHT") },
                            label = { Text("Light") },
                            leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null) }
                        )
                        FilterChip(
                            selected = themeMode == "DARK",
                            onClick = { viewModel.setThemeMode("DARK") },
                            label = { Text("Dark") },
                            leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) }
                        )
                        FilterChip(
                            selected = themeMode == "SYSTEM",
                            onClick = { viewModel.setThemeMode("SYSTEM") },
                            label = { Text("System") },
                            leadingIcon = { Icon(Icons.Default.BrightnessMedium, contentDescription = null) }
                        )
                    }
                }
            }
        }

        // Section: Download Defaults
        item {
            Text(
                text = "Download Defaults",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Default Video Quality",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = qualityDropdownExpanded,
                        onExpandedChange = { qualityDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = defaultQuality.label,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = qualityDropdownExpanded,
                            onDismissRequest = { qualityDropdownExpanded = false }
                        ) {
                            VideoQuality.values().forEach { quality ->
                                DropdownMenuItem(
                                    text = { Text(quality.label) },
                                    onClick = {
                                        viewModel.setDefaultQuality(quality)
                                        qualityDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Max Concurrent Downloads: $maxConcurrent",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = maxConcurrent.toFloat(),
                        onValueChange = { viewModel.setMaxConcurrentDownloads(it.toInt()) },
                        valueRange = 1f..5f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Section: Engines & Updates
        item {
            Text(
                text = "Engines & Updates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // yt-dlp version & check
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "yt-dlp Engine", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Installed version: ${viewModel.getYtDlpCurrentVersion()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(onClick = { viewModel.checkForYtDlpUpdate() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check")
                        }
                    }

                    when (val state = ytDlpUpdateState) {
                        is UpdateCheckState.Checking -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking GitHub for updates...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is UpdateCheckState.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (state.data.hasUpdate) {
                                Text(
                                    text = "Update available: ${state.data.latestVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "yt-dlp is up to date!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGreen
                                )
                            }
                        }
                        is UpdateCheckState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Error: ${state.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // FFmpeg status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "FFmpeg Post-Processor", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (viewModel.isFfmpegReady()) viewModel.getFfmpegVersion() else "Not loaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (viewModel.isFfmpegReady()) AccentGreen else MaterialTheme.colorScheme.error
                            )
                        }

                        if (viewModel.isFfmpegReady()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // App self-update
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "App Version", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(onClick = { viewModel.checkForAppUpdate() }) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check Update")
                        }
                    }

                    when (val state = appUpdateState) {
                        is UpdateCheckState.Checking -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking for new APK...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is UpdateCheckState.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (state.data.isNewer) {
                                Column {
                                    Text(
                                        text = "New release found: v${state.data.tagName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (isDownloadingAppUpdate) {
                                        LinearProgressIndicator(
                                            progress = { appDownloadProgress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Button(onClick = { viewModel.downloadAndInstallAppUpdate(state.data.apkUrl) }) {
                                            Text("Download & Install APK")
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "App is on the latest version!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentGreen
                                )
                            }
                        }
                        is UpdateCheckState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Update check failed: ${state.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        // Section: About
        item {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "YT-DLP Android GUI",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A simple, powerful, and modern Material 3 interface for yt-dlp running completely on-device via embedded Python.\n\nOpen-source under the GPL-3.0 License.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
