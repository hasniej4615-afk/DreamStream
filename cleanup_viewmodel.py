import re
import os

file_path = r'C:/Users/User/Desktop/Pencuri/app/src/main/java/com/duta/movie/ui/VideoViewModel.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

cleaned_lines = []
skip_mode = False
brace_count = 0

for line in lines:
    if 'override fun onCleared()' in line:
        skip_mode = True
        brace_count = line.count('{') - line.count('}')
        continue
    
    if skip_mode:
        brace_count += line.count('{') - line.count('}')
        if brace_count <= 0:
            skip_mode = False
        continue
    
    # Fix merged lines
    l = line
    l = l.replace('}if', '} if')
    l = l.replace('}if', '} if') # double check
    l = l.replace(')suspend', ')\\n    suspend')
    l = l.replace(')fun', ')\\n    fun')
    l = l.replace('}val', '}\\n                    val')
    l = l.replace('}finally', '}\\n            finally')
    l = l.replace('}catch', '}\\n            catch')
    l = l.replace(')updateVideoInLists', ')\\n                updateVideoInLists')
    l = l.replace('}else', '}\\n                else')
    l = l.replace(')return', ')\\n                    return')
    l = l.replace('}launch', '}\\n            launch')
    
    cleaned_lines.append(l)

# Join and final polish
content = "".join(cleaned_lines)

# Fix specific broken lines from the analysis
content = content.replace(') { headliner, _, _ ->\n        headliner?.let { applyMetadata(it) }}.flowOn', ') { headliner, _, _ ->\n        headliner?.let { applyMetadata(it) }\n    }.flowOn')
content = content.replace('metaVideofingerprintMap', 'metaVideo\\n            fingerprintMap')
content = content.replace('!it.label.contains("[Embedded]") } if (!force', '!it.label.contains("[Embedded]") }\\n\\n        if (!force')
content = content.replace('"[Embedded]") }_isSubtitleLoading.value = true', '"[Embedded]") }\\n        _isSubtitleLoading.value = true')
content = content.replace('r.url } }if (newOnes.isNotEmpty())', 'r.url } }\\n                        if (newOnes.isNotEmpty())')

# Add missing braces for try/catch blocks that got messed up
# This is a bit manual but based on the analyze_file output
content = content.replace('updateMetadataCache(results)\n                    _categoryVideos.value += (path to results)\n                    categoryPages[path] = 3\n\n            } catch', 'updateMetadataCache(results)\n                    _categoryVideos.value += (path to results)\n                    categoryPages[path] = 3\n                }\n            } catch')

# Ensure class ends with a proper onCleared
content = content.rstrip()
if content.endswith('}'):
    content = content[:-1].rstrip()
    if content.endswith('}'):
        content = content[:-1].rstrip()
    
    content += '\n\n    override fun onCleared() {\n        super.onCleared()\n        fetchJob?.cancel()\n        loadMoreJob?.cancel()\n        searchJob?.cancel()\n        backgroundDetailsJob?.cancel()\n        startupJob?.cancel()\n        resolveJob?.cancel()\n        subSearchJob?.cancel()\n        Log.d("VideoViewModel", "Cleared: All pending extraction jobs cancelled")\n    }\n}'

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
