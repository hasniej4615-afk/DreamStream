import os

path = 'app/src/main/java/com/duta/movie/ui/VideoViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

orig_str = '''                // PRIORITY LOADING: Trigger top rows first
                PRIORITY_PATHS.forEach { path ->
                    fetchVideosForCategoryRow(path)
                }'''

new_str = '''                // FETCH ALL ENABLED ROWS: TV mode requires rows to have data to be focusable/scrollable
                val enabled = enabledCategoryPaths.value
                val fetchQueue = enabled.toList().sortedBy { path -> 
                    val i = PRIORITY_PATHS.indexOf(path)
                    if (i == -1) 999 else i 
                }
                fetchQueue.forEach { path ->
                    fetchVideosForCategoryRow(path)
                }'''

content = content.replace(orig_str, new_str)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
