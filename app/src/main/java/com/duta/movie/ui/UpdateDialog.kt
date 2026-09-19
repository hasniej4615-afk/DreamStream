package com.duta.movie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duta.movie.util.DownloadState
import com.duta.movie.util.UpdateChecker
import com.duta.movie.util.UpdateDownloader
import com.duta.movie.util.UpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val primaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(downloadState) {
        // Auto-request focus for TV D-Pad on state transitions
        delay(100)
        try {
            primaryFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = {
            if (downloadState is DownloadState.Downloading) {
                downloadJob?.cancel()
                downloadJob = null
            }
            onDismissRequest()
        },
        title = {
            Text(
                text = when (downloadState) {
                    is DownloadState.Downloading -> "Downloading Update..."
                    is DownloadState.ReadyToInstall -> "Update Ready to Install"
                    is DownloadState.Error -> "Update Failed"
                    else -> "Update Available"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Version ${updateInfo.latestVersionName} is now available!",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )

                if (updateInfo.changelog.isNotBlank()) {
                    Surface(
                        color = Color(0xFF2A2A2A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = updateInfo.changelog,
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                when (val state = downloadState) {
                    is DownloadState.Downloading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.progress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFFE50914),
                                    trackColor = Color(0xFF444444)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${(state.progress * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${UpdateDownloader.formatBytes(state.downloadedBytes)} / ${UpdateDownloader.formatBytes(state.totalBytes)}",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFFE50914),
                                    trackColor = Color(0xFF444444)
                                )
                                Text(
                                    text = "Downloaded: ${UpdateDownloader.formatBytes(state.downloadedBytes)}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    is DownloadState.ReadyToInstall -> {
                        Surface(
                            color = Color(0xFF1B3820),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ APK downloaded successfully!\nClick 'Install Now' to proceed with the update.",
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is DownloadState.Error -> {
                        Surface(
                            color = Color(0xFF3E1E1E),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Error: ${state.message}",
                                color = Color(0xFFE57373),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is DownloadState.Idle -> {
                        // Changelog already displayed above
                    }
                }
            }
        },
        confirmButton = {
            when (val state = downloadState) {
                is DownloadState.Downloading -> {
                    // No confirm button while actively downloading
                }
                is DownloadState.ReadyToInstall -> {
                    var isFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { UpdateDownloader.installApk(context, state.file) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFocused) Color.White else Color(0xFFE50914)
                        ),
                        modifier = Modifier
                            .focusRequester(primaryFocusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .scale(if (isFocused) 1.05f else 1f)
                            .focusable()
                    ) {
                        Text(
                            text = "Install Now",
                            color = if (isFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is DownloadState.Error -> {
                    var isFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            val url = updateInfo.apkUrl
                            if (!url.isNullOrBlank()) {
                                downloadJob = scope.launch {
                                    downloadState = DownloadState.Downloading(0f, 0L, 0L)
                                    val result = UpdateDownloader.downloadApk(context, url) { prog, down, tot ->
                                        downloadState = DownloadState.Downloading(prog, down, tot)
                                    }
                                    result.fold(
                                        onSuccess = { file ->
                                            downloadState = DownloadState.ReadyToInstall(file)
                                            UpdateDownloader.installApk(context, file)
                                        },
                                        onFailure = { err ->
                                            downloadState = DownloadState.Error(err.message ?: "Download failed")
                                        }
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFocused) Color.White else Color(0xFFE50914)
                        ),
                        modifier = Modifier
                            .focusRequester(primaryFocusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .scale(if (isFocused) 1.05f else 1f)
                            .focusable()
                    ) {
                        Text(
                            text = "Retry",
                            color = if (isFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is DownloadState.Idle -> {
                    var isFocused by remember { mutableStateOf(false) }
                    val hasApkUrl = !updateInfo.apkUrl.isNullOrBlank()

                    Button(
                        onClick = {
                            if (hasApkUrl) {
                                downloadJob = scope.launch {
                                    downloadState = DownloadState.Downloading(0f, 0L, 0L)
                                    val result = UpdateDownloader.downloadApk(context, updateInfo.apkUrl!!) { prog, down, tot ->
                                        downloadState = DownloadState.Downloading(prog, down, tot)
                                    }
                                    result.fold(
                                        onSuccess = { file ->
                                            downloadState = DownloadState.ReadyToInstall(file)
                                            UpdateDownloader.installApk(context, file)
                                        },
                                        onFailure = { err ->
                                            downloadState = DownloadState.Error(err.message ?: "Download failed")
                                        }
                                    )
                                }
                            } else {
                                UpdateChecker.openTelegram(context, updateInfo.telegramUrl)
                                onDismissRequest()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFocused) Color.White else Color(0xFFE50914)
                        ),
                        modifier = Modifier
                            .focusRequester(primaryFocusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .scale(if (isFocused) 1.05f else 1f)
                            .focusable()
                    ) {
                        Text(
                            text = if (hasApkUrl) "Update Now" else "Get on Telegram",
                            color = if (isFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Secondary Telegram button if apkUrl is available or download failed
                if (updateInfo.telegramUrl.isNotBlank() && (downloadState is DownloadState.Error || (downloadState is DownloadState.Idle && !updateInfo.apkUrl.isNullOrBlank()))) {
                    var isTgFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = {
                            UpdateChecker.openTelegram(context, updateInfo.telegramUrl)
                        },
                        modifier = Modifier
                            .onFocusChanged { isTgFocused = it.isFocused }
                            .scale(if (isTgFocused) 1.05f else 1f)
                            .focusable()
                    ) {
                        Text(
                            text = "Telegram",
                            color = if (isTgFocused) Color.White else Color(0xFF64B5F6)
                        )
                    }
                }

                if (downloadState is DownloadState.Downloading) {
                    var isCancelFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = {
                            downloadJob?.cancel()
                            downloadJob = null
                            downloadState = DownloadState.Idle
                        },
                        modifier = Modifier
                            .onFocusChanged { isCancelFocused = it.isFocused }
                            .scale(if (isCancelFocused) 1.05f else 1f)
                            .focusable()
                    ) {
                        Text(
                            text = "Cancel",
                            color = if (isCancelFocused) Color.White else Color.Gray
                        )
                    }
                } else {
                    var isDismissFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .onFocusChanged { isDismissFocused = it.isFocused }
                            .scale(if (isDismissFocused) 1.05f else 1f)
                            .focusable()
                    ) {
                        Text(
                            text = if (downloadState is DownloadState.ReadyToInstall) "Close" else "Later",
                            color = if (isDismissFocused) Color.White else Color.Gray
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp)
    )
}
