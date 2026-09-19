import os
path = 'app/src/main/java/com/duta/movie/ui/VideoViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'private suspend fun fetchVideos' in line or 'suspend fun fetchVideos' in line:
        for j in range(i, min(len(lines), i+20)):
            print(f'{j+1}: {lines[j].rstrip()}')
        break
