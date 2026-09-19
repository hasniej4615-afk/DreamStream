import os
path = 'app/src/main/java/com/duta/movie/ui/VideoPlayerScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if "val mediaItem = MediaItem.Builder()" in line:
        for j in range(i, i+30):
            if j < len(lines):
                print(f"{j+1}: {lines[j].rstrip()}")
        break
