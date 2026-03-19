package com.ynot

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class MediaMode(val label: String) {
    VIDEO("Video"),
    AUDIO_ONLY("Audio only"),
}

enum class RemuxFormat(val label: String, val value: String?) {
    NONE("None (original)", null),
    MP4("MP4", "mp4"),
    MKV("MKV", "mkv"),
    WEBM("WebM", "webm"),
}

data class DownloadState(
    val url: String = "",
    val cookiesText: String = "",
    val mediaMode: MediaMode = MediaMode.VIDEO,
    val remuxFormat: RemuxFormat = RemuxFormat.NONE,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val etaSeconds: Long = -1,
    val statusLine: String = "",
    val error: String? = null,
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val processId = "ynot-download"

    fun updateUrl(url: String) = _state.update { it.copy(url = url) }
    fun updateCookiesText(text: String) = _state.update { it.copy(cookiesText = text) }
    fun updateMediaMode(mode: MediaMode) = _state.update { it.copy(mediaMode = mode) }
    fun updateRemuxFormat(format: RemuxFormat) = _state.update { it.copy(remuxFormat = format) }

    fun startDownload(cacheDir: File) {
        val current = _state.value
        if (current.url.isBlank() || current.isDownloading) return

        _state.update {
            it.copy(
                isDownloading = true,
                progress = 0f,
                etaSeconds = -1,
                statusLine = "Starting download...",
                error = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = buildRequest(current, cacheDir)

                YoutubeDL.getInstance().execute(
                    request,
                    processId,
                ) { progress, eta, line ->
                    _state.update {
                        it.copy(
                            progress = progress,
                            etaSeconds = eta,
                            statusLine = line,
                        )
                    }
                }

                _state.update {
                    it.copy(
                        isDownloading = false,
                        progress = 100f,
                        statusLine = "Download complete!",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isDownloading = false,
                        error = e.message ?: "Unknown error",
                        statusLine = "",
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        YoutubeDL.getInstance().destroyProcessById(processId)
        _state.update {
            it.copy(
                isDownloading = false,
                statusLine = "Cancelled",
            )
        }
    }

    private fun buildRequest(state: DownloadState, cacheDir: File): YoutubeDLRequest {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath

        return YoutubeDLRequest(state.url).apply {
            addOption("-o", "$downloadsDir/%(title).200B.%(ext)s")
            addOption("--no-mtime")

            when (state.mediaMode) {
                MediaMode.VIDEO -> {
                    addOption("-f", "bestvideo+bestaudio/best")
                }
                MediaMode.AUDIO_ONLY -> {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                }
            }

            state.remuxFormat.value?.let { format ->
                addOption("--remux-video", format)
            }

            if (state.cookiesText.isNotBlank()) {
                val cookiesFile = File(cacheDir, "cookies.txt").apply {
                    writeText(state.cookiesText)
                }
                addOption("--cookies", cookiesFile.absolutePath)
            }
        }
    }
}
