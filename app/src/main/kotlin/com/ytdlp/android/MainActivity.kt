package com.ytdlp.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ytdlp.android.ui.navigation.AppNavigation
import com.ytdlp.android.ui.settings.SettingsViewModel
import com.ytdlp.android.ui.setup.SetupScreen
import com.ytdlp.android.ui.theme.YtDlpTheme

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sharedUrl = extractUrlFromIntent(intent)

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val isSetupComplete by settingsViewModel.isSetupComplete.collectAsState()

            YtDlpTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!isSetupComplete && !settingsViewModel.isFfmpegReady()) {
                        SetupScreen(
                            onComplete = {
                                settingsViewModel.markSetupComplete()
                            }
                        )
                    } else {
                        AppNavigation(initialUrl = sharedUrl)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = extractUrlFromIntent(intent)
        if (url != null) {
            sharedUrl = url
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                text?.let { findUrlInText(it) }
            }
            Intent.ACTION_VIEW -> {
                intent.dataString?.takeIf { isValidUrl(it) }
            }
            else -> null
        }
    }

    private fun findUrlInText(text: String): String? {
        val parts = text.split("\\s+".toRegex())
        return parts.firstOrNull { isValidUrl(it) } ?: text.takeIf { isValidUrl(it) }
    }

    private fun isValidUrl(candidate: String): Boolean {
        return candidate.startsWith("http://") || candidate.startsWith("https://")
    }
}
