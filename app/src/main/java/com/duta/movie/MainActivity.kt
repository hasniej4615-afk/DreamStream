package com.duta.movie

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.LocalActivity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.duta.movie.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.scale
import com.duta.movie.util.UpdateChecker
import com.duta.movie.util.UpdateInfo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.animation.core.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import com.duta.movie.ui.theme.MovieTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.duta.movie.ui.VideoListScreen
import com.duta.movie.ui.VideoDetailScreen
import com.duta.movie.ui.VideoPlayerScreen
import com.duta.movie.ui.CategoryResultsScreen
import com.duta.movie.ui.ActressProfileScreen
import com.duta.movie.ui.SettingsScreen
import com.duta.movie.ui.YouTubeScreen
import com.duta.movie.ui.UpdateDialog
import com.duta.movie.ui.BroadcastAnnouncementDialog
import com.duta.movie.ui.navigation.Destination
import com.duta.movie.ui.VideoViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navDeepLink
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferenceManager: com.duta.movie.data.PreferenceManager

    @Inject
    lateinit var providerManager: com.duta.movie.provider.core.ProviderManager

    private var isPipMode = mutableStateOf(false)
    private var isPlayerActiveGlobal = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(this)
        if (isPlayerActiveGlobal && !isRealTV) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode.value = isInPictureInPictureMode
    }

    fun updatePipParams(isActive: Boolean) {
        isPlayerActiveGlobal = isActive
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(this)
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setAutoEnterEnabled(isActive && !isRealTV)
                        .build()
                    setPictureInPictureParams(params)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                providerManager.autoSyncIfNeeded()
            } catch (e: Exception) {
                Log.w("MainActivity", "Auto-sync onResume notice: ${e.message}")
            }
        }
    }

    fun enterPipMode() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                }
            }
        } catch (_: Exception) {}
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Only enable edge-to-edge for modern mobile devices, TV boxes often crash with this
        val isTVMode = com.duta.movie.util.DeviceUtils.isTvDevice(this)
        if (!isTVMode) {
            try { 
                enableEdgeToEdge() 
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } catch(_: Exception) {}
        }

        // Initialize CastContext in background after UI settles smoothly (delayed to avoid initial frame contention)
        if (!isTVMode) {
            lifecycleScope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(12000)
                try {
                    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this@MainActivity) == ConnectionResult.SUCCESS) {
                        CastContext.getSharedInstance(applicationContext, java.util.concurrent.Executors.newSingleThreadExecutor())
                    }
                } catch (e: Exception) {
                    Log.w("MainActivity", "Safe Cast init notice: ${e.message}")
                }
            }
        }
        
        // Run TV Channel Sync only on TV devices
        // (Supports certified TV, Google TV, and uncertified AOSP TV boxes with TV launchers).
        if (isTVMode) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (com.duta.movie.tv.TvChannelSyncWorker.isTvChannelSupported(this@MainActivity)) {
                        com.duta.movie.tv.TvChannelSyncWorker.syncChannelDirectly(applicationContext)
                    }
                } catch (e: Throwable) {
                    Log.w("MainActivity", "Direct TV sync error: ${e.message}")
                }
            }
        }

        // Register install anonymously on first launch (counted only once per device)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.duta.movie.util.InstallTracker.registerInstall(applicationContext, preferenceManager)
            } catch (e: Exception) {
                Log.w("MainActivity", "Install registration error: ${e.message}")
            }
        }

        // Schedule periodic background auto-sync for extensions & repositories via WorkManager on IO dispatcher
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.duta.movie.data.remote.RepoSyncWorker.schedulePeriodicSync(applicationContext)
            } catch (_: Exception) {}
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isInPip by isPipMode
            
            val context = LocalContext.current
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            
            LaunchedEffect(Unit) {
                val info = UpdateChecker.checkForUpdate()
                if (info != null && info.latestVersionCode > BuildConfig.VERSION_CODE) {
                    updateInfo = info
                }
            }

            MovieTheme {
                if (updateInfo != null) {
                    UpdateDialog(
                        updateInfo = updateInfo!!,
                        onDismissRequest = { updateInfo = null }
                    )
                }

                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                
                val videoViewModel: VideoViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    videoViewModel.resetStartupState()
                }
                val isPlayerActive by videoViewModel.isPlayerActive.collectAsStateWithLifecycle()
                val isYouTubePlaying by videoViewModel.isYouTubePlaying.collectAsStateWithLifecycle()
                val isCurrentAtYouTube = currentDestination?.hasRoute<Destination.YouTube>() == true
                val isAnyPlayerActive = isPlayerActive || (isCurrentAtYouTube && isYouTubePlaying)
                val isYouTubeFullScreen by videoViewModel.isYouTubeFullScreen.collectAsStateWithLifecycle()
                val isMobileLandscapeEnabled by videoViewModel.isMobileLandscapeEnabled.collectAsStateWithLifecycle()
                val showBroadcastDialog by videoViewModel.showBroadcastDialog.collectAsStateWithLifecycle()
                val activeBroadcast by videoViewModel.activeBroadcast.collectAsStateWithLifecycle()

                if (showBroadcastDialog && activeBroadcast != null && updateInfo == null && !isPlayerActive) {
                    BroadcastAnnouncementDialog(
                        broadcast = activeBroadcast!!,
                        onDismissRequest = { videoViewModel.dismissBroadcastDialog() }
                    )
                }
                
                val uiSafeAreaPadding by videoViewModel.uiSafeAreaPadding.collectAsStateWithLifecycle()
                val uiScaleFactor by videoViewModel.uiScaleFactor.collectAsStateWithLifecycle()

                LaunchedEffect(isAnyPlayerActive) {
                    isPlayerActiveGlobal = isAnyPlayerActive
                    updatePipParams(isAnyPlayerActive)
                }

                LaunchedEffect(isMobileLandscapeEnabled, isPlayerActive, isYouTubeFullScreen) {
                    val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(this@MainActivity)
                    if (!isRealTV && !isPlayerActive && !isYouTubeFullScreen) {
                        requestedOrientation = if (isMobileLandscapeEnabled) {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                }

                val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
                val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
                val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(this@MainActivity)
                // Mobile layout check
                val isTVLayout = isExpanded || isMedium || isRealTV

                val navItems = listOf(
                    Triple(Destination.Home, Icons.Default.Home, stringResource(R.string.home)),
                    Triple(Destination.Movies, Icons.Default.Movie, stringResource(R.string.movies)),
                    Triple(Destination.TVShows, Icons.Default.Tv, stringResource(R.string.tv_shows)),
                    Triple(Destination.YouTube(), Icons.Default.SmartDisplay, stringResource(R.string.youtube)),
                    Triple(Destination.MyList, Icons.Default.Bookmark, stringResource(R.string.my_list))
                )
                val isAtPlayer = currentDestination?.hasRoute<Destination.Player>() == true
                val showNav = !isAtPlayer && !isInPip && !isYouTubeFullScreen

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        if (showNav && !isTVLayout) { // Hide on TV and Landscape Mobile
                            NavigationBar(
                                containerColor = Color.Black.copy(alpha = 0.85f),
                                contentColor = Color.White
                            ) {
                                navItems.forEach { (route, icon, label) ->
                                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(route::class) } == true
                                    NavigationBarItem(
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label, fontSize = 10.sp) },
                                        selected = selected,
                                        onClick = {
                                            if (route == Destination.Home) {
                                                videoViewModel.fetchPakcikRekomenVideos(silent = true)
                                            }
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.Red,
                                            selectedTextColor = Color.Red,
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray,
                                            indicatorColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    CompositionLocalProvider(LocalPipMode provides isInPip) {
                        val contentFocusRequester = remember { FocusRequester() }

                        LaunchedEffect(currentDestination) {
                            if (currentDestination?.hasRoute<Destination.Home>() == true && isTVLayout) {
                                if (videoViewModel.shouldRestoreFocusOnResume) {
                                    try {
                                        contentFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        val searchTabFocusRequester = remember { FocusRequester() }
                        val homeTabFocusRequester = remember { FocusRequester() }
                        val moviesTabFocusRequester = remember { FocusRequester() }
                        val tvShowsTabFocusRequester = remember { FocusRequester() }
                        val youtubeTabFocusRequester = remember { FocusRequester() }
                        val myListTabFocusRequester = remember { FocusRequester() }
                        val settingsTabFocusRequester = remember { FocusRequester() }
                        val isSearchActive by videoViewModel.isSearchActive.collectAsStateWithLifecycle()

                        Row(modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isRealTV) uiSafeAreaPadding.dp else 0.dp)
                            .graphicsLayer {
                                scaleX = uiScaleFactor
                                scaleY = uiScaleFactor
                            }
                        ) {
                            // ── SIDEBAR (Real TV and Landscape Mobile) ────────────────
                            if (showNav && isTVLayout) {
                                var sidebarFocused by remember { mutableStateOf(false) }
                                val sidebarWidth by androidx.compose.animation.core.animateDpAsState(
                                    if (sidebarFocused) 200.dp else 72.dp,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 200,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    ),
                                    label = "sidebar_width"
                                )

                                if (isTVLayout && sidebarFocused) {
                                    androidx.activity.compose.BackHandler {
                                        sidebarFocused = false
                                        try {
                                            contentFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(sidebarWidth)
                                        .fillMaxHeight()
                                        .background(Color.Black)
                                        .onFocusChanged { sidebarFocused = it.hasFocus }
                                        .padding(vertical = 12.dp)
                                        .drawBehind {
                                            if (sidebarFocused) {
                                                drawRect(
                                                    color = Color.White.copy(alpha = 0.03f),
                                                    topLeft = Offset.Zero,
                                                    size = size
                                                )
                                            }
                                        }
                                        .focusGroup()
                                        .focusProperties {
                                            // TV STABILITY: Intercept ALL focus entering sidebar container from content
                                            enter = { direction ->
                                                if (direction == FocusDirection.Left) {
                                                    when {
                                                        isSearchActive -> searchTabFocusRequester
                                                        currentDestination?.hasRoute<Destination.Movies>() == true -> moviesTabFocusRequester
                                                        currentDestination?.hasRoute<Destination.TVShows>() == true -> tvShowsTabFocusRequester
                                                        currentDestination?.hasRoute<Destination.YouTube>() == true -> youtubeTabFocusRequester
                                                        currentDestination?.hasRoute<Destination.MyList>() == true -> myListTabFocusRequester
                                                        currentDestination?.hasRoute<Destination.Settings>() == true -> settingsTabFocusRequester
                                                        else -> homeTabFocusRequester
                                                    }
                                                } else {
                                                    FocusRequester.Cancel
                                                }
                                            }
                                            exit = { direction ->
                                                if (direction == FocusDirection.Right) {
                                                    contentFocusRequester
                                                } else {
                                                    FocusRequester.Cancel
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp, top = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                 "DM", 
                                                 color = Color.Red, 
                                                 fontWeight = FontWeight.Black, 
                                                 fontSize = 28.sp,
                                                 letterSpacing = (-1.5).sp
                                            )
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = sidebarFocused,
                                                enter = fadeIn(animationSpec = tween(300)) + androidx.compose.animation.expandHorizontally(),
                                                exit = fadeOut(animationSpec = tween(300)) + androidx.compose.animation.shrinkHorizontally()
                                            ) {
                                                Text(
                                                    "Stream", 
                                                    color = Color.White, 
                                                    fontWeight = FontWeight.Black, 
                                                    fontSize = 28.sp,
                                                    letterSpacing = (-1.5).sp
                                                )
                                            }
                                        }

                                        // Simplified TV Sidebar (OnStream Style)
                                        SidebarIcon(
                                            icon = Icons.Default.Search,
                                            label = stringResource(R.string.search),
                                            isSelected = isSearchActive,
                                            isExpanded = sidebarFocused,
                                            modifier = Modifier
                                                .focusRequester(searchTabFocusRequester)
                                                .focusProperties { up = FocusRequester.Cancel },
                                            isRealTV = isRealTV,
                                            onClick = {
                                                videoViewModel.setSearchActive(!isSearchActive)
                                                if (currentDestination?.hasRoute<Destination.Home>() == false) {
                                                    navController.navigate(Destination.Home)
                                                }
                                            }
                                        )

                                        navItems.forEach { (route, icon, label) ->
                                            val selected = currentDestination?.hierarchy?.any { it.hasRoute(route::class) } == true
                                            val itemFocusRequester = when (route) {
                                                Destination.Home -> homeTabFocusRequester
                                                Destination.Movies -> moviesTabFocusRequester
                                                Destination.TVShows -> tvShowsTabFocusRequester
                                                is Destination.YouTube -> youtubeTabFocusRequester
                                                Destination.MyList -> myListTabFocusRequester
                                                else -> null
                                            }
                                            SidebarIcon(
                                                icon = icon,
                                                label = label,
                                                isSelected = selected,
                                                isExpanded = sidebarFocused,
                                                modifier = if (itemFocusRequester != null) Modifier.focusRequester(itemFocusRequester) else Modifier,
                                                isRealTV = isRealTV,
                                                onClick = {
                                                    videoViewModel.setSearchActive(false)
                                                    videoViewModel.onSearchQueryChange("")
                                                    if (route == Destination.Home) {
                                                        videoViewModel.fetchPakcikRekomenVideos(silent = true)
                                                    }
                                                    navController.navigate(route) {
                                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            )
                                        }

                                        SidebarIcon(
                                            icon = Icons.Default.Settings,
                                            label = stringResource(R.string.settings),
                                            isSelected = currentDestination?.hasRoute<Destination.Settings>() == true,
                                            isExpanded = sidebarFocused,
                                            modifier = Modifier
                                                .focusRequester(settingsTabFocusRequester)
                                                .focusProperties { down = FocusRequester.Cancel },
                                            isRealTV = isRealTV,
                                            onClick = { navController.navigate(Destination.Settings) }
                                        )

                                        Spacer(modifier = Modifier.height(32.dp))
                                    }
                                }
                            }

                            val onAppVideoClick: (String) -> Unit = { id ->
                                if (com.duta.movie.model.LiveTvCatalog.isLiveVideo(id)) {
                                    val ch = com.duta.movie.model.LiveTvCatalog.getChannelById(id)
                                    navController.navigate(Destination.Player(id, ch?.videoUrl))
                                } else if (id.startsWith("yt_")) {
                                    val rawId = id.removePrefix("yt_")
                                    navController.navigate(Destination.YouTube(initialUrl = "https://m.youtube.com/watch?v=$rawId"))
                                } else {
                                    navController.navigate(Destination.VideoDetail(id))
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = Destination.Home,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(contentFocusRequester)
                                    .focusGroup()
                                    .focusProperties {
                                        exit = { direction ->
                                            if (direction == FocusDirection.Up || direction == FocusDirection.Down) {
                                                // Vertical navigation inside NavHost must NEVER bleed into the sidebar
                                                FocusRequester.Cancel
                                            } else if (direction == FocusDirection.Left && isTVLayout) {
                                                // When navigating left from ANY row in content, land directly on the active tab!
                                                when {
                                                    isSearchActive -> searchTabFocusRequester
                                                    currentDestination?.hasRoute<Destination.Movies>() == true -> moviesTabFocusRequester
                                                    currentDestination?.hasRoute<Destination.TVShows>() == true -> tvShowsTabFocusRequester
                                                    currentDestination?.hasRoute<Destination.YouTube>() == true -> youtubeTabFocusRequester
                                                    currentDestination?.hasRoute<Destination.MyList>() == true -> myListTabFocusRequester
                                                    currentDestination?.hasRoute<Destination.Settings>() == true -> settingsTabFocusRequester
                                                    else -> homeTabFocusRequester
                                                }
                                            } else {
                                                FocusRequester.Default
                                            }
                                        }
                                    }
                                    .padding(
                                        bottom = if (isInPip || isTVLayout) 0.dp else padding.calculateBottomPadding()
                                    ),
                                enterTransition = { fadeIn(animationSpec = tween(400)) },
                                exitTransition = { fadeOut(animationSpec = tween(400)) }
                            ) {
                                composable<Destination.Home>(
                                    deepLinks = listOf(navDeepLink<Destination.Home>(basePath = "duta://video/home"))
                                ) {
                                    VideoListScreen(
                                        viewModel = videoViewModel,
                                        windowSizeClass = windowSizeClass,
                                        onVideoClick = onAppVideoClick,
                                        onSettingsClick = { navController.navigate(Destination.Settings) }
                                    )
                                }
                                composable<Destination.Movies> {
                                    CategoryResultsScreen(
                                        categoryPath = "/movie/",
                                        categoryName = "Movies",
                                        viewModel = videoViewModel,
                                        windowSizeClass = windowSizeClass,
                                        onBackClick = { navController.popBackStack() },
                                        onVideoClick = onAppVideoClick
                                    )
                                }
                                composable<Destination.TVShows> {
                                    CategoryResultsScreen(
                                        categoryPath = "/serial-tv-terbaru/",
                                        categoryName = "TV Shows",
                                        viewModel = videoViewModel,
                                        windowSizeClass = windowSizeClass,
                                        onBackClick = { navController.popBackStack() },
                                        onVideoClick = onAppVideoClick
                                    )
                                }
                                composable<Destination.YouTube> { backStackEntry ->
                                    val dest = backStackEntry.toRoute<Destination.YouTube>()
                                    YouTubeScreen(
                                        initialUrl = dest.initialUrl,
                                        viewModel = videoViewModel,
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                                composable<Destination.MyList> {
                                    val myListVideos by videoViewModel.myListVideos.collectAsStateWithLifecycle()
                                    CategoryResultsScreen(
                                        categoryPath = "my-list",
                                        categoryName = "My List",
                                        viewModel = videoViewModel,
                                        windowSizeClass = windowSizeClass,
                                        onBackClick = { navController.popBackStack() },
                                        onVideoClick = onAppVideoClick,
                                        customVideos = myListVideos
                                    )
                                }
                                composable<Destination.VideoDetail>(
                                    deepLinks = listOf(navDeepLink<Destination.VideoDetail>(basePath = "duta://video"))
                                ) { backStackEntry ->
                                    val detail = backStackEntry.toRoute<Destination.VideoDetail>()
                                    VideoDetailScreen(
                                        videoId = detail.videoId,
                                        viewModel = videoViewModel,
                                        windowSizeClass = windowSizeClass,
                                        onBackClick = { navController.popBackStack() },
                                        onPlayClick = { vid, serverUrl -> navController.navigate(Destination.Player(vid, serverUrl)) },
                                        onActressClick = { path, name -> navController.navigate(Destination.ActressProfile(path, name)) },
                                        onSettingsClick = { navController.navigate(Destination.Settings) }
                                    )
                                }
                                composable<Destination.CategoryResults> { backStackEntry ->
                                    val results = backStackEntry.toRoute<Destination.CategoryResults>()
                                    CategoryResultsScreen(
                                        categoryPath = results.path,
                                        categoryName = results.name,
                                        viewModel = videoViewModel,
                                        windowSizeClass = windowSizeClass,
                                        onBackClick = { navController.popBackStack() },
                                        onVideoClick = onAppVideoClick
                                    )
                                }
                                composable<Destination.ActressProfile> { backStackEntry ->
                                    val actress = backStackEntry.toRoute<Destination.ActressProfile>()
                                    ActressProfileScreen(
                                        actressPath = actress.path,
                                        actressName = actress.name,
                                        viewModel = videoViewModel,
                                        windowSizeClass = windowSizeClass,
                                        onBackClick = { navController.popBackStack() },
                                        onVideoClick = onAppVideoClick
                                    )
                                }
                                composable<Destination.Player> { backStackEntry ->
                                    val player = backStackEntry.toRoute<Destination.Player>()
                                    VideoPlayerScreen(
                                        videoId = player.videoId, 
                                        serverUrl = player.serverUrl, 
                                        viewModel = videoViewModel,
                                        onBackClick = {
                                            // Automatically go back to home as requested
                                            navController.popBackStack(Destination.Home, inclusive = false)
                                        }
                                    )
                                }
                                composable<Destination.Settings> {
                                    SettingsScreen(
                                        viewModel = videoViewModel,
                                        onBackClick = { navController.popBackStack() },
                                        onNavigateToRepoManager = { navController.navigate(Destination.RepoManager) }
                                    )
                                }
                                composable<Destination.RepoManager> {
                                    com.duta.movie.ui.repo.RepoManagerScreen(
                                        viewModel = videoViewModel,
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    isRealTV: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else if (isFocused) 1.10f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)
    )

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .height(60.dp)
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused && isRealTV) Modifier.border(3.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                else Modifier
            )
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(
                if (isSelected) Color.Red.copy(alpha = 0.95f)
                else if (isFocused) Color.Transparent
                else Color.Transparent
            )
            .then(
                if (isFocused && !isSelected) Modifier.background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Red.copy(alpha = 0.4f), Color.Transparent)
                    )
                ) else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                contentDescription = label, 
                tint = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(300)) + androidx.compose.animation.expandHorizontally(),
                exit = fadeOut(animationSpec = tween(300)) + androidx.compose.animation.shrinkHorizontally()
            ) {
                Text(
                    text = label,
                    color = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp),
                    maxLines = 1
                )
            }
        }
    }
}

val LocalPipMode = staticCompositionLocalOf { false }
