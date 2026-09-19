import os
path = 'app/src/main/java/com/duta/movie/ui/VideoPlayerScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if "castPlayer.setMediaItem" in line:
        for j in range(max(0, i-80), min(len(lines), i-30)):
            print(f"{j+1}: {lines[j].rstrip()}")
        break
