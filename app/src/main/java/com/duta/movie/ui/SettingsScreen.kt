package com.duta.movie.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.duta.movie.R
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duta.movie.util.CacheManager

enum class SettingsSection(val label: String, val icon: ImageVector) {
    LANGUAGE("LANGUAGE / BAHASA", Icons.Default.Translate),
    DISPLAY("DISPLAY ADJUSTMENT (TV)", Icons.Default.Tv),
    SUBTITLES("SUBTITLES", Icons.Default.ClosedCaption),
    CATEGORIES("MANAGE CATEGORIES", Icons.AutoMirrored.Filled.List),
    STORAGE("STORAGE", Icons.Default.Home),
    HELP("HELP & TIPS", Icons.AutoMirrored.Filled.Help),
    ABOUT("ABOUT", Icons.Default.Info),
    DEBUG("DEBUG", Icons.Default.Build)
}

enum class CategorySortMode {
    TAXONOMY,
    ACTIVE_FIRST,
    ALPHABETICAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: VideoViewModel
) {
    val context = LocalContext.current
    val isTV = remember { com.duta.movie.util.DeviceUtils.isTvDevice(context) }
    var selectedSection by remember { 
        mutableStateOf(
            if (isTV) 
                SettingsSection.DISPLAY 
            else 
                SettingsSection.SUBTITLES
        ) 
    }
    
    val isDebugModeEnabled by viewModel.isDebugModeEnabled.collectAsStateWithLifecycle()
    val defaultSubtitleLanguage by viewModel.defaultSubtitleLanguage.collectAsStateWithLifecycle()
    val languages = listOf("English", "Indonesian", "Malay", "Japanese", "Chinese", "Thai", "Arabic")

    var showChangelogDialog by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf(context.getString(R.string.calculating)) }

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    
    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var changePinError by remember { mutableStateOf<String?>(null) }

    var isCategoryUnlocked by remember { mutableStateOf(false) }
    var categoryPinInput by remember { mutableStateOf("") }
    var categoryPinError by remember { mutableStateOf<String?>(null) }
    

    val uiScaleFactor by viewModel.uiScaleFactor.collectAsStateWithLifecycle()
    val uiSafeAreaPadding by viewModel.uiSafeAreaPadding.collectAsStateWithLifecycle()
    val uiHeroHeightOffset by viewModel.uiHeroHeightOffset.collectAsStateWithLifecycle()
    val uiThumbnailScaleFactor by viewModel.uiThumbnailScaleFactor.collectAsStateWithLifecycle()
    var showCalibration by remember { mutableStateOf(false) }
    
    var showLogcatDialog by remember { mutableStateOf(false) }
    var logcatContent by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var manualUpdateInfo by remember { mutableStateOf<com.duta.movie.util.UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var baseUrlInput by remember { mutableStateOf("") }
    val activeBaseUrl by viewModel.activeBaseUrl.collectAsStateWithLifecycle()

    val allCategories: List<Map<String, String>> by viewModel.allCategories.collectAsStateWithLifecycle()
    val enabledPaths: Set<String> by viewModel.enabledCategoryPaths.collectAsStateWithLifecycle()
    var categorySortMode by remember { mutableStateOf(CategorySortMode.TAXONOMY) }

    LaunchedEffect(Unit) {
        cacheSize = CacheManager.getCacheSize(context)
    }

    // Dialogs (Kept from original)
    

    

    

    if (showBaseUrlDialog) {
        AlertDialog(
            onDismissRequest = { showBaseUrlDialog = false },
            title = { Text(stringResource(R.string.manual_base_url), color = Color.White) },
            text = {
                Column {
                    Text(stringResource(R.string.enter_a_new_domain_e_g_https_e), color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1A1A1A),
                            unfocusedContainerColor = Color(0xFF1A1A1A)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateBaseUrlManually(baseUrlInput)
                        showBaseUrlDialog = false
                    }
                ) {
                    Text(stringResource(R.string.update), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBaseUrlDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    if (showLogcatDialog) {
        AlertDialog(
            onDismissRequest = { showLogcatDialog = false },
            title = { Text(stringResource(R.string.app_logcat), color = Color.White) },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = logcatContent,
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText(context.getString(R.string.logcat), logcatContent)
                        clipboardManager.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.copy), color = Color.Red)
                    }
                    TextButton(onClick = { showLogcatDialog = false }) {
                        Text(stringResource(R.string.close), color = Color.Red)
                    }
                }
            },
            containerColor = Color(0xFF1A1A1A),
            textContentColor = Color.LightGray,
            modifier = Modifier.fillMaxWidth(0.9f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawWithContent {
                drawContent()
                if (showCalibration) {
                    val margin = uiSafeAreaPadding.toFloat()
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(margin, margin),
                        size = Size(size.width - (margin * 2), size.height - (margin * 2)),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isBackFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .onFocusChanged { isBackFocused = it.isFocused }
                        .scale(if (isBackFocused) 1.15f else 1f)
                        .border(
                            width = if (isBackFocused && isTV) 2.dp else 0.dp,
                            color = if (isBackFocused && isTV) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = if (isBackFocused) Color.Red else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_caps),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar
                val sidebarScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .width(90.dp)
                        .fillMaxHeight()
                        .background(Color.Black)
                        .focusGroup()
                        .verticalScroll(sidebarScrollState)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsSection.entries.forEach { section ->
                        if (section == SettingsSection.DEBUG && !isDebugModeEnabled) return@forEach
                        if (section == SettingsSection.DISPLAY && !isTV) return@forEach
                        
                        val isSelected = selectedSection == section
                        var isFocused by remember(section) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .size(70.dp, 60.dp)
                                .scale(if (isFocused) 1.08f else 1f)
                                .onFocusChanged {
                                    isFocused = it.isFocused
                                    if (it.isFocused && isTV) {
                                        selectedSection = section
                                    }
                                }
                                .background(
                                    color = if (isSelected) Color.Red else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isFocused) 3.dp else 0.dp,
                                    color = if (isFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedSection = section }
                                .focusable(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.label,
                                tint = if (isSelected || isFocused) Color.White else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Main Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = if (isTV) 24.dp else 14.dp)
                ) {
                    // Section Title with Red Accent
                    Column(modifier = Modifier.padding(bottom = 32.dp)) {
                        Text(
                            text = translateSettingsSection(selectedSection.label),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(4.dp)
                                .background(Color.Red)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        when (selectedSection) {

                            SettingsSection.LANGUAGE -> {
                                item {
                                    Text(
                                        stringResource(R.string.select_preferred_language_desc),
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                item {
                                    val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                                    var currentLanguage by remember { mutableStateOf(if (locales.isEmpty) "en" else locales.get(0)?.language ?: "en") }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        LanguageSelectionCard(
                                            title = "English",
                                            isSelected = currentLanguage == "en",
                                            onClick = {
                                                currentLanguage = "en"
                                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                                    androidx.core.os.LocaleListCompat.forLanguageTags("en")
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        LanguageSelectionCard(
                                            title = "Bahasa Melayu",
                                            isSelected = currentLanguage == "ms",
                                            onClick = {
                                                currentLanguage = "ms"
                                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                                    androidx.core.os.LocaleListCompat.forLanguageTags("ms")
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
    
                            SettingsSection.DISPLAY -> {
                                item {
                                    Text(
                                        stringResource(R.string.fine_tune_interface_desc),
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                item {
                                    DisplayAdjustmentSlider(
                                        label = stringResource(R.string.screen_edge_margin),
                                        value = uiSafeAreaPadding.toFloat(),
                                        range = 0f..100f,
                                        onValueChange = { 
                                            viewModel.setUiSafeAreaPadding(it.toInt())
                                            showCalibration = true
                                        },
                                        displayValue = "${uiSafeAreaPadding}dp"
                                    )
                                }
                                item {
                                    DisplayAdjustmentSlider(
                                        label = stringResource(R.string.headliner_height_offset),
                                        value = uiHeroHeightOffset.toFloat(),
                                        range = -100f..100f,
                                        onValueChange = { viewModel.setUiHeroHeightOffset(it.toInt()) },
                                        displayValue = if (uiHeroHeightOffset >= 0) "+${uiHeroHeightOffset}dp" else "${uiHeroHeightOffset}dp"
                                    )
                                }
                                item {
                                    val tvScope = rememberCoroutineScope()
                                    SettingsActionCard(
                                        title = "Add to Home Screen",
                                        description = "Publish DreamStream channel with movies to your Android TV home screen",
                                        icon = Icons.Default.Tv,
                                        onClick = {
                                            tvScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    val currentMovies = viewModel.latestMovies.value.ifEmpty { 
                                                        viewModel.featuredVideos.value 
                                                    }
                                                    val success = com.duta.movie.tv.TvChannelSyncWorker.syncChannelDirectly(context, currentMovies)
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        if (success) {
                                                            android.widget.Toast.makeText(context, "Channel published with movies! Check your home screen.", android.widget.Toast.LENGTH_LONG).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Channel created. Check TV home screen settings to enable DMStreaM row.", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        android.widget.Toast.makeText(context, "FAILED: ${e.javaClass.simpleName}: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                item {
                                    DisplayAdjustmentSlider(
                                        label = stringResource(R.string.overall_ui_scale),
                                        value = uiScaleFactor,
                                        range = 0.8f..1.2f,
                                        onValueChange = { viewModel.setUiScaleFactor(it) },
                                        displayValue = "%.2f x".format(uiScaleFactor)
                                    )
                                }
                                item {
                                    DisplayAdjustmentSlider(
                                        label = stringResource(R.string.thumbnail_size),
                                        value = uiThumbnailScaleFactor,
                                        range = 0.5f..1.5f,
                                        onValueChange = { viewModel.setUiThumbnailScaleFactor(it) },
                                        displayValue = "%.2f x".format(uiThumbnailScaleFactor)
                                    )
                                }
                                item {
                                    SettingsToggleCard(
                                        title = stringResource(R.string.show_calibration_border),
                                        description = stringResource(R.string.displays_red_border_alignment),
                                        checked = showCalibration,
                                        onCheckedChange = { showCalibration = it }
                                    )
                                }
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.restore_defaults),
                                        description = stringResource(R.string.reset_display_adjustments),
                                        icon = Icons.Default.Refresh,
                                        onClick = { 
                                            viewModel.resetDisplaySettings()
                                            showCalibration = false
                                        }
                                    )
                                }
                            }

                            SettingsSection.SUBTITLES -> {
                                item {
                                    val isAutoSubtitleEnabled by viewModel.isAutoSubtitleEnabled.collectAsStateWithLifecycle()
                                    SettingsToggleCard(
                                        title = stringResource(R.string.auto_download_subtitles),
                                        description = stringResource(R.string.auto_select_load_preferred_language),
                                        checked = isAutoSubtitleEnabled,
                                        onCheckedChange = { viewModel.setAutoSubtitleEnabled(it) }
                                    )
                                }
                                item {
                                    Text(
                                        stringResource(R.string.default_language),
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(languages) { language ->
                                    val isSelected = language == defaultSubtitleLanguage
                                    var isFocused by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isFocused) 2.dp else 0.dp,
                                                color = if (isFocused) Color.White else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.setDefaultSubtitleLanguage(language) }
                                            .focusable()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(selectedColor = Color.Red, unselectedColor = Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = translateLanguageName(language), color = Color.White, fontSize = 16.sp)
                                    }
                                }
                            }

                            SettingsSection.CATEGORIES -> {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            stringResource(R.string.manage_home_categories),
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.fetchHomeData(force = true) }) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Gray)
                                        }
                                    }
                                }
                                item {
                                    Text(
                                        stringResource(R.string.choose_categories_desc),
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                }

                                // Interactive Sorting Selector Chips
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CategorySortChip(
                                            label = stringResource(R.string.sort_categorized),
                                            isSelected = categorySortMode == CategorySortMode.TAXONOMY,
                                            onClick = { categorySortMode = CategorySortMode.TAXONOMY }
                                        )
                                        CategorySortChip(
                                            label = stringResource(R.string.sort_active_first),
                                            isSelected = categorySortMode == CategorySortMode.ACTIVE_FIRST,
                                            onClick = { categorySortMode = CategorySortMode.ACTIVE_FIRST }
                                        )
                                        CategorySortChip(
                                            label = stringResource(R.string.sort_alphabetical),
                                            isSelected = categorySortMode == CategorySortMode.ALPHABETICAL,
                                            onClick = { categorySortMode = CategorySortMode.ALPHABETICAL }
                                        )
                                    }
                                }

                                // Quick Bulk Management Actions
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CategoryActionPill(
                                            label = stringResource(R.string.enable_all),
                                            onClick = { viewModel.enableAllCategories() }
                                        )
                                        CategoryActionPill(
                                            label = stringResource(R.string.disable_all),
                                            onClick = { viewModel.disableAllCategories() }
                                        )
                                        CategoryActionPill(
                                            label = stringResource(R.string.reset_defaults),
                                            onClick = { viewModel.resetDefaultCategories() }
                                        )
                                    }
                                }

                                if (categorySortMode == CategorySortMode.TAXONOMY) {
                                    val grouped = allCategories.groupBy { 
                                        VideoViewModel.getCategoryGroup(it["path"] ?: "", it["name"] ?: "") 
                                    }
                                    VideoViewModel.CategoryGroup.entries.sortedBy { it.priority }.forEach { group ->
                                        val itemsInGroup = grouped[group] ?: emptyList()
                                        if (itemsInGroup.isNotEmpty()) {
                                            item(key = "hdr_${group.name}") {
                                                CategoryGroupHeader(group = group, count = itemsInGroup.size)
                                            }
                                            items(itemsInGroup, key = { it["path"] ?: it["name"] ?: "" }) { category ->
                                                val name = category["name"] ?: ""
                                                val path = category["path"] ?: ""
                                                if (name.isNotEmpty() && path.isNotEmpty()) {
                                                    SettingsToggleCard(
                                                        title = translateCategoryName(name),
                                                        description = if (enabledPaths.contains(path)) stringResource(R.string.currently_displayed) else stringResource(R.string.category_hidden),
                                                        checked = enabledPaths.contains(path),
                                                        tag = group.tag,
                                                        tagColor = getGroupTagColor(group),
                                                        onCheckedChange = { viewModel.toggleCategory(path) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val sortedList = when (categorySortMode) {
                                        CategorySortMode.ACTIVE_FIRST -> allCategories.sortedWith(
                                            compareByDescending<Map<String, String>> { enabledPaths.contains(it["path"]) }
                                                .thenBy { allCategories.indexOf(it) }
                                        )
                                        CategorySortMode.ALPHABETICAL -> allCategories.sortedBy { 
                                            (it["name"] ?: "").lowercase() 
                                        }
                                        else -> allCategories
                                    }

                                    items(sortedList, key = { it["path"] ?: it["name"] ?: "" }) { category ->
                                        val name = category["name"] ?: ""
                                        val path = category["path"] ?: ""
                                        if (name.isNotEmpty() && path.isNotEmpty()) {
                                            val group = VideoViewModel.getCategoryGroup(path, name)
                                            SettingsToggleCard(
                                                title = translateCategoryName(name),
                                                description = if (enabledPaths.contains(path)) stringResource(R.string.currently_displayed) else stringResource(R.string.category_hidden),
                                                checked = enabledPaths.contains(path),
                                                tag = group.tag,
                                                tagColor = getGroupTagColor(group),
                                                onCheckedChange = { viewModel.toggleCategory(path) }
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsSection.STORAGE -> {
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.clear_cache),
                                        description = stringResource(R.string.current_usage, cacheSize),
                                        icon = Icons.Default.Delete,
                                        onClick = { 
                                            viewModel.clearCache()
                                            cacheSize = "0 B"
                                        }
                                    )
                                }
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.clear_watch_history),
                                        description = stringResource(R.string.remove_all_videos_continue_watching),
                                        icon = Icons.Default.History,
                                        onClick = { 
                                            viewModel.clearRecentlyWatched()
                                            android.widget.Toast.makeText(context, context.getString(R.string.history_cleared), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }

                            SettingsSection.DEBUG -> {
                                item {
                                    val totalInstalls by viewModel.totalInstallCount.collectAsStateWithLifecycle()
                                    val lastKnownCount by viewModel.lastKnownInstallCount.collectAsStateWithLifecycle()
                                    val isFetching by viewModel.isFetchingInstalls.collectAsStateWithLifecycle()
                                    val isRegistered by viewModel.isDeviceInstallRegistered.collectAsStateWithLifecycle()

                                    LaunchedEffect(Unit) {
                                        viewModel.fetchTotalInstalls()
                                    }

                                    val countDisplay = when {
                                        totalInstalls != null -> java.text.NumberFormat.getIntegerInstance().format(totalInstalls)
                                        lastKnownCount > 0 -> "${java.text.NumberFormat.getIntegerInstance().format(lastKnownCount)} (cached)"
                                        isFetching -> "Fetching count..."
                                        else -> "Tap to fetch"
                                    }

                                    val regStatus = if (isRegistered) "Registered" else "Pending"

                                    SettingsActionCard(
                                        title = "Total App Installations: $countDisplay",
                                        description = "This device: $regStatus • Tap to refresh live install count from server",
                                        icon = Icons.Default.Download,
                                        onClick = {
                                            viewModel.fetchTotalInstalls()
                                        }
                                    )
                                }
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.view_app_logcat),
                                        description = stringResource(R.string.read_copy_logcat),
                                        icon = Icons.Default.Info,
                                        onClick = { 
                                            showLogcatDialog = true
                                            logcatContent = context.getString(R.string.loading_logcat)
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    val process = Runtime.getRuntime().exec("logcat -d -v threadtime -t 1500")
                                                    val output = process.inputStream.bufferedReader().use { it.readText() }
                                                    logcatContent = if (output.length > 50000) output.takeLast(50000) else output
                                                } catch (e: Exception) {
                                                    logcatContent = context.getString(R.string.error_reading_logcat, e.message ?: "")
                                                }
                                            }
                                        }
                                    )
                                }
                                item {
                                    val isMobileLandscapeEnabled by viewModel.isMobileLandscapeEnabled.collectAsStateWithLifecycle()
                                    SettingsToggleCard(
                                        title = stringResource(R.string.enable_mobile_landscape),
                                        description = stringResource(R.string.allow_main_app_ui_rotate),
                                        checked = isMobileLandscapeEnabled,
                                        onCheckedChange = { viewModel.setMobileLandscapeEnabled(it) }
                                    )
                                }
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.check_discover_domain),
                                        description = stringResource(R.string.current_url_tap_refresh, activeBaseUrl ?: stringResource(R.string.searching)),
                                        icon = Icons.Default.Refresh,
                                        onClick = { viewModel.checkConnection() }
                                    )
                                }
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.manual_domain_update),
                                        description = stringResource(R.string.manually_specify_backend_url),
                                        icon = Icons.Default.Edit,
                                        onClick = { 
                                            baseUrlInput = activeBaseUrl ?: ""
                                            showBaseUrlDialog = true 
                                        }
                                    )
                                }
                            }

                            SettingsSection.HELP -> {
                                item {
                                    Text(
                                        text = stringResource(R.string.help_and_manual_desc),
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_pakcik_title),
                                        description = stringResource(R.string.help_topic_pakcik_desc),
                                        content = stringResource(R.string.help_topic_pakcik_content),
                                        tag = "[REKOMEN]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_cast_title),
                                        description = stringResource(R.string.help_topic_cast_desc),
                                        content = stringResource(R.string.help_topic_cast_content),
                                        tag = "[CAST]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_tv_title),
                                        description = stringResource(R.string.help_topic_tv_desc),
                                        content = stringResource(R.string.help_topic_tv_content),
                                        tag = "[TV REMOTE]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_mobile_title),
                                        description = stringResource(R.string.help_topic_mobile_desc),
                                        content = stringResource(R.string.help_topic_mobile_content),
                                        tag = "[MOBILE]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_mirrors_title),
                                        description = stringResource(R.string.help_topic_mirrors_desc),
                                        content = stringResource(R.string.help_topic_mirrors_content),
                                        tag = "[STREAMING]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_display_title),
                                        description = stringResource(R.string.help_topic_display_desc),
                                        content = stringResource(R.string.help_topic_display_content),
                                        tag = "[DISPLAY]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_subtitles_title),
                                        description = stringResource(R.string.help_topic_subtitles_desc),
                                        content = stringResource(R.string.help_topic_subtitles_content),
                                        tag = "[SUBTITLES]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_categories_title),
                                        description = stringResource(R.string.help_topic_categories_desc),
                                        content = stringResource(R.string.help_topic_categories_content),
                                        tag = "[CATALOG]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_series_title),
                                        description = stringResource(R.string.help_topic_series_desc),
                                        content = stringResource(R.string.help_topic_series_content),
                                        tag = "[SERIES]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_comments_title),
                                        description = stringResource(R.string.help_topic_comments_desc),
                                        content = stringResource(R.string.help_topic_comments_content),
                                        tag = "[COMMENTS]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_ratings_title),
                                        description = stringResource(R.string.help_topic_ratings_desc),
                                        content = stringResource(R.string.help_topic_ratings_content),
                                        tag = "[RATINGS]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_updates_title),
                                        description = stringResource(R.string.help_topic_updates_desc),
                                        content = stringResource(R.string.help_topic_updates_content),
                                        tag = "[UPDATES]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_tips_title),
                                        description = stringResource(R.string.help_topic_tips_desc),
                                        content = stringResource(R.string.help_topic_tips_content),
                                        tag = "[TIPS]"
                                    )
                                }
                                item {
                                    HelpTopicCard(
                                        title = stringResource(R.string.help_topic_troubleshoot_title),
                                        description = stringResource(R.string.help_topic_troubleshoot_desc),
                                        content = stringResource(R.string.help_topic_troubleshoot_content),
                                        tag = "[REPAIR]"
                                    )
                                }
                            }

                            SettingsSection.ABOUT -> {
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.user_manual_guide),
                                        description = stringResource(R.string.view_manual_and_tips),
                                        icon = Icons.AutoMirrored.Filled.Help,
                                        onClick = { selectedSection = SettingsSection.HELP }
                                    )
                                }
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.about_app),
                                        description = stringResource(R.string.about_app_desc),
                                        icon = Icons.Default.Info,
                                        onClick = {}
                                    )
                                }
                                item {
                                    val checkingUpdatesText = stringResource(R.string.checking_for_updates)
                                    val clickToCheckText = stringResource(R.string.click_to_check_updates)
                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        annotatedDescription = androidx.compose.ui.text.buildAnnotatedString {
                                            append("DreamStream, Premium v2.0.4 Raven's Revenge\n")
                                            withStyle(style = SpanStyle(color = Color.Red)) {
                                                append(if (isCheckingUpdate) checkingUpdatesText else clickToCheckText)
                                            }
                                        },
                                        icon = Icons.Default.Info,
                                        onClick = {
                                            if (!isCheckingUpdate) {
                                                scope.launch {
                                                    isCheckingUpdate = true
                                                    val info = com.duta.movie.util.UpdateChecker.checkForUpdate()
                                                    isCheckingUpdate = false
                                                    if (info != null && info.latestVersionCode > com.duta.movie.BuildConfig.VERSION_CODE) {
                                                        manualUpdateInfo = info
                                                    } else {
                                                        android.widget.Toast.makeText(context, "App is up to date!", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.join_telegram),
                                        description = stringResource(R.string.join_telegram_desc),
                                        icon = @Suppress("DEPRECATION") Icons.Default.Send,
                                        onClick = {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/DMXStream"))
                                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, context.getString(R.string.no_app_can_open_link), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.changelog),
                                        description = stringResource(R.string.view_latest_updates),
                                        icon = Icons.AutoMirrored.Filled.List,
                                        onClick = { showChangelogDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (manualUpdateInfo != null) {
        UpdateDialog(
            updateInfo = manualUpdateInfo!!,
            onDismissRequest = { manualUpdateInfo = null }
        )
    }

    if (showChangelogDialog) {
        val changelogScrollState = rememberScrollState()
        val changelogScope = rememberCoroutineScope()
        val isTVDevice = remember { com.duta.movie.util.DeviceUtils.isTvDevice(context) }
        val changelogScrollFocusRequester = remember { FocusRequester() }
        val changelogCloseFocusRequester = remember { FocusRequester() }
        var isChangelogScrollFocused by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (isTVDevice) {
                kotlinx.coroutines.delay(100)
                try {
                    changelogScrollFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }

        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = { Text(stringResource(R.string.changelog_v2_0_4), color = Color.White) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isTVDevice) 320.dp else 450.dp)
                        .focusRequester(changelogScrollFocusRequester)
                        .onFocusChanged { isChangelogScrollFocused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        if (changelogScrollState.canScrollForward) {
                                            changelogScope.launch {
                                                val target = (changelogScrollState.value + 150).coerceIn(0, changelogScrollState.maxValue)
                                                changelogScrollState.animateScrollTo(target)
                                            }
                                            true
                                        } else {
                                            changelogCloseFocusRequester.requestFocus()
                                            true
                                        }
                                    }
                                    Key.DirectionUp -> {
                                        if (changelogScrollState.canScrollBackward) {
                                            changelogScope.launch {
                                                val target = (changelogScrollState.value - 150).coerceIn(0, changelogScrollState.maxValue)
                                                changelogScrollState.animateScrollTo(target)
                                            }
                                            true
                                        } else {
                                            true
                                        }
                                    }
                                    Key.PageDown -> {
                                        changelogScope.launch {
                                            val target = (changelogScrollState.value + 400).coerceIn(0, changelogScrollState.maxValue)
                                            changelogScrollState.animateScrollTo(target)
                                        }
                                        true
                                    }
                                    Key.PageUp -> {
                                        changelogScope.launch {
                                            val target = (changelogScrollState.value - 400).coerceIn(0, changelogScrollState.maxValue)
                                            changelogScrollState.animateScrollTo(target)
                                        }
                                        true
                                    }
                                    Key.DirectionRight, Key.Tab -> {
                                        changelogCloseFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter -> {
                                        if (changelogScrollState.canScrollForward) {
                                            changelogScope.launch {
                                                val target = (changelogScrollState.value + 250).coerceIn(0, changelogScrollState.maxValue)
                                                changelogScrollState.animateScrollTo(target)
                                            }
                                            true
                                        } else {
                                            changelogCloseFocusRequester.requestFocus()
                                            true
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .drawWithContent {
                            drawContent()
                            if (changelogScrollState.maxValue > 0) {
                                val viewHeight = size.height
                                val totalHeight = viewHeight + changelogScrollState.maxValue
                                val thumbHeight = (viewHeight * (viewHeight / totalHeight)).coerceIn(28.dp.toPx(), viewHeight)
                                val thumbOffset = (viewHeight - thumbHeight) * (changelogScrollState.value.toFloat() / changelogScrollState.maxValue.toFloat())

                                drawRoundRect(
                                    color = if (isChangelogScrollFocused) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.25f),
                                    topLeft = Offset(size.width - 4.dp.toPx(), thumbOffset),
                                    size = Size(4.dp.toPx(), thumbHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                )
                            }
                        }
                        .verticalScroll(changelogScrollState)
                        .padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.v2_0_4_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_4_highlight_1), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_4_highlight_2), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_4_highlight_3), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_4_highlight_4), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_4_highlight_5), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v2_0_3_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_3_highlight_1), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_3_highlight_2), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_3_highlight_3), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v2_0_2_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_2_highlight_1), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_2_highlight_2), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_2_highlight_3), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v2_0_1_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_1_highlight_1), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_1_highlight_2), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_1_highlight_3), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_1_highlight_4), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.v2_0_1_highlight_5), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v2_0_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.promo_highlight_1), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.promo_highlight_2), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.promo_highlight_3), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.promo_highlight_4), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.promo_highlight_5), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_7_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_multi_provider_alt_sources), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_pencuri_adaptive_domain), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_player_dead_stream_ui), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_movie_state_bleed), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.optimized_subtitle_loading), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_6_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_cast_7_bugs), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.improved_tv_buffering), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_tv_home_channel), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_5_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_locked_text_ui), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_4_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_category_pagination), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_cast_state), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_3_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_tv_dpad_seekbar), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_2_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_dynamic_mobile_hero), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_language_highlight_lag), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_language_btn_alignment), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_1_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_malay_translation), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_tv_scroll_barrier), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_tv_video_details_silent_fail), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.improved_auto_heal_engine_to_f), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed) + " " + stringResource(R.string.video_details_not_loading), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_8_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_auto_heal_migration_for_), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_a_ui_bug_where_tv_displa), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.configured_secure_release_sign), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_a_link_to_the_official_t), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showChangelogDialog = false },
                    modifier = Modifier
                        .focusRequester(changelogCloseFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                changelogScrollFocusRequester.requestFocus()
                                true
                            } else false
                        }
                ) {
                    Text(stringResource(R.string.close), color = Color(0xFFFF3333))
                }
            },
            containerColor = Color(0xFF1C1C1E),
            textContentColor = Color.LightGray
        )
    }
}

@Composable
fun SettingsToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    tag: String? = null,
    tagColor: Color = Color.Gray,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.01f else 1f)
    val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(androidx.compose.ui.platform.LocalContext.current)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { isFocused = it.isFocused }
            .background(Color(0xFF141416), RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused && isRealTV) 2.dp else 1.dp,
                color = if (isFocused && isRealTV) Color.White else Color(0xFF222228),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onCheckedChange(!checked) }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (tag != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(tagColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .border(0.8.dp, tagColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tag,
                            color = tagColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                color = if (checked) Color(0xFF888892) else Color(0xFF55555C),
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) },
            modifier = Modifier.focusable(false),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFE50914),
                uncheckedThumbColor = Color(0xFF777782),
                uncheckedTrackColor = Color(0xFF25252C)
            )
        )
    }
}

fun getGroupTagColor(group: VideoViewModel.CategoryGroup): Color {
    return when (group) {
        VideoViewModel.CategoryGroup.CORE -> Color(0xFFFF453A)      // Studio Crimson
        VideoViewModel.CategoryGroup.REGIONAL -> Color(0xFF0A84FF)  // Deep Accent Blue
        VideoViewModel.CategoryGroup.STREAMING -> Color(0xFFBF5AF2) // Studio Purple
        VideoViewModel.CategoryGroup.GENRE -> Color(0xFF30D158)     // Studio Emerald
        VideoViewModel.CategoryGroup.YEAR -> Color(0xFFFF9F0A)      // Studio Amber
        VideoViewModel.CategoryGroup.OTHER -> Color(0xFF8E8E93)     // Slate
    }
}

@Composable
fun CategoryGroupHeader(group: VideoViewModel.CategoryGroup, count: Int) {
    val title = when (group) {
        VideoViewModel.CategoryGroup.CORE -> stringResource(R.string.cat_group_core)
        VideoViewModel.CategoryGroup.REGIONAL -> stringResource(R.string.cat_group_regional)
        VideoViewModel.CategoryGroup.STREAMING -> stringResource(R.string.cat_group_streaming)
        VideoViewModel.CategoryGroup.GENRE -> stringResource(R.string.cat_group_genres)
        VideoViewModel.CategoryGroup.YEAR -> stringResource(R.string.cat_group_years)
        VideoViewModel.CategoryGroup.OTHER -> stringResource(R.string.cat_group_other)
    }
    val accentColor = getGroupTagColor(group)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical studio accent bar
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(14.dp)
                .background(accentColor, RoundedCornerShape(1.5.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title.uppercase(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .background(Color(0xFF1F1F24), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color(0xFF33333C), RoundedCornerShape(4.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                color = Color(0xFFA0A0AA),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(Color(0xFF24242A))
        )
    }
}

@Composable
fun CategorySortChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(androidx.compose.ui.platform.LocalContext.current)

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                color = if (isSelected) Color(0xFFE50914) else Color(0xFF1C1C20),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = if (isFocused && isRealTV) 2.dp else 1.dp,
                color = if (isFocused && isRealTV) Color.White else if (isSelected) Color(0xFFE50914) else Color(0xFF2E2E36),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFFB0B0B8),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun CategoryActionPill(
    label: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(androidx.compose.ui.platform.LocalContext.current)

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .background(Color(0xFF141418), RoundedCornerShape(6.dp))
            .border(
                width = if (isFocused && isRealTV) 2.dp else 1.dp,
                color = if (isFocused && isRealTV) Color.White else Color(0xFF2A2A32),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFF9E9EA8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    description: String? = null,
    annotatedDescription: androidx.compose.ui.text.AnnotatedString? = null,
    icon: ImageVector,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)
    val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(androidx.compose.ui.platform.LocalContext.current)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged { isFocused = it.isFocused }
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused && isRealTV) 2.dp else 0.dp,
                color = if (isFocused && isRealTV) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .focusable()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (annotatedDescription != null) {
                Text(text = annotatedDescription, color = Color.Gray, fontSize = 14.sp)
            } else if (description != null) {
                Text(text = description, color = Color.Gray, fontSize = 14.sp)
            }
        }
        Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun HelpTopicCard(
    title: String,
    description: String,
    content: String,
    tag: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isHeaderFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isRealTV = remember { com.duta.movie.util.DeviceUtils.isTvDevice(context) }
    val headerScale by animateFloatAsState(if (isHeaderFocused && isRealTV) 1.02f else 1f)

    if (isRealTV && isExpanded) {
        androidx.activity.compose.BackHandler {
            isExpanded = false
        }
    }

    val blocks = remember(content) {
        content.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = headerScale, scaleY = headerScale)
            .background(
                color = if (isHeaderFocused && isRealTV) Color(0xFF282836) else Color(0xFF1E1E24),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isHeaderFocused && isRealTV) 3.dp else if (isExpanded) 1.5.dp else 1.dp,
                color = if (isHeaderFocused && isRealTV) Color.White else if (isExpanded) Color.Red.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .padding(if (isRealTV) 16.dp else 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isHeaderFocused = it.isFocused }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                         keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                        isExpanded = !isExpanded
                        true
                    } else false
                }
                .clickable { isExpanded = !isExpanded }
                .focusable()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color.Red.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = tag,
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = if (isExpanded) Color.Red else if (isHeaderFocused && isRealTV) Color.White else Color(0xFF2E2E38),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (!isExpanded && isHeaderFocused && isRealTV) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(if (isExpanded) R.string.help_action_hide else R.string.help_action_read),
                            color = if (!isExpanded && isHeaderFocused && isRealTV) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = if (isRealTV) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (isRealTV) 24.sp else 21.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                color = Color(0xFFB0B0B8),
                fontSize = if (isRealTV) 13.5.sp else 12.5.sp,
                lineHeight = if (isRealTV) 19.sp else 17.5.sp
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                blocks.forEach { block ->
                    HelpBlockItem(block = block, isRealTV = isRealTV)
                }

                var isCloseBtnFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isCloseBtnFocused = it.isFocused }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCloseBtnFocused && isRealTV) Color.White else Color.Red.copy(alpha = 0.2f)
                            )
                            .border(
                                width = if (isCloseBtnFocused && isRealTV) 2.dp else 1.dp,
                                color = if (isCloseBtnFocused && isRealTV) Color.White else Color.Red,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                    (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                     keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                    isExpanded = false
                                    true
                                } else false
                            }
                            .clickable { isExpanded = false }
                            .focusable()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = if (isCloseBtnFocused && isRealTV) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.help_action_hide),
                                color = if (isCloseBtnFocused && isRealTV) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpBlockItem(
    block: String,
    isRealTV: Boolean
) {
    var isBlockFocused by remember { mutableStateOf(false) }
    val blockScale by animateFloatAsState(if (isBlockFocused && isRealTV) 1.02f else 1f)

    val colonIdx = block.indexOf(':')
    val hasLabel = colonIdx in 1..45
    val rawLabel = if (hasLabel) block.substring(0, colonIdx).trim() else ""
    val cleanLabel = rawLabel.removePrefix("•").removePrefix("-").removePrefix("*").trim()
    val detail = if (hasLabel) block.substring(colonIdx + 1).trim() else block.trim()

    val isIssue = cleanLabel.equals("Issue", ignoreCase = true) || cleanLabel.equals("Masalah", ignoreCase = true)
    val isSolution = cleanLabel.equals("Solution", ignoreCase = true) || cleanLabel.equals("Penyelesaian", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = blockScale, scaleY = blockScale)
            .onFocusChanged { isBlockFocused = it.isFocused }
            .background(
                color = if (isBlockFocused && isRealTV) Color(0xFF282838) else Color(0xFF141418),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isBlockFocused && isRealTV) 2.5.dp else 1.dp,
                color = if (isBlockFocused && isRealTV) Color.White else Color(0xFF24242E),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (hasLabel) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isIssue || isSolution) 8.dp else 6.dp)
                        .background(
                            color = when {
                                isIssue -> Color(0xFFFF5252)
                                isSolution -> Color(0xFF4CAF50)
                                else -> Color.Red
                            },
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = cleanLabel.ifEmpty { rawLabel },
                    color = when {
                        isIssue -> Color(0xFFFF6B6B)
                        isSolution -> Color(0xFF81C784)
                        else -> if (isBlockFocused && isRealTV) Color.White else Color(0xFFF0F0F5)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isRealTV) 14.5.sp else 13.5.sp
                )
            }
            Text(
                text = detail,
                color = if (isBlockFocused && isRealTV) Color.White else Color(0xFFC4C4CC),
                fontSize = if (isRealTV) 13.5.sp else 12.5.sp,
                lineHeight = if (isRealTV) 20.sp else 18.sp,
                modifier = Modifier.padding(start = 14.dp)
            )
        } else {
            Text(
                text = detail.removePrefix("•").removePrefix("-").removePrefix("*").trim(),
                color = if (isBlockFocused && isRealTV) Color.White else Color(0xFFD4D4DC),
                fontSize = if (isRealTV) 13.5.sp else 12.5.sp,
                lineHeight = if (isRealTV) 20.sp else 18.sp
            )
        }
    }
}

@Composable
fun DisplayAdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    displayValue: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(displayValue, color = Color.Red, fontWeight = FontWeight.Black)
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            val rangeSpan = range.endInclusive - range.start
            val step = if (rangeSpan <= 1f) 0.05f else 1f

            var isMinusFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .onFocusChanged { isMinusFocused = it.isFocused }
                    .border(if (isMinusFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    .background(Color.DarkGray, CircleShape)
                    .clickable { onValueChange((value - step).coerceIn(range.start, range.endInclusive)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease), tint = Color.White)
            }
            
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Red,
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .focusProperties { canFocus = false }
            )
            
            var isPlusFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .onFocusChanged { isPlusFocused = it.isFocused }
                    .border(if (isPlusFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    .background(Color.DarkGray, CircleShape)
                    .clickable { onValueChange((value + step).coerceIn(range.start, range.endInclusive)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase), tint = Color.White)
            }
        }
    }
}


@Composable
fun LanguageSelectionCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.Red.copy(alpha = 0.2f) else Color(0xFF1E1E1E)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color.Red else Color.DarkGray
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}


@Composable
fun translateSettingsSection(label: String): String {
    return when(label) {
        "LANGUAGE / BAHASA" -> stringResource(R.string.language)
        "DISPLAY ADJUSTMENT (TV)" -> stringResource(R.string.display_adjustment_tv)
        "SUBTITLES" -> stringResource(R.string.subtitles)
        "MANAGE CATEGORIES" -> stringResource(R.string.manage_home_categories)
        "STORAGE" -> stringResource(R.string.storage)
        "HELP & TIPS" -> stringResource(R.string.help_and_manual)
        "ABOUT" -> stringResource(R.string.about)
        "DEBUG" -> stringResource(R.string.debug)
        else -> label
    }
}


@Composable
fun translateLanguageName(name: String): String {
    return when (name) {
        "English" -> stringResource(R.string.lang_english)
        "Indonesian" -> stringResource(R.string.lang_indonesian)
        "Malay" -> stringResource(R.string.lang_malay)
        "Japanese" -> stringResource(R.string.lang_japanese)
        "Chinese" -> stringResource(R.string.lang_chinese)
        "Thai" -> stringResource(R.string.lang_thai)
        "Arabic" -> stringResource(R.string.lang_arabic)
        else -> name
    }
}