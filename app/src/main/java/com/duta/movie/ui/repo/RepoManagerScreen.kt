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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.data.local.InstalledRepoEntity
import com.duta.movie.provider.model.RemoteProviderManifest
import com.duta.movie.ui.VideoViewModel

enum class RepoTab(val title: String) {
    INSTALLED("Installed"),
    BROWSE("Browse Online"),
    REPOS("Repositories")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoManagerScreen(
    viewModel: VideoViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(RepoTab.INSTALLED) }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    val installedProviders by viewModel.installedProviders.collectAsStateWithLifecycle()
    val installedRepos by viewModel.installedRepos.collectAsStateWithLifecycle()
    val availableOnlineProviders by viewModel.availableOnlineProviders.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isRepoSyncing.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Extensions & Repositories",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CloudStream Provider Hub & Remote Sources",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.syncRepositories()
                            Toast.makeText(context, "Syncing repositories...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync"
                            )
                        }
                    }
                    IconButton(onClick = { showAddRepoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Repository"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                RepoTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = when (tab) {
                                    RepoTab.INSTALLED -> "${tab.title} (${installedProviders.size})"
                                    RepoTab.BROWSE -> tab.title
                                    RepoTab.REPOS -> "${tab.title} (${installedRepos.size})"
                                },
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                RepoTab.INSTALLED -> {
                    InstalledProvidersList(
                        providers = installedProviders,
                        onToggle = { id, enabled -> viewModel.toggleProvider(id, enabled) },
                        onUninstall = { id ->
                            viewModel.uninstallProvider(id)
                            Toast.makeText(context, "Provider uninstalled", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                RepoTab.BROWSE -> {
                    BrowseOnlineProvidersList(
                        onlineProviders = availableOnlineProviders,
                        installedProviders = installedProviders,
                        onInstall = { manifest ->
                            viewModel.installProvider(manifest)
                            Toast.makeText(context, "Installed ${manifest.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                RepoTab.REPOS -> {
                    RepositoriesList(
                        repos = installedRepos,
                        onAddClick = { showAddRepoDialog = true }
                    )
                }
            }
        }
    }

    if (showAddRepoDialog) {
        AddRepositoryDialog(
            onDismiss = { showAddRepoDialog = false },
            onAdd = { url ->
                viewModel.addCustomRepository(url) { success, msg ->
                    if (success) {
                        Toast.makeText(context, "Added repository: $msg", Toast.LENGTH_SHORT).show()
                        showAddRepoDialog = false
                    } else {
                        Toast.makeText(context, "Failed: $msg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
fun InstalledProvidersList(
    providers: List<InstalledProviderEntity>,
    onToggle: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit
) {
    if (providers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No providers installed.\nTap Browse Online to install sources.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(providers, key = { it.id }) { provider ->
                InstalledProviderCard(
                    provider = provider,
                    onToggle = { onToggle(provider.id, it) },
                    onUninstall = { onUninstall(provider.id) }
                )
            }
        }
    }
}

@Composable
fun InstalledProviderCard(
    provider: InstalledProviderEntity,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (provider.iconUrl.isNotBlank()) {
                AsyncImage(
                    model = provider.iconUrl,
                    contentDescription = provider.name,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "v${provider.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (provider.description.isNotBlank()) {
                    Text(
                        text = provider.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BadgeChip(text = provider.mediaType)
                    BadgeChip(text = provider.templateType)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Enable / Disable Switch
            Switch(
                checked = provider.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun BrowseOnlineProvidersList(
    onlineProviders: List<RemoteProviderManifest>,
    installedProviders: List<InstalledProviderEntity>,
    onInstall: (RemoteProviderManifest) -> Unit
) {
    val installedIds = remember(installedProviders) { installedProviders.map { it.id }.toSet() }

    if (onlineProviders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Connecting to Supabase repository...\nTap sync if no catalog appears.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(onlineProviders, key = { it.id }) { manifest ->
                val isInstalled = installedIds.contains(manifest.id)
                val installedVer = installedProviders.find { it.id == manifest.id }?.version ?: 0
                val hasUpdate = isInstalled && manifest.version > installedVer

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = manifest.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (manifest.description.isNotBlank()) {
                                Text(
                                    text = manifest.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BadgeChip(text = manifest.mediaType.name)
                                BadgeChip(text = "v${manifest.versionName}")
                            }
                        }

                        Button(
                            onClick = { onInstall(manifest) },
                            enabled = !isInstalled || hasUpdate,
                            colors = if (hasUpdate) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary) else ButtonDefaults.buttonColors()
                        ) {
                            Text(
                                text = when {
                                    hasUpdate -> "Update"
                                    isInstalled -> "Installed"
                                    else -> "Install"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RepositoriesList(
    repos: List<InstalledRepoEntity>,
    onAddClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(repos, key = { it.id }) { repo ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (repo.isOfficial) Icons.Default.Verified else Icons.Default.Source,
                        contentDescription = null,
                        tint = if (repo.isOfficial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = repo.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (repo.isOfficial) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Official",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        if (repo.description.isNotBlank()) {
                            Text(
                                text = repo.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Repository") },
        text = {
            Column {
                Text(
                    text = "Enter a CloudStream repository URL (repo.json) or Supabase repository manifest URL:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Repository URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(urlInput.trim()) },
                enabled = urlInput.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
