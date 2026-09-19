import os

path = 'app/src/main/java/com/duta/movie/ui/VideoListScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('.filter { it.posterPath.isNotBlank() || it.backdropPath.isNotBlank() }', '.filter { it.thumbnailUrl.isNotBlank() || it.backdropUrl.isNotBlank() }')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
