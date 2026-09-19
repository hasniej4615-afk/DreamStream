\ = Get-Content "c:\Users\User\Desktop\Duta\app\src\main\java\com\duta\movie\ui\VideoViewModel.kt" -Raw

# Fix the metadataCache empty servers overwrite
\ = \ -replace "(?s)withContext\(Dispatchers\.Main\) \{ _videoMetadata\.value = video \}\s*// Owl's Eye Stability Logic", "withContext(Dispatchers.Main) { 
                    if (_videoMetadata.value?.servers.isNullOrEmpty() || video.servers.isNotEmpty()) {
                        _videoMetadata.value = video 
                    }
                }

                // Owl's Eye Stability Logic"

# Fix _currentEpisode.value not being set on fresh start
\ = \ -replace "(?s)if \(video\!\!\.isSeries == true && _currentEpisode\.value == null\) \{\s*val filteredEps = video\!\!\.episodes\.filter \{ ep ->\s*val low = ep\.name\.lowercase\(\)\s*!low\.contains\("lihat semua"\) && !low\.contains\("see all"\) && \s*!low\.contains\("episode list"\) && !low\.contains\("daftar episode"\)\s*\}\s*val currentMirrorSlug = VideoExtractor\.extractStableId\(mirrorToResolve\)\s*val matchingEp = filteredEps\.find \{ VideoExtractor\.extractStableId\(it\.url\) == currentMirrorSlug \|\| it\.url\.contains\(currentMirrorSlug\) \}\s*if \(matchingEp \!\= null\) \{\s*_currentEpisode\.value = matchingEp\s*\} else if \(filteredEps\.isNotEmpty\(\)\) \{\s*// CRITICAL: Fallback to first episode if we can't match it\s*_currentEpisode\.value = filteredEps\.first\(\)\s*\}\s*\}", "if (video!!.isSeries == true && _currentEpisode.value == null) {
                    val filteredEps = video!!.episodes.filter { ep ->
                        val low = ep.name.lowercase()
                        !low.contains("lihat semua") && !low.contains("see all") && 
                        !low.contains("episode list") && !low.contains("daftar episode")
                    }
                    val currentMirrorSlug = VideoExtractor.extractStableId(mirrorToResolve)
                    val matchingEp = filteredEps.find { VideoExtractor.extractStableId(it.url) == currentMirrorSlug || it.url.contains(currentMirrorSlug) }
                    
                    if (matchingEp != null) {
                        _currentEpisode.value = matchingEp
                    } else if (filteredEps.isNotEmpty()) {
                        // CRITICAL: Fallback to first episode if we can't match it
                        _currentEpisode.value = filteredEps.first()
                    }
                }
                
                // EXTRA BULLETPROOF: if it's still null and we have episodes, just force it
                if (video!!.isSeries == true && _currentEpisode.value == null && video!!.episodes.isNotEmpty()) {
                    _currentEpisode.value = video!!.episodes.first()
                }"

Set-Content "c:\Users\User\Desktop\Duta\app\src\main\java\com\duta\movie\ui\VideoViewModel.kt" \

\ = Get-Content "c:\Users\User\Desktop\Duta\app\src\main\java\com\duta\movie\ui\VideoPlayerScreen.kt" -Raw
\ = \ -replace "(?s)val currentIndex = if \(currentEp \!\= null\) \{\s*val slug = com\.duta\.movie\.util\.VideoExtractor\.extractStableId\(currentEp\.url\)\s*episodes\.indexOfFirst \{\s*it\.url == currentEp\.url \|\| it\.id == currentEp\.id \|\| \s*com\.duta\.movie\.util\.VideoExtractor\.extractStableId\(it\.url\) == slug \|\| \s*it\.url\.contains\(slug\)\s*\}\s*\} else -1\s*if \(currentIndex \!\= -1 && currentIndex < episodes\.size - 1\) onNextEpisodeClick else null", "val currentIndex = if (currentEp != null) {
                        val slug = com.duta.movie.util.VideoExtractor.extractStableId(currentEp.url)
                        val idx = episodes.indexOfFirst { 
                            it.url == currentEp.url || it.id == currentEp.id || 
                            com.duta.movie.util.VideoExtractor.extractStableId(it.url) == slug ||
                            it.url.contains(slug)
                        }
                        if (idx == -1 && episodes.isNotEmpty()) 0 else idx
                    } else if (episodes.isNotEmpty()) 0 else -1
                    
                    if (currentIndex != -1 && currentIndex < episodes.size - 1) onNextEpisodeClick else null"

Set-Content "c:\Users\User\Desktop\Duta\app\src\main\java\com\duta\movie\ui\VideoPlayerScreen.kt" \
