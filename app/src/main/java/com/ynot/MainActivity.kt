package com.ynot

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.ynot.ui.MainScreen
import com.ynot.ui.theme.YnotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startDownload(this@MainActivity)
        }
    }

    private val youtubeHosts = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "youtu.be", "music.youtube.com",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)

        setContent {
            YnotTheme {
                val state by viewModel.state.collectAsState()

                MainScreen(
                    state = state,
                    onUrlChange = viewModel::updateUrl,
                    onCookiesChange = viewModel::updateCookiesText,
                    onMediaModeChange = viewModel::updateMediaMode,
                    onOutputFormatChange = viewModel::updateOutputFormat,
                    onDownload = ::onDownloadRequested,
                    onCancel = viewModel::cancelDownload,
                    onUpdate = { viewModel.updateYtDlp(this@MainActivity) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return

        val url = text.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                try {
                    val host = android.net.Uri.parse(line).host?.lowercase()
                    host in youtubeHosts
                } catch (_: Exception) {
                    false
                }
            } ?: return

        viewModel.updateUrl(url)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (viewModel.state.value.url.isNotEmpty()) return

        val clip = getSystemService(ClipboardManager::class.java)
            ?.primaryClip?.getItemAt(0)?.text?.toString() ?: return

        if (clip.startsWith("http://") || clip.startsWith("https://")) {
            viewModel.updateUrl(clip)
            Toast.makeText(this, "Pasted URL from clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onDownloadRequested() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        viewModel.startDownload(this@MainActivity)
    }
}
