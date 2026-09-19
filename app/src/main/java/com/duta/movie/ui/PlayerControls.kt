package com.duta.movie.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.duta.movie.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent

data class VideoQualityTrack(val label: String, val groupIndex: Int, val trackIndex: Int)

@Composable
fun PlayerControls(
    player: Player?,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    onFullscreenToggle: () -> Unit,
    isFullscreen: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    onServerListClick: (() -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    onPipClick: (() -> Unit)? = null,
    qualityTracks: List<VideoQualityTrack> = emptyList(),
    selectedQualityIndex: Int = -1,
    onQualitySelect: ((VideoQualityTrack) -> Unit)? = null,
    onNextEpisodeClick: (() -> Unit)? = null,
    onEpisodeListClick: (() -> Unit)? = null,
    onOpenExternalClick: (() -> Unit)? = null,
    externalBadge: String? = null,
    playPauseFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onPlayPauseFocusChange: (Boolean) -> Unit = {},
    fallbackDurationMs: Long = 0L,
    isTV: Boolean = false
) {
    val playbackState by rememberPlayerState(player)
    val isPlaying = rememberIsPlaying(player)
    val currentPosition = rememberPlayerPosition(player)
    val rawDuration = rememberPlayerDuration(player, fallbackDurationMs)
    val duration = if (rawDuration > 0L) rawDuration else maxOf(fallbackDurationMs, 1L)
    var isPlayFocused by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onVisibilityToggle() })
            }
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            ) {
                // ── Top Bar ───────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isFullscreenFocused by remember { mutableStateOf(false) }
                    val fullscreenScale by animateFloatAsState(if (isFullscreenFocused) 1.2f else 1f)
                    
                    if (!isTV) {
                        IconButton(
                            onClick = onFullscreenToggle,
                            modifier = Modifier
                                .scale(fullscreenScale)
                                .onFocusChanged { isFullscreenFocused = it.isFocused }
                                .focusable()
                                .background(if (isFullscreenFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                                .border(if (isFullscreenFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.toggle_fullscreen),
                                tint = Color.White
                            )
                        }
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = if (isTV) 24.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
                    )

                    // Quality selector button
                    if (qualityTracks.isNotEmpty() && onQualitySelect != null) {
                        var isQualFocused by remember { mutableStateOf(false) }
                        val qualScale by animateFloatAsState(if (isQualFocused) 1.2f else 1f)
                        IconButton(
                            onClick = { showQualityMenu = true },
                            modifier = Modifier
                                .scale(qualScale)
                                .onFocusChanged { isQualFocused = it.isFocused }
                                .focusable()
                                .background(if (isQualFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                                .border(if (isQualFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        ) {
                            Icon(Icons.Default.Hd, contentDescription = stringResource(R.string.quality), tint = Color.White)
                        }
                    }

                    if (onServerListClick != null) {
                        var isServerFocused by remember { mutableStateOf(false) }
                        val serverScale by animateFloatAsState(if (isServerFocused) 1.2f else 1f)
                        IconButton(
                            onClick = onServerListClick,
                            modifier = Modifier
                                .scale(serverScale)
                                .onFocusChanged { isServerFocused = it.isFocused }
                                .focusable()
                                .background(if (isServerFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                                .border(if (isServerFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        ) {
                            Icon(Icons.Default.Dns, contentDescription = stringResource(R.string.select_server), tint = Color.White)
                        }
                    }

                    if (onSubtitleClick != null) {
                        var isSubFocused by remember { mutableStateOf(false) }
                        val subScale by animateFloatAsState(if (isSubFocused) 1.2f else 1f)
                        IconButton(
                            onClick = onSubtitleClick,
                            modifier = Modifier
                                .scale(subScale)
                                .onFocusChanged { isSubFocused = it.isFocused }
                                .focusable()
                                .background(if (isSubFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                                .border(if (isSubFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        ) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = stringResource(R.string.subtitles), tint = Color.White)
                        }
                    }
                    
                    if (onSyncClick != null) {
                        var isSyncFocused by remember { mutableStateOf(false) }
                        val syncScale by animateFloatAsState(if (isSyncFocused) 1.2f else 1f)
                        IconButton(
                            onClick = onSyncClick,
                            modifier = Modifier
                                .scale(syncScale)
                                .onFocusChanged { isSyncFocused = it.isFocused }
                                .focusable()
                                .background(if (isSyncFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                                .border(if (isSyncFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = stringResource(R.string.subtitle_sync), tint = Color.White)
                        }
                    }

                    if (onEpisodeListClick != null) {
                        var isEpFocused by remember { mutableStateOf(false) }
                        val epScale by animateFloatAsState(if (isEpFocused) 1.2f else 1f)
                        IconButton(
                            onClick = onEpisodeListClick,
                            modifier = Modifier
                                .scale(epScale)
                                .onFocusChanged { isEpFocused = it.isFocused }
                                .focusable()
                                .background(if (isEpFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                                .border(if (isEpFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = stringResource(R.string.episodes), tint = Color.White)
                        }
                    }

                    if (onPipClick != null) {
                        var isPipFocused by remember { mutableStateOf(false) }
                        val pipScale by animateFloatAsState(if (isPipFocused) 1.2f else 1f)
                        IconButton(
                            onClick = onPipClick,
                            modifier = Modifier
                                .scale(pipScale)
                                .onFocusChanged { isPipFocused = it.isFocused }
                                .focusable()
                                .background(if (isPipFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                                .border(if (isPipFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                        ) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = stringResource(R.string.picture_in_picture), tint = Color.White)
                        }
                    }

                    if (onOpenExternalClick != null && externalBadge != null) {
                        var isExtFocused by remember { mutableStateOf(false) }
                        val extScale by animateFloatAsState(if (isExtFocused) 1.15f else 1f)
                        val badgeColor = when (externalBadge.lowercase()) {
                            "youtube" -> Color(0xFFFF0000)
                            "bilibili" -> Color(0xFF00AEEC)
                            "dailymotion" -> Color(0xFF0066DC)
                            else -> Color(0xFFCC0000)
                        }
                        Button(
                            onClick = onOpenExternalClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isExtFocused) Color.White else badgeColor,
                                contentColor = if (isExtFocused) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = if (isTV) 14.dp else 10.dp, vertical = if (isTV) 8.dp else 4.dp),
                            modifier = Modifier
                                .scale(extScale)
                                .onFocusChanged { isExtFocused = it.isFocused }
                                .focusable()
                                .border(
                                    if (isExtFocused) BorderStroke(2.5.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                    RoundedCornerShape(20.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in $externalBadge",
                                modifier = Modifier.size(if (isTV) 18.dp else 16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = externalBadge,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isTV) 14.sp else 12.sp
                            )
                        }
                    }

                    if (!isTV) {
                        CastButton(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(start = 12.dp)
                        )
                    }
                }

                // ── Middle Controls ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    ControlIcon(
                        icon = Icons.Default.Replay10, 
                        contentDescription = "Skip Back 10s",
                        modifier = Modifier.size(if (isTV) 64.dp else 56.dp)
                    ) {
                        player?.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) player?.pause() else player?.play()
                        },
                        modifier = Modifier
                            .size(if (isTV) 96.dp else 80.dp)
                            .then(if (playPauseFocusRequester != null) Modifier.focusRequester(playPauseFocusRequester) else Modifier)
                            .onFocusChanged { isPlayFocused = it.isFocused }
                            .focusable()
                            .scale(if (isPlayFocused) 1.2f else 1f)
                            .background(if (isPlayFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                            .border(if (isPlayFocused) BorderStroke(3.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ControlIcon(
                        icon = Icons.Default.Forward10, 
                        contentDescription = "Skip Forward 10s",
                        modifier = Modifier.size(if (isTV) 64.dp else 56.dp)
                    ) {
                        player?.seekTo((player.currentPosition + 10000).coerceAtMost(duration))
                    }

                    if (onNextEpisodeClick != null) {
                        var isNextFocused by remember { mutableStateOf(false) }
                        val nextScale by animateFloatAsState(if (isNextFocused) 1.2f else 1f)
                        Button(
                            onClick = onNextEpisodeClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isNextFocused) Color.White else Color.White.copy(alpha = 0.1f),
                                contentColor = if (isNextFocused) Color.Black else Color.White
                            ),
                            modifier = Modifier
                                .height(if (isTV) 64.dp else 56.dp)
                                .onFocusChanged { isNextFocused = it.isFocused }
                                .scale(nextScale)
                                .focusable()
                                .border(if (isNextFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(28.dp)),
                            shape = RoundedCornerShape(28.dp),
                            contentPadding = PaddingValues(horizontal = if (isTV) 28.dp else 24.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(if (isTV) 36.dp else 32.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.next), 
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isTV) 18.sp else 14.sp
                            )
                        }
                    }
                }

                // ── Bottom Controls ───────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(horizontal = if (isTV) 28.dp else 16.dp, vertical = if (isTV) 16.dp else 8.dp)
                ) {
                    var isSliderFocused by remember { mutableStateOf(false) }
                    var scrubbingFraction by remember { mutableStateOf<Float?>(null) }
                    var isScrubbingActive by remember { mutableStateOf(false) }
                    var pendingSeekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    val scope = rememberCoroutineScope()

                    // Automatically clear scrubbingFraction once player catches up to target position
                    LaunchedEffect(currentPosition) {
                        if (!isScrubbingActive && scrubbingFraction != null) {
                            val targetPos = (scrubbingFraction!! * duration).toLong()
                            if (Math.abs(currentPosition - targetPos) < 2500L) {
                                scrubbingFraction = null
                            }
                        }
                    }

                    val executeSeek = { targetFraction: Float ->
                        val clampedFraction = targetFraction.coerceIn(0f, 1f)
                        scrubbingFraction = clampedFraction
                        isScrubbingActive = true
                        pendingSeekJob?.cancel()
                        pendingSeekJob = scope.launch {
                            delay(350)
                            val targetMs = (clampedFraction * duration).toLong().coerceIn(0L, duration)
                            player?.seekTo(targetMs)
                            delay(600)
                            isScrubbingActive = false
                            scrubbingFraction = null
                        }
                    }

                    val currentFraction = if (duration > 0L) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    val displayFraction = scrubbingFraction ?: currentFraction
                    val displayTimeMs = if (scrubbingFraction != null) (scrubbingFraction!! * duration).toLong() else currentPosition

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(displayTimeMs), 
                            color = if (scrubbingFraction != null) Color(0xFFFFCC00) else Color.White, 
                            fontSize = if (isTV) 18.sp else 14.sp,
                            fontWeight = if (scrubbingFraction != null) FontWeight.Bold else FontWeight.Normal
                        )

                        Slider(
                            value = displayFraction,
                            onValueChange = { frac ->
                                isScrubbingActive = true
                                scrubbingFraction = frac
                            },
                            onValueChangeFinished = {
                                scrubbingFraction?.let { executeSeek(it) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                                .onFocusChanged { isSliderFocused = it.isFocused }
                                .scale(if (isSliderFocused) 1.04f else 1f)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.DirectionRight || event.key == Key.DirectionLeft) {
                                        if (event.type == KeyEventType.KeyDown) {
                                            isScrubbingActive = true
                                            val stepMs = if (isTV) {
                                                when {
                                                    duration > 45 * 60 * 1000L -> 60_000L // 1 min for full movies
                                                    duration > 15 * 60 * 1000L -> 30_000L // 30s for medium videos
                                                    else -> 10_000L // 10s for short videos
                                                }
                                            } else {
                                                10_000L
                                            }
                                            val stepFraction = if (duration > 0L) (stepMs.toFloat() / duration).coerceAtLeast(0.002f) else 0.05f
                                            val current = scrubbingFraction ?: currentFraction
                                            val nextFraction = if (event.key == Key.DirectionRight) {
                                                (current + stepFraction).coerceIn(0f, 1f)
                                            } else {
                                                (current - stepFraction).coerceIn(0f, 1f)
                                            }
                                            executeSeek(nextFraction)
                                        }
                                        true
                                    } else if ((event.key == Key.DirectionCenter || event.key == Key.Enter) && event.type == KeyEventType.KeyDown) {
                                        scrubbingFraction?.let { frac: Float ->
                                            pendingSeekJob?.cancel()
                                            val targetMs = (frac * duration).toLong().coerceIn(0L, duration)
                                            player?.seekTo(targetMs)
                                            scope.launch {
                                                delay(500)
                                                isScrubbingActive = false
                                                scrubbingFraction = null
                                            }
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            colors = SliderDefaults.colors(
                                thumbColor = if (isSliderFocused || isScrubbingActive) Color.White else Color.Red,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )
                        Text(
                            text = formatTime(duration), 
                            color = Color.White, 
                            fontSize = if (isTV) 18.sp else 14.sp
                        )
                    }
                }
            }
        }

        // ── Quality picker dialog ─────────────────────────────────────────────
        if (showQualityMenu && qualityTracks.isNotEmpty() && onQualitySelect != null) {
            val firstItemFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(300)
                try { firstItemFocusRequester.requestFocus() } catch(_: Exception) {}
            }
            AlertDialog(
                onDismissRequest = { showQualityMenu = false },
                title = { Text(stringResource(R.string.video_quality), color = Color.White) },
                text = {
                    LazyColumn {
                        item {
                            var isFocused by remember { mutableStateOf(false) }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.auto), color = Color.White) },
                                trailingContent = { if (selectedQualityIndex == -1) Icon(Icons.Default.Check, null, tint = Color.Red) },
                                modifier = Modifier
                                    .focusRequester(firstItemFocusRequester)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .clickable {
                                        player?.trackSelectionParameters = player?.trackSelectionParameters
                                            ?.buildUpon()
                                            ?.clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                                            ?.build() ?: return@clickable
                                        showQualityMenu = false
                                    }
                                    .border(if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                                colors = ListItemDefaults.colors(containerColor = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            )
                        }
                        items(qualityTracks) { track ->
                            val isSelected = selectedQualityIndex == track.groupIndex
                            var isFocused by remember { mutableStateOf(false) }
                            ListItem(
                                headlineContent = { Text(track.label, color = Color.White) },
                                trailingContent = { if (isSelected) Icon(Icons.Default.Check, null, tint = Color.Red) },
                                modifier = Modifier
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .clickable {
                                        onQualitySelect(track)
                                        showQualityMenu = false
                                    }
                                    .border(if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp)),
                                colors = ListItemDefaults.colors(containerColor = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            )
                        }
                    }
                },
                confirmButton = { 
                    var isCloseFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showQualityMenu = false },
                        modifier = Modifier
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .focusable()
                            .background(if (isCloseFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .border(if (isCloseFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
                    ) { Text(stringResource(R.string.close), color = if (isCloseFocused) Color.White else Color.Red) } 
                },
                containerColor = Color(0xFF1A1A1A)
            )
        }
    }
}

@Composable
fun ControlIcon(
    icon: ImageVector, 
    contentDescription: String, 
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.2f else 1f)
    IconButton(
        onClick = onClick, 
        modifier = modifier
            .size(56.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .focusable()
            .background(if (isFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
            .border(if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun rememberPlayerState(player: Player?): State<Int> {
    val state = remember { mutableIntStateOf(player?.playbackState ?: Player.STATE_IDLE) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.intValue = playbackState
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                state.intValue = player?.playbackState ?: Player.STATE_IDLE
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return state
}

@Composable
fun rememberPlayerPosition(player: Player?): Long {
    var position by remember { mutableLongStateOf(player?.currentPosition ?: 0L) }
    val delayMs = 100L
    LaunchedEffect(player) {
        while (true) {
            position = player?.currentPosition ?: 0L
            delay(delayMs)
        }
    }
    return position
}

@Composable
fun rememberPlayerDuration(player: Player?, fallbackDurationMs: Long = 0L): Long {
    var duration by remember(player, fallbackDurationMs) { 
        val d = player?.duration?.coerceAtLeast(0L) ?: 0L
        mutableLongStateOf(if (d > 0L) d else fallbackDurationMs) 
    }
    DisposableEffect(player, fallbackDurationMs) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = player?.duration?.coerceAtLeast(0L) ?: 0L
                duration = if (d > 0L) d else fallbackDurationMs
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val d = player?.duration?.coerceAtLeast(0L) ?: 0L
                duration = if (d > 0L) d else fallbackDurationMs
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player, fallbackDurationMs) {
        while (true) {
            val d = player?.duration?.coerceAtLeast(0L) ?: 0L
            val target = if (d > 0L) d else fallbackDurationMs
            if (target > 0L && target != duration) {
                duration = target
            }
            delay(500)
        }
    }
    return duration
}

@Composable
fun rememberIsPlaying(player: Player?): Boolean {
    var isPlaying by remember { 
        mutableStateOf(player?.isPlaying == true || (player?.playbackState == Player.STATE_READY && player?.playWhenReady == true)) 
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = player?.isPlaying == true || (playbackState == Player.STATE_READY && player?.playWhenReady == true)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = player?.isPlaying == true || (player?.playbackState == Player.STATE_READY && playWhenReady)
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            val playing = player?.isPlaying == true || (player?.playbackState == Player.STATE_READY && player?.playWhenReady == true)
            if (playing != isPlaying) {
                isPlaying = playing
            }
            delay(250)
        }
    }
    return isPlaying
}

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.2f else 1f)
    var mediaRouteButton by remember { mutableStateOf<MediaRouteButton?>(null) }
    var isCastAvailable by remember { mutableStateOf(true) }
    
    if (!isCastAvailable) return // Hide button entirely if GMS unavailable
    
    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { mediaRouteButton?.performClick() }
            .background(if (isFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
            .border(if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                MediaRouteButton(context).apply {
                    try {
                        CastButtonFactory.setUpMediaRouteButton(context, this)
                        mediaRouteButton = this
                    } catch (e: Exception) {
                        android.util.Log.w("CastButton", "Cast unavailable on this device", e)
                        isCastAvailable = false
                    }
                }
            },
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun formatTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
