package com.ynot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ynot.AudioFormat
import com.ynot.DownloadState
import com.ynot.MediaMode
import com.ynot.OutputFormat
import com.ynot.VideoFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: DownloadState,
    onUrlChange: (String) -> Unit,
    onCookiesChange: (String) -> Unit,
    onMediaModeChange: (MediaMode) -> Unit,
    onOutputFormatChange: (OutputFormat) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onUpdate: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ynot") },
                actions = {
                    TextButton(
                        onClick = onUpdate,
                        enabled = !state.isDownloading && !state.isUpdating,
                    ) {
                        Text(if (state.isUpdating) "Updating..." else "Update yt-dlp")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val urlFocusRequester = remember { FocusRequester() }

            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(urlFocusRequester),
                enabled = !state.isDownloading,
                trailingIcon = {
                    if (state.url.isNotEmpty()) {
                        IconButton(onClick = {
                            onUrlChange("")
                            urlFocusRequester.requestFocus()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
            )

            DropdownSelector(
                label = "Mode",
                selected = state.mediaMode.label,
                options = MediaMode.entries.map { it.label },
                onSelect = { label ->
                    MediaMode.entries.first { it.label == label }.let(onMediaModeChange)
                },
                enabled = !state.isDownloading,
                modifier = Modifier.fillMaxWidth(),
            )

            run {
                val formatOptions: List<OutputFormat> = when (state.mediaMode) {
                    MediaMode.VIDEO -> VideoFormat.entries
                    MediaMode.AUDIO_ONLY -> AudioFormat.entries
                }

                DropdownSelector(
                    label = "Format",
                    selected = state.outputFormat.label,
                    options = formatOptions.map { it.label },
                    onSelect = { label ->
                        formatOptions.first { it.label == label }.let(onOutputFormatChange)
                    },
                    enabled = !state.isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = state.cookiesText,
                onValueChange = onCookiesChange,
                label = { Text("Cookies (cookies.txt content)") },
                placeholder = { Text("Paste cookies.txt here (optional)") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isDownloading,
            )

            if (state.isDownloading) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text("Cancel")
                }
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.url.isNotBlank(),
                ) {
                    Text("Download")
                }
            }

            if (state.isDownloading || state.progress > 0f) {
                Column {
                    LinearProgressIndicator(
                        progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (state.progress >= 0f) "%.1f%%".format(state.progress) else "",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        if (state.etaSeconds > 0) {
                            Text(
                                text = "ETA ${state.etaSeconds}s",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (state.statusLine.isNotBlank()) {
                Text(
                    text = state.statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            enabled = enabled,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
