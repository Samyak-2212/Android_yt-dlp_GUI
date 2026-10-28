package com.ytdlp.android.ui.download.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun UrlInputBar(
    url: String,
    onUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("Video or Audio URL") },
        placeholder = { Text("Paste YouTube or media link here") },
        leadingIcon = {
            Icon(Icons.Default.Link, contentDescription = "Link")
        },
        trailingIcon = {
            if (url.isNotEmpty()) {
                IconButton(onClick = { onUrlChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            } else {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                        if (pasteText.isNotBlank()) {
                            onUrlChange(pasteText.trim())
                        }
                    }
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}
