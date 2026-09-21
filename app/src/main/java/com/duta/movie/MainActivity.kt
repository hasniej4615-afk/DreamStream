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
import com.duta.movie.ui.UpdateDialog
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

    private var isPipMode = mutableStateOf(false)
    private var isPlayerActiveGlobal = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActiveGlobal) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode.value = isInPictureInPictureMode
    }

    fun enterPipMode() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        } catch (_: Exception) {}
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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

        // Initialize CastContext on Main thread (required by Cast SDK), using async executor for heavy work
        if (!isTVMode) {
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this@MainActivity) == ConnectionResult.SUCCESS) {
                        CastContext.getSharedInstance(applicationContext, java.util.concurrent.Executors.newSingleThreadExecutor())
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Safe Cast check error", e)
                }
            }
        }
        
        // Run TV Channel Sync unconditionally
        // (Supports certified TV, Google TV, and uncertified AOSP TV boxes with TV launchers).
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (com.duta.movie.tv.TvChannelSyncWorker.isTvChannelSupported(this@MainActivity)) {
                    com.duta.movie.tv.TvChannelSyncWorker.syncChannelDirectly(applicationContext)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Direct TV sync error: ${e.message}")
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
        try {
            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.duta.movie.tv.TvChannelSyncWorker>().build()
            androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                "TvChannelSync", 
                androidx.work.ExistingWorkPolicy.REPLACE, 
                syncRequest
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to enqueue TV sync", e)
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
                val isPlayerActive by videoViewModel.isPlayerActive.collectAsStateWithLifecycle()
                val isMobileLandscapeEnabled by videoViewModel.isMobileLandscapeEnabled.collectAsStateWithLifecycle()
                
                val uiSafeAreaPadding by videoViewModel.uiSafeAreaPadding.collectAsStateWithLifecycle()
                val uiScaleFactor by videoViewModel.uiScaleFactor.collectAsStateWithLifecycle()

                LaunchedEffect(isPlayerActive) {
                    isPlayerActiveGlobal = isPlayerActive
                }

                LaunchedEffect(isMobileLandscapeEnabled, isPlayerActive) {
                    val isRealTV = com.duta.movie.util.DeviceUtils.isTvDevice(this@MainActivity)
                    if (!isRealTV && !isPlayerActive) {
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
                    Triple(Destination.MyList, Icons.Default.Bookmark, stringResource(R.string.my_list))
                )
                val isAtPlayer = currentDestination?.hasRoute<Destination.Player>() == true
                val showNav = !isAtPlayer && !isInPip

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
                        Row(modifier = Modifier
                            .fillMaxSize()
                            .padding(uiSafeAreaPadding.dp)
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
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                    ),
                                    label = "sidebar_width"
                                )

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
                                        },
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .focusGroup()
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState()),
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
                                        val isSearchActive by videoViewModel.isSearchActive.collectAsStateWithLifecycle()
                                        
                                        SidebarIcon(
                                            icon = Icons.Default.Search,
                                            label = stringResource(R.string.search),
                                            isSelected = isSearchActive,
                                            isExpanded = sidebarFocused,
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
                                            SidebarIcon(
                                                icon = icon,
                                                label = label,
                                                isSelected = selected,
                                                isExpanded = sidebarFocused,
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
                                            isRealTV = isRealTV,
                                            onClick = { navController.navigate(Destination.Settings) }
                                        )

                                        Spacer(modifier = Modifier.height(32.dp))
                                    }
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = Destination.Home,
                                modifier = Modifier
                                    .weight(1f)
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
                                        onVideoClick = { navController.navigate(Destination.VideoDetail(it)) },
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
                                        onVideoClick = { navController.navigate(Destination.VideoDetail(it)) }
                                    )
                                }
                                composable<Destination.TVShows> {
                                    CategoryResultsScreen(
                                        categoryPath = "/serial-tv-terbaru/",
                                        categoryName = "TV Shows",
                                        viewModel = videoViewModel,
                                        windowSizeClass = windowSizeClass,
                                        onBackClick = { navController.popBackStack() },
                                        onVideoClick = { navController.navigate(Destination.VideoDetail(it)) }
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
                                        onVideoClick = { navController.navigate(Destination.VideoDetail(it)) },
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
                                        onVideoClick = { navController.navigate(Destination.VideoDetail(it)) }
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
                                        onVideoClick = { navController.navigate(Destination.VideoDetail(it)) }
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
                                    SettingsScreen(viewModel = videoViewModel, onBackClick = { navController.popBackStack() })
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
    isRealTV: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else if (isFocused) 1.12f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .height(60.dp)
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
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
