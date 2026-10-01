package com.duta.movie.ui.repo

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duta.movie.provider.diagnostic.EndpointStatus
import com.duta.movie.provider.diagnostic.EndpointTestResult
import com.duta.movie.provider.diagnostic.ProviderDiagnosticSummary
import com.duta.movie.ui.VideoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDiagnosticDialog(
    viewModel: VideoViewModel,
    isTV: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDiagnosing by viewModel.isDiagnosingEndpoints.collectAsStateWithLifecycle()
    val diagnosticResults by viewModel.diagnosticResults.collectAsStateWithLifecycle()
    val diagnosticSummaries by viewModel.diagnosticSummaries.collectAsStateWithLifecycle()
    val installedProviders by viewModel.installedProviders.collectAsStateWithLifecycle()

    val closeFocusRequester = remember { FocusRequester() }
    val runTestFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (diagnosticResults.isEmpty() && !isDiagnosing) {
            viewModel.runProviderDiagnostics()
        }
        try { runTestFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isTV) 0.90f else 0.96f)
                .fillMaxHeight(if (isTV) 0.92f else 0.95f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isTV) 2.dp else 1.dp,
                    color = if (isTV) Color.White.copy(alpha = 0.6f) else Color.DarkGray,
                    shape = RoundedCornerShape(16.dp)
                ),
            color = Color(0xFF161616),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isTV) 24.dp else 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE50914).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = Color(0xFFE50914),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Provider Mirror Diagnostics",
                                    color = Color.White,
                                    fontSize = if (isTV) 22.sp else 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFE50914)
                                ) {
                                    Text(
                                        text = "DEBUG",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Probe every IP, domain, and fallback mirror across repo providers",
                                color = Color.Gray,
                                fontSize = if (isTV) 13.sp else 11.sp
                            )
                        }
                    }

                    var isCloseFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(closeFocusRequester)
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .border(
                                width = if (isCloseFocused && isTV) 2.dp else 0.dp,
                                color = if (isCloseFocused && isTV) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Dashboard Banner
                val totalTested = diagnosticResults.size
                val activeCount = diagnosticResults.count { it.status == EndpointStatus.ACTIVE }
                val redirectCount = diagnosticResults.count { it.status == EndpointStatus.REDIRECTED }
                val deadCount = diagnosticResults.count { it.status == EndpointStatus.DEAD }
                val timeoutCount = diagnosticResults.count { it.status == EndpointStatus.TIMEOUT }
                val blockedCount = diagnosticResults.count { it.status == EndpointStatus.BLOCKED }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF222222),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isDiagnosing) "Testing endpoints in parallel..." else "Diagnostic Summary ($totalTested Endpoints Checked)",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (isTV) 15.sp else 13.sp
                            )

                            var isRunFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { viewModel.runProviderDiagnostics() },
                                enabled = !isDiagnosing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRunFocused && isTV) Color.White else Color(0xFFE50914),
                                    contentColor = if (isRunFocused && isTV) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .focusRequester(runTestFocusRequester)
                                    .onFocusChanged { isRunFocused = it.isFocused }
                                    .border(
                                        width = if (isRunFocused && isTV) 2.dp else 0.dp,
                                        color = if (isRunFocused && isTV) Color.Red else Color.Transparent,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                            ) {
                                if (isDiagnosing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Probing...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retest All", fontSize = 12.sp)
                                }
                            }
                        }

                        if (isDiagnosing) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFFE50914),
                                trackColor = Color(0xFF333333)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Metric Badges Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricPill(
                                label = "Active",
                                count = activeCount,
                                color = Color(0xFF4CAF50),
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "Redirect",
                                count = redirectCount,
                                color = Color(0xFFFFC107),
                                icon = Icons.Default.Directions,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "Dead / 404",
                                count = deadCount,
                                color = Color(0xFFF44336),
                                icon = Icons.Default.Cancel,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "Timeout",
                                count = timeoutCount,
                                color = Color(0xFFFF9800),
                                icon = Icons.Default.HourglassEmpty,
                                modifier = Modifier.weight(1f)
                            )
                            if (blockedCount > 0) {
                                MetricPill(
                                    label = "Blocked",
                                    count = blockedCount,
                                    color = Color(0xFFE91E63),
                                    icon = Icons.Default.Shield,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Results List grouped by Provider
                val groupedResults = remember(diagnosticResults, installedProviders) {
                    val map = linkedMapOf<String, MutableList<EndpointTestResult>>()
                    diagnosticResults.forEach { r ->
                        map.getOrPut(r.providerName) { mutableListOf() }.add(r)
                    }
                    map
                }

                if (groupedResults.isEmpty() && isDiagnosing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFE50914), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Probing repository endpoints & IP mirrors...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (groupedResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No endpoints tested yet. Click 'Retest All' to begin probe.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedResults.forEach { (providerName, endpoints) ->
                            item(key = providerName) {
                                ProviderEndpointGroupCard(
                                    providerName = providerName,
                                    endpoints = endpoints,
                                    isTV = isTV,
                                    onApplyPrimary = { providerId, url ->
                                        viewModel.applyEndpointAsPrimary(providerId, url) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricPill(
    label: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "$count $label",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProviderEndpointGroupCard(
    providerName: String,
    endpoints: List<EndpointTestResult>,
    isTV: Boolean,
    onApplyPrimary: (String, String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val aliveCount = endpoints.count { it.status == EndpointStatus.ACTIVE }
    val total = endpoints.size

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E1E),
        border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable to expand/collapse)
            var isHeaderFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .onFocusChanged { isHeaderFocused = it.isFocused }
                    .border(
                        width = if (isHeaderFocused && isTV) 2.dp else 0.dp,
                        color = if (isHeaderFocused && isTV) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = if (aliveCount > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = providerName,
                        color = Color.White,
                        fontSize = if (isTV) 17.sp else 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (aliveCount > 0) Color(0xFF1B5E20).copy(alpha = 0.3f) else Color(0xFFB71C1C).copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, if (aliveCount > 0) Color(0xFF4CAF50) else Color(0xFFF44336))
                    ) {
                        Text(
                            text = "$aliveCount / $total Alive",
                            color = if (aliveCount > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded Endpoints List
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF2C2C2C))
                    Spacer(modifier = Modifier.height(2.dp))

                    endpoints.sortedByDescending { it.status == EndpointStatus.ACTIVE }.forEach { endpoint ->
                        EndpointResultRow(
                            endpoint = endpoint,
                            isTV = isTV,
                            onApply = { onApplyPrimary(endpoint.providerId, endpoint.url) }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
fun EndpointResultRow(
    endpoint: EndpointTestResult,
    isTV: Boolean,
    onApply: () -> Unit
) {
    val statusColor = when (endpoint.status) {
        EndpointStatus.ACTIVE -> Color(0xFF4CAF50)
        EndpointStatus.REDIRECTED -> Color(0xFFFFC107)
        EndpointStatus.BLOCKED -> Color(0xFFE91E63)
        EndpointStatus.TIMEOUT -> Color(0xFFFF9800)
        EndpointStatus.DEAD -> Color(0xFFF44336)
    }

    val statusIcon = when (endpoint.status) {
        EndpointStatus.ACTIVE -> Icons.Default.CheckCircle
        EndpointStatus.REDIRECTED -> Icons.Default.Directions
        EndpointStatus.BLOCKED -> Icons.Default.Shield
        EndpointStatus.TIMEOUT -> Icons.Default.HourglassEmpty
        EndpointStatus.DEAD -> Icons.Default.Cancel
    }

    var isRowFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isRowFocused && isTV) Color(0xFF2C2C2C) else Color(0xFF141414),
        border = BorderStroke(
            width = if (isRowFocused && isTV) 2.dp else 1.dp,
            color = if (isRowFocused && isTV) Color.White else Color(0xFF282828)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isRowFocused = it.isFocused }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = endpoint.url,
                            color = Color.White,
                            fontSize = if (isTV) 14.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (endpoint.latencyMs > 0L) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF282828)
                            ) {
                                Text(
                                    text = "${endpoint.latencyMs}ms",
                                    color = if (endpoint.latencyMs < 500) Color(0xFF4CAF50) else Color(0xFFFFC107),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    if (endpoint.details != null) {
                        Text(
                            text = endpoint.details,
                            color = statusColor,
                            fontSize = if (isTV) 12.sp else 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Quick Apply / Set as Primary button for working endpoints
            if (endpoint.status == EndpointStatus.ACTIVE || endpoint.status == EndpointStatus.REDIRECTED) {
                var isBtnFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = onApply,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isBtnFocused && isTV) Color.White else statusColor
                    ),
                    border = BorderStroke(1.dp, if (isBtnFocused && isTV) Color.White else statusColor.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .onFocusChanged { isBtnFocused = it.isFocused }
                ) {
                    Text(
                        text = "Use Base",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
