package com.ynot

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
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

interface OutputFormat {
    val label: String
    val value: String?
}

enum class VideoFormat(override val label: String, override val value: String?) : OutputFormat {
    NONE("None (original)", null),
    MP4("MP4", "mp4"),
    MKV("MKV", "mkv"),
    WEBM("WebM", "webm"),
}

enum class AudioFormat(override val label: String, override val value: String?) : OutputFormat {
    MP3("MP3", "mp3"),
    M4A("M4A", "m4a"),
    OPUS("Opus", "opus"),
    FLAC("FLAC", "flac"),
}

data class DownloadState(
    val url: String = "",
    val cookiesText: String = "",
    val mediaMode: MediaMode = MediaMode.VIDEO,
    val outputFormat: OutputFormat = VideoFormat.NONE,
    val isDownloading: Boolean = false,
    val isUpdating: Boolean = false,
    val progress: Float = 0f,
    val etaSeconds: Long = -1,
    val statusLine: String = "",
    val error: String? = null,
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val processId = "ynot-download"
    private val tag = "Ynot"

    fun updateUrl(url: String) = _state.update { it.copy(url = url) }
    fun updateCookiesText(text: String) = _state.update { it.copy(cookiesText = text) }
    fun updateMediaMode(mode: MediaMode) = _state.update {
        val defaultFormat: OutputFormat = when (mode) {
            MediaMode.VIDEO -> VideoFormat.NONE
            MediaMode.AUDIO_ONLY -> AudioFormat.MP3
        }
        it.copy(mediaMode = mode, outputFormat = defaultFormat)
    }
    fun updateOutputFormat(format: OutputFormat) = _state.update { it.copy(outputFormat = format) }

    fun startDownload(context: Context) {
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
                val request = buildRequest(current, context.cacheDir)
                Log.i(tag, "Starting download: ${current.url}")
                Log.i(tag, "Output dir: ${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath}")

                val response = YoutubeDL.getInstance().execute(
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

                Log.i(tag, "yt-dlp exit code: ${response.exitCode}")
                Log.i(tag, "yt-dlp stdout:\n${response.out}")
                Log.i(tag, "yt-dlp stderr:\n${response.err}")

                scanDownloadsDir(context)

                _state.update {
                    it.copy(
                        isDownloading = false,
                        progress = 100f,
                        statusLine = "Download complete!",
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Download failed", e)
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

    fun updateYtDlp(context: Context) {
        if (_state.value.isUpdating) return

        _state.update { it.copy(isUpdating = true, error = null, statusLine = "Updating yt-dlp...") }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(context, UpdateChannel.STABLE)
                Log.i(tag, "yt-dlp update result: $status")
                _state.update {
                    it.copy(
                        isUpdating = false,
                        statusLine = when (status?.name) {
                            "ALREADY_UP_TO_DATE" -> "yt-dlp is already up to date"
                            else -> "yt-dlp updated successfully"
                        },
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "yt-dlp update failed", e)
                _state.update {
                    it.copy(
                        isUpdating = false,
                        error = "Update failed: ${e.message}",
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

    private fun scanDownloadsDir(context: Context) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val files = downloadsDir.listFiles()?.map { it.absolutePath }?.toTypedArray() ?: return
        Log.d(tag, "Scanning ${files.size} files in Downloads for MediaStore")
        MediaScannerConnection.scanFile(context, files, null) { path, uri ->
            Log.d(tag, "Scanned: $path -> $uri")
        }
    }

    private fun buildRequest(state: DownloadState, cacheDir: File): YoutubeDLRequest {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath

        Log.d(tag, "Downloads dir: $downloadsDir, exists=${File(downloadsDir).exists()}, writable=${File(downloadsDir).canWrite()}")

        return YoutubeDLRequest(state.url).apply {
            addOption("-o", "$downloadsDir/%(title).200B.%(ext)s")
            addOption("--no-mtime")
            addOption("--no-update")

            when (state.mediaMode) {
                MediaMode.VIDEO -> {
                    addOption("-f", "bestvideo+bestaudio/best")
                    (state.outputFormat as? VideoFormat)?.value?.let { format ->
                        addOption("--remux-video", format)
                    }
                }
                MediaMode.AUDIO_ONLY -> {
                    addOption("-x")
                    val audioFormat = (state.outputFormat as? AudioFormat)?.value ?: "mp3"
                    addOption("--audio-format", audioFormat)
                }
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
