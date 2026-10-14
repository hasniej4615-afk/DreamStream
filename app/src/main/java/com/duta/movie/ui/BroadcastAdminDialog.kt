package com.duta.movie.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duta.movie.util.DeviceUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BroadcastAdminDialog(
    viewModel: VideoViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isRealTV = remember { DeviceUtils.isTvDevice(context) }
    val activeBroadcast by viewModel.activeBroadcast.collectAsStateWithLifecycle()
    val isSending by viewModel.isSendingBroadcast.collectAsStateWithLifecycle()
    val isBroadcastLoading by viewModel.isBroadcastLoading.collectAsStateWithLifecycle()

    var titleInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("info") }

    // Pre-populate input fields if active broadcast exists and user hasn't typed yet
    var hasInitializedFields by remember { mutableStateOf(false) }
    LaunchedEffect(activeBroadcast) {
        if (!hasInitializedFields && activeBroadcast != null) {
            val b = activeBroadcast!!
            titleInput = b.title
            messageInput = b.message
            selectedType = b.type.lowercase().ifBlank { "info" }
            hasInitializedFields = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchActiveBroadcast(forceShow = false)
    }

    // Strictly Netflix Theme: Red, White, and Black
    val netflixRed = Color(0xFFE50914)
    val focusedRed = Color(0xFFFF1744)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isRealTV) 0.70f else 0.92f)
                .widthIn(max = if (isRealTV) 620.dp else 480.dp)
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
                        horizontal = if (isRealTV) 24.dp else 18.dp,
                        vertical = if (isRealTV) 20.dp else 16.dp
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
                            imageVector = Icons.Default.Campaign,
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
                                    text = "DEBUG ADMIN",
                                    color = Color.White,
                                    fontSize = if (isRealTV) 10.5.sp else 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Siaran Mesej Global",
                            fontSize = if (isRealTV) 17.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    var isRefreshFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { viewModel.fetchActiveBroadcast(forceShow = false) },
                        enabled = !isBroadcastLoading,
                        modifier = Modifier
                            .size(if (isRealTV) 38.dp else 32.dp)
                            .onFocusChanged { isRefreshFocused = it.isFocused }
                            .scale(if (isRefreshFocused) 1.1f else 1f)
                            .border(
                                width = if (isRefreshFocused) 2.dp else 0.dp,
                                color = if (isRefreshFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusable()
                    ) {
                        if (isBroadcastLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(if (isRealTV) 18.dp else 16.dp),
                                strokeWidth = 2.dp,
                                color = netflixRed
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Muat Semula",
                                tint = if (isRefreshFocused) Color.White else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(if (isRealTV) 20.dp else 18.dp)
                            )
                        }
                    }

                    if (!isRealTV) {
                        Spacer(modifier = Modifier.width(4.dp))

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
                Spacer(modifier = Modifier.height(10.dp))

                // ── SCROLLABLE CONTENT ───────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (isRealTV) 12.dp else 10.dp)
                ) {
                    // 1. Live Status Card
                    val isLive = activeBroadcast != null && activeBroadcast!!.isActive
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLive) Color(0xFF1E1E1E) else Color(0xFF181818),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isLive) netflixRed.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(if (isRealTV) 14.dp else 10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isRealTV) 8.dp else 7.dp)
                                        .background(if (isLive) netflixRed else Color.Gray, CircleShape)
                                )
                                Text(
                                    text = if (isLive) "SEDANG AKTIF PADA SEMUA PERANTI" else "TIADA SIARAN AKTIF KETIKA INI",
                                    fontSize = if (isRealTV) 12.sp else 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLive) netflixRed else Color.White.copy(alpha = 0.5f),
                                    letterSpacing = 0.3.sp
                                )
                            }

                            if (isLive) {
                                val b = activeBroadcast!!
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tajuk: ${b.title}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = if (isRealTV) 14.sp else 12.5.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Mesej: ${b.message}",
                                    fontSize = if (isRealTV) 13.sp else 11.5.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (b.timestamp > 0) {
                                    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                    Text(
                                        text = "${sdf.format(Date(b.timestamp))} • ${b.type.uppercase()}",
                                        fontSize = if (isRealTV) 11.sp else 10.sp,
                                        color = Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Title & Copy Shortcut Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Borang Siaran:",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isRealTV) 13.5.sp else 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        if (isLive && (titleInput != activeBroadcast!!.title || messageInput != activeBroadcast!!.message)) {
                            var isCopyFocused by remember { mutableStateOf(false) }
                            Text(
                                text = "Salin dari siaran semasa",
                                fontSize = if (isRealTV) 12.sp else 11.sp,
                                color = if (isCopyFocused) Color.White else netflixRed,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .onFocusChanged { isCopyFocused = it.isFocused }
                                    .border(
                                        width = if (isCopyFocused) 1.5.dp else 0.dp,
                                        color = if (isCopyFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        val b = activeBroadcast!!
                                        titleInput = b.title
                                        messageInput = b.message
                                        selectedType = b.type.lowercase().ifBlank { "info" }
                                    }
                                    .focusable()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 3. Title Input
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = {
                            Text(
                                "Tajuk Siaran (cth: Kemaskini Pelayan)",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = if (isRealTV) 13.sp else 12.sp
                            )
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = if (isRealTV) 14.sp else 12.5.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF181818),
                            focusedBorderColor = netflixRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            cursorColor = netflixRed
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 4. Message Input
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        placeholder = {
                            Text(
                                "Tulis mesej pengumuman untuk semua pengguna...",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = if (isRealTV) 13.sp else 12.sp
                            )
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = if (isRealTV) 14.sp else 12.5.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF181818),
                            focusedBorderColor = netflixRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            cursorColor = netflixRed
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = if (isRealTV) 84.dp else 72.dp,
                                max = if (isRealTV) 120.dp else 110.dp
                            )
                    )

                    // 5. Category / Type Selector (Strictly Red, White, Black)
                    Column {
                        Text(
                            text = "Kategori:",
                            fontSize = if (isRealTV) 12.sp else 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Pair("info", "PENGUMUMAN"),
                                Pair("update", "KEMASKINI"),
                                Pair("alert", "PENTING")
                            ).forEach { (typeKey, label) ->
                                val isSelected = selectedType == typeKey
                                var isChipFocused by remember { mutableStateOf(false) }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) netflixRed else Color(0xFF1C1C1C),
                                    border = BorderStroke(
                                        width = if (isChipFocused) (if (isRealTV) 2.5.dp else 2.dp) else 1.dp,
                                        color = if (isChipFocused) Color.White else if (isSelected) netflixRed else Color.White.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { isChipFocused = it.isFocused }
                                        .scale(if (isChipFocused) (if (isRealTV) 1.05f else 1.03f) else 1f)
                                        .clickable { selectedType = typeKey }
                                        .focusable()
                                    ) {
                                    Box(
                                        modifier = Modifier.padding(
                                            vertical = if (isRealTV) 9.dp else 7.dp,
                                            horizontal = 4.dp
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = if (isRealTV) 12.sp else 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                            letterSpacing = 0.4.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isRealTV) 14.dp else 12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(if (isRealTV) 14.dp else 12.dp))

                // ── FOOTER ACTIONS ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeBroadcast != null && activeBroadcast!!.isActive) {
                        var isClearFocused by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = {
                                viewModel.clearBroadcastMessage { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        titleInput = ""
                                        messageInput = ""
                                    }
                                }
                            },
                            enabled = !isSending,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isClearFocused) Color(0xFF2A1010) else Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(
                                width = if (isClearFocused) (if (isRealTV) 2.5.dp else 2.dp) else 1.dp,
                                color = if (isClearFocused) Color.White else netflixRed.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(
                                horizontal = if (isRealTV) 16.dp else 12.dp,
                                vertical = if (isRealTV) 9.dp else 6.dp
                            ),
                            modifier = Modifier
                                .onFocusChanged { isClearFocused = it.isFocused }
                                .scale(if (isClearFocused) (if (isRealTV) 1.08f else 1.05f) else 1f)
                                .focusable()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = netflixRed,
                                modifier = Modifier.size(if (isRealTV) 17.dp else 15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Padam",
                                fontSize = if (isRealTV) 13.sp else 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isRealTV) 10.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isCloseFocused by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = onDismissRequest,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isCloseFocused) Color(0xFF282828) else Color.Transparent,
                                contentColor = if (isCloseFocused) Color.White else Color.White.copy(alpha = 0.7f)
                            ),
                            border = BorderStroke(
                                width = if (isCloseFocused) (if (isRealTV) 2.5.dp else 2.dp) else 1.dp,
                                color = if (isCloseFocused) Color.White else Color.White.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(
                                horizontal = if (isRealTV) 18.dp else 12.dp,
                                vertical = if (isRealTV) 9.dp else 6.dp
                            ),
                            modifier = Modifier
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .scale(if (isCloseFocused) (if (isRealTV) 1.08f else 1.05f) else 1f)
                                .focusable()
                        ) {
                            Text(
                                text = "Tutup",
                                fontSize = if (isRealTV) 13.sp else 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCloseFocused) Color.White else Color.White.copy(alpha = 0.8f)
                            )
                        }

                        var isSendFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                if (messageInput.isBlank()) {
                                    Toast.makeText(context, "Sila masukkan mesej siaran.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.sendBroadcastMessage(
                                    title = titleInput.ifBlank { "Pengumuman Rasmi" },
                                    message = messageInput,
                                    type = selectedType
                                ) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        viewModel.fetchActiveBroadcast(forceShow = false)
                                    }
                                }
                            },
                            enabled = !isSending && messageInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSendFocused) focusedRed else netflixRed,
                                contentColor = Color.White,
                                disabledContainerColor = netflixRed.copy(alpha = 0.3f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(
                                horizontal = if (isRealTV) 22.dp else 16.dp,
                                vertical = if (isRealTV) 9.dp else 6.dp
                            ),
                            modifier = Modifier
                                .onFocusChanged { isSendFocused = it.isFocused }
                                .scale(if (isSendFocused) (if (isRealTV) 1.08f else 1.05f) else 1f)
                                .border(
                                    width = if (isSendFocused) (if (isRealTV) 2.5.dp else 2.dp) else 0.dp,
                                    color = if (isSendFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .focusable()
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(if (isRealTV) 16.dp else 14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Menghantar...",
                                    fontSize = if (isRealTV) 13.sp else 12.sp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isRealTV) 17.dp else 15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Hantar Siaran",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isRealTV) 13.sp else 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
