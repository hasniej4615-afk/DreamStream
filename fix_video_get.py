import os
import re

path = 'app/src/main/java/com/duta/movie/ui/VideoViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace loadFullDetails block
load_full_pattern = r'fun loadFullDetails\(videoId: String\) \{[\s\S]*?finally \{\s*_isLoading\.value = false\s*\}\s*\}\s*\}'

load_full_new = '''fun loadFullDetails(videoId: String) { 
        if (_videoMetadata.value?.id != videoId) {
            _videoMetadata.value = getVideo(videoId) 
        }

        viewModelScope.launch { 
            _isLoading.value = true
            try { 
                val video = getVideo(videoId)
                val detailed = videoRepository.fetchVideoDetails(videoId)
                if (detailed != null) { 
                    _videoMetadata.value = applyMetadata(detailed)
                    updateMetadataCache(listOf(detailed), triggerBackground = false) 
                } else if (video != null) {
                    _videoMetadata.value = applyMetadata(video)
                }
            } catch (e: Exception) { 
                Log.e("VideoViewModel", "Failed to load details", e) 
            } finally { 
                _isLoading.value = false
            }
        }
    }'''

content = re.sub(load_full_pattern, load_full_new, content)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
