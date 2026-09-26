package com.duta.movie.ui.repo

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.data.local.InstalledRepoEntity
import com.duta.movie.provider.core.ProviderManager
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
    var repoToDelete by remember { mutableStateOf<InstalledRepoEntity?>(null) }
    var providerToUninstall by remember { mutableStateOf<InstalledProviderEntity?>(null) }

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
                            Toast.makeText(context, "Syncing repositories...", Toast.LENGTH_SHORT).show()
                            viewModel.syncRepositories { success, msg ->
                                Toast.makeText(
                                    context,
                                    if (success) "Sync complete: $msg" else "Sync failed: $msg",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
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
                                    RepoTab.BROWSE -> "${tab.title} (${availableOnlineProviders.size})"
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
                        onRequestUninstall = { provider ->
                            providerToUninstall = provider
                        }
                    )
                }
                RepoTab.BROWSE -> {
                    BrowseOnlineProvidersList(
                        onlineProviders = availableOnlineProviders,
                        installedProviders = installedProviders,
                        onInstall = { manifest ->
                            viewModel.installProvider(manifest)
                            Toast.makeText(context, "Installing ${manifest.displayName}...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                RepoTab.REPOS -> {
                    RepositoriesList(
                        repos = installedRepos,
                        onAddClick = { showAddRepoDialog = true },
                        onRequestDelete = { repo ->
                            repoToDelete = repo
                        }
                    )
                }
            }
        }
    }

    if (showAddRepoDialog) {
        AddRepositoryDialog(
            onDismiss = { showAddRepoDialog = false },
            onAdd = { url ->
                showAddRepoDialog = false
                Toast.makeText(context, "Adding repository and fetching plugins...", Toast.LENGTH_SHORT).show()
                viewModel.addCustomRepository(url) { success, msg ->
                    if (success) {
                        Toast.makeText(context, "Added repository: $msg", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed: $msg", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Delete Repository Confirmation Dialog
    if (repoToDelete != null) {
        val targetRepo = repoToDelete!!
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Repository?") },
            text = {
                Text(
                    "Are you sure you want to remove \"${targetRepo.name}\"?\n\nAll extensions installed from this repository will also be uninstalled and removed."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val repoId = targetRepo.id
                        repoToDelete = null
                        viewModel.deleteRepository(repoId) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Repository")
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Uninstall Provider Confirmation Dialog
    if (providerToUninstall != null) {
        val targetProvider = providerToUninstall!!
        AlertDialog(
            onDismissRequest = { providerToUninstall = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Uninstall Extension?") },
            text = {
                Text("Are you sure you want to uninstall \"${targetProvider.displayName}\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val provId = targetProvider.id
                        providerToUninstall = null
                        viewModel.uninstallProvider(provId) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { providerToUninstall = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun InstalledProvidersList(
    providers: List<InstalledProviderEntity>,
    onToggle: (String, Boolean) -> Unit,
    onRequestUninstall: (InstalledProviderEntity) -> Unit
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
                    onUninstall = { onRequestUninstall(provider) }
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
    val isOfficial = provider.repoId == ProviderManager.OFFICIAL_REPO_ID

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
                    if (isOfficial) {
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
                    BadgeChip(text = provider.engineType)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Controls: Switch and Delete button (or protected lock)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggle
                )
                if (!isOfficial) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onUninstall,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Uninstall"
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Core Built-in Provider",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
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
                text = "Connecting to repository manifests...\nTap sync icon in top bar to refresh catalog.",
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
                        // Icon
                        if (manifest.iconUrl.isNotBlank()) {
                            AsyncImage(
                                model = manifest.iconUrl,
                                contentDescription = manifest.name,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
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
                            Spacer(modifier = Modifier.width(12.dp))
                        }

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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BadgeChip(text = manifest.mediaType.name)
                                BadgeChip(text = "v${manifest.versionName}")
                                if (manifest.engineType.name.isNotBlank()) {
                                    BadgeChip(text = manifest.engineType.name)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

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
    onAddClick: () -> Unit,
    onRequestDelete: (InstalledRepoEntity) -> Unit
) {
    if (repos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No repositories installed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onAddClick) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Repository")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(repos, key = { it.id }) { repo ->
                val isOfficial = repo.isOfficial || repo.id == ProviderManager.OFFICIAL_REPO_ID

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
                            imageVector = if (isOfficial) Icons.Default.Verified else Icons.Default.Source,
                            contentDescription = null,
                            tint = if (isOfficial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
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
                                if (isOfficial) {
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
                            if (repo.url.isNotBlank()) {
                                Text(
                                    text = repo.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!isOfficial) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { onRequestDelete(repo) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Repository"
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Protected Official Repository",
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
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
    val quickSuggestions = remember {
        listOf(
            "cspr" to "Official CloudStream",
            "megarepo" to "MegaRepo (Multi-source)",
            "indostream" to "IndoStream (Malay / Indo)",
            "storm" to "Storm Extensions",
            "phisher" to "SuperStream (Phisher)"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Repository") },
        text = {
            Column {
                Text(
                    text = "Enter a CloudStream shortcode, GitHub repository URL, or raw repo.json URL:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Repository URL or Shortcode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickSuggestions.forEach { (code, desc) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { urlInput = code },
                            shape = RoundedCornerShape(8.dp),
                            color = if (urlInput == code) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(urlInput.trim()) },
                enabled = urlInput.isNotBlank()
            ) {
                Text("Add & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
