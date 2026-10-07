package com.duta.movie.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BroadcastAdminDialog(
    viewModel: VideoViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(max = 600.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Broadcast Message to All Users",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "DEBUG1 MODE • Admin Global Announcement Hub",
                        fontSize = 11.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { viewModel.fetchActiveBroadcast(forceShow = false) },
                    enabled = !isBroadcastLoading
                ) {
                    if (isBroadcastLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Gray)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (activeBroadcast != null && activeBroadcast!!.isActive) {
                            Color(0xFF1B5E20).copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (activeBroadcast != null && activeBroadcast!!.isActive) {
                            Color(0xFF4CAF50).copy(alpha = 0.4f)
                        } else {
                            Color.Gray.copy(alpha = 0.25f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (activeBroadcast != null && activeBroadcast!!.isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (activeBroadcast != null && activeBroadcast!!.isActive) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (activeBroadcast != null && activeBroadcast!!.isActive) {
                                    "LIVE BROADCAST ACTIVE ACROSS ALL APPS"
                                } else {
                                    "NO ACTIVE BROADCAST CURRENTLY"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeBroadcast != null && activeBroadcast!!.isActive) Color(0xFF4CAF50) else Color.Gray
                            )
                        }

                        if (activeBroadcast != null && activeBroadcast!!.isActive) {
                            val b = activeBroadcast!!
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tajuk: ${b.title}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Mesej: ${b.message}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (b.timestamp > 0) {
                                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                Text(
                                    text = "Dihantar: ${sdf.format(Date(b.timestamp))} • Jenis: ${b.type.uppercase()}",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Cipta / Kemaskini Siaran Global:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                // Title Input
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Tajuk Siaran (Title)") },
                    placeholder = { Text("Cth: Pengumuman Rasmi / Penyelenggaraan Pelayan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // Message Input
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    label = { Text("Mesej Siaran (Message for all users)") },
                    placeholder = { Text("Tulis pengumuman di sini. Mesej ini akan dipaparkan kepada SEMUA pengguna...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 160.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                // Severity / Priority Type Selector
                Column {
                    Text(
                        text = "Tahap / Kategori Siaran:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("info", "Info (Cyan)", Color(0xFF00BCD4)),
                            Triple("warning", "Penting (Oren)", Color(0xFFFFA000)),
                            Triple("alert", "Kecemasan (Merah)", Color(0xFFE53935))
                        ).forEach { (typeKey, label, color) ->
                            val isSelected = selectedType == typeKey
                            var isChipFocused by remember { mutableStateOf(false) }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = if (isSelected || isChipFocused) 2.dp else 1.dp,
                                    color = if (isSelected) color else if (isChipFocused) Color.White else Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isChipFocused = it.isFocused }
                                    .clickable { selectedType = typeKey }
                                    .focusable()
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // If active message exists, allow quick copying
                if (activeBroadcast != null && activeBroadcast!!.isActive &&
                    (titleInput != activeBroadcast!!.title || messageInput != activeBroadcast!!.message)
                ) {
                    TextButton(
                        onClick = {
                            val b = activeBroadcast!!
                            titleInput = b.title
                            messageInput = b.message
                            selectedType = b.type.lowercase().ifBlank { "info" }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Salin Dari Siaran Semasa", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
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
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .onFocusChanged { isSendFocused = it.isFocused }
                    .border(
                        width = if (isSendFocused) 2.dp else 0.dp,
                        color = if (isSendFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .focusable()
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menghantar...")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hantar ke Semua Pengguna", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Delete / Clear button if active broadcast exists
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .onFocusChanged { isClearFocused = it.isFocused }
                            .border(
                                width = if (isClearFocused) 2.dp else 0.dp,
                                color = if (isClearFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusable()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Padam Siaran", fontSize = 12.sp)
                    }
                }

                var isCloseFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .onFocusChanged { isCloseFocused = it.isFocused }
                        .border(
                            width = if (isCloseFocused) 2.dp else 0.dp,
                            color = if (isCloseFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusable()
                ) {
                    Text("Tutup")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
