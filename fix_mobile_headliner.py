import os

path = 'app/src/main/java/com/duta/movie/ui/VideoListScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_hero_block = '''    var focusedVideo by remember { mutableStateOf<Video?>(null) }
    var debouncedHeroVideo by remember { mutableStateOf<Video?>(null) }
    val randomHeroIndex = remember(latestMovies.size) { 
        if (latestMovies.isNotEmpty()) (0 until latestMovies.size.coerceAtMost(10)).random() else 0 
    }
    
    LaunchedEffect(focusedVideo) {
        if (focusedVideo == null) return@LaunchedEffect
        delay(if (isImmersiveMode) 200 else 1000)
        debouncedHeroVideo = focusedVideo
        if (isImmersiveMode) {
            viewModel.prefetchVideoDetails(focusedVideo!!)
        }
    }

    val featuredHeroVideo = remember(debouncedHeroVideo, featuredVideos, latestMovies, latestTVSeries, metadataTrigger) {
        val base = debouncedHeroVideo ?: run {
            if (!isRealTV && latestMovies.isNotEmpty()) {
                latestMovies.getOrNull(randomHeroIndex) ?: latestMovies.first()
            } else {
                featuredVideos.firstOrNull() ?: latestMovies.firstOrNull()
            }
        }
        base?.let { viewModel.applyMetadata(it) }
    }'''

new_hero_block = '''    var focusedVideo by remember { mutableStateOf<Video?>(null) }
    var debouncedHeroVideo by remember { mutableStateOf<Video?>(null) }
    
    var randomMobileHero by remember { mutableStateOf<Video?>(null) }
    
    LaunchedEffect(isRealTV) {
        if (!isRealTV) {
            while (true) {
                // Wait for some data to load initially
                if (randomMobileHero == null) kotlinx.coroutines.delay(1000) else kotlinx.coroutines.delay(10000)
                
                val currentCategoryVideos = viewModel.categoryVideos.value
                val pool = currentCategoryVideos.values.flatten()
                    .distinctBy { it.id }
                    .filter { it.posterPath.isNotBlank() || it.backdropPath.isNotBlank() }
                
                if (pool.isNotEmpty() && focusedVideo == null) {
                    randomMobileHero = pool.random()
                }
            }
        }
    }
    
    LaunchedEffect(focusedVideo) {
        if (focusedVideo == null) return@LaunchedEffect
        kotlinx.coroutines.delay(if (isImmersiveMode) 200L else 1000L)
        debouncedHeroVideo = focusedVideo
        if (isImmersiveMode) {
            viewModel.prefetchVideoDetails(focusedVideo!!)
        }
    }

    val featuredHeroVideo = remember(debouncedHeroVideo, featuredVideos, latestMovies, latestTVSeries, randomMobileHero, metadataTrigger) {
        val base = debouncedHeroVideo ?: run {
            if (!isRealTV && randomMobileHero != null) {
                randomMobileHero
            } else if (!isRealTV && latestMovies.isNotEmpty()) {
                latestMovies.first()
            } else {
                featuredVideos.firstOrNull() ?: latestMovies.firstOrNull()
            }
        }
        base?.let { viewModel.applyMetadata(it) }
    }'''

if old_hero_block in content:
    content = content.replace(old_hero_block, new_hero_block)
else:
    print("Could not find old hero block!")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
