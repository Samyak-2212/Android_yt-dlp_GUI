package com.ytdlp.android.ui.download.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ytdlp.android.data.model.VideoQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityDropdown(
    selectedQuality: VideoQuality,
    onQualitySelected: (VideoQuality) -> Unit,
    customFormat: String,
    onCustomFormatChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedQuality.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Download Quality / Format") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                VideoQuality.values().forEach { quality ->
                    DropdownMenuItem(
                        text = { Text(quality.label) },
                        onClick = {
                            onQualitySelected(quality)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (selectedQuality == VideoQuality.CUSTOM) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customFormat,
                onValueChange = onCustomFormatChange,
                label = { Text("Custom yt-dlp Format String") },
                placeholder = { Text("e.g. bv*[height<=1080]+ba/b") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
