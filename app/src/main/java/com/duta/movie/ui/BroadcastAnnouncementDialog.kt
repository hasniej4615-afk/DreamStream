package com.duta.movie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.duta.movie.model.BroadcastMessage
import com.duta.movie.util.DeviceUtils
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BroadcastAnnouncementDialog(
    broadcast: BroadcastMessage,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isRealTV = remember { DeviceUtils.isTvDevice(context) }

    val dismissFocusRequester = remember { FocusRequester() }
    val messageFocusRequester = remember { FocusRequester() }
    var isDismissFocused by remember { mutableStateOf(false) }
    var isMessageFocused by remember { mutableStateOf(false) }
    val messageScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        delay(150)
        try {
            dismissFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Strictly Netflix Theme: Red, White, and Black
    val netflixRed = Color(0xFFE50914)
    val focusedRed = Color(0xFFFF1744)

    val (typeLabel, iconVector) = when (broadcast.type.lowercase()) {
        "alert", "danger", "error" -> Pair("PENTING", Icons.Default.Warning)
        "update", "new" -> Pair("KEMASKINI", Icons.Default.Campaign)
        else -> Pair("PENGUMUMAN", Icons.Default.Campaign)
    }

    val formattedDate = remember(broadcast.timestamp) {
        if (broadcast.timestamp > 0) {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            sdf.format(Date(broadcast.timestamp))
        } else ""
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isRealTV) 0.65f else 0.9f)
                .widthIn(max = if (isRealTV) 580.dp else 420.dp)
                .clip(RoundedCornerShape(if (isRealTV) 16.dp else 14.dp))
                .border(
                    BorderStroke(if (isRealTV) 2.dp else 1.5.dp, netflixRed.copy(alpha = 0.55f)),
                    RoundedCornerShape(if (isRealTV) 16.dp else 14.dp)
                ),
            color = Color(0xFF141414), // Netflix Pitch Black
            shape = RoundedCornerShape(if (isRealTV) 16.dp else 14.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isRealTV) 26.dp else 18.dp,
                        vertical = if (isRealTV) 22.dp else 16.dp
                    )
            ) {
                // ── HEADER ───────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isRealTV) 40.dp else 34.dp)
                            .background(netflixRed.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, netflixRed.copy(alpha = 0.4f)), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = netflixRed,
                            modifier = Modifier.size(if (isRealTV) 24.dp else 20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(if (isRealTV) 14.dp else 10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = netflixRed
                            ) {
                                Text(
                                    text = typeLabel,
                                    color = Color.White,
                                    fontSize = if (isRealTV) 10.5.sp else 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            if (formattedDate.isNotBlank()) {
                                Text(
                                    text = formattedDate,
                                    fontSize = if (isRealTV) 11.5.sp else 10.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = broadcast.title.ifBlank { "Pengumuman Rasmi" },
                            fontSize = if (isRealTV) 18.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // On mobile touch devices, show subtle close X icon; on TV, remote Back or Dismiss button handles it
                    if (!isRealTV) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isRealTV) 14.dp else 10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(if (isRealTV) 14.dp else 10.dp))

                // ── BODY ─────────────────────────────────────────────────────
                // Optimized for TV D-Pad scrolling: if content is long, focusing message allows D-Pad UP/DOWN scrolling
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isRealTV) 260.dp else 200.dp)
                        .focusRequester(messageFocusRequester)
                        .onFocusChanged { isMessageFocused = it.isFocused }
                        .then(
                            if (isRealTV && messageScrollState.maxValue > 0) {
                                Modifier
                                    .border(
                                        BorderStroke(
                                            width = 1.5.dp,
                                            color = if (isMessageFocused) Color.White.copy(alpha = 0.6f) else Color.Transparent
                                        ),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(4.dp)
                                    .focusable()
                            } else Modifier
                        )
                        .verticalScroll(messageScrollState)
                ) {
                    Text(
                        text = broadcast.message,
                        fontSize = if (isRealTV) 15.sp else 13.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = if (isRealTV) 22.sp else 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(if (isRealTV) 16.dp else 14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(if (isRealTV) 14.dp else 12.dp))

                // ── FOOTER ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daripada: ${broadcast.author.ifBlank { "Admin" }}",
                        fontSize = if (isRealTV) 12.5.sp else 11.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .focusRequester(dismissFocusRequester)
                            .onFocusChanged { isDismissFocused = it.isFocused }
                            .scale(if (isDismissFocused) (if (isRealTV) 1.08f else 1.05f) else 1f)
                            .border(
                                width = if (isDismissFocused) 2.5.dp else 0.dp,
                                color = if (isDismissFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusProperties {
                                if (isRealTV && messageScrollState.maxValue > 0) {
                                    up = messageFocusRequester
                                }
                            }
                            .focusable(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDismissFocused) focusedRed else netflixRed,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(
                            horizontal = if (isRealTV) 24.dp else 18.dp,
                            vertical = if (isRealTV) 10.dp else 6.dp
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Faham / Tutup",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isRealTV) 13.5.sp else 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
