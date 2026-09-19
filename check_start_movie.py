import os
path = 'app/src/main/java/com/duta/movie/ui/VideoViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if "fun startMoviePlaybackResolution(" in line:
        for j in range(max(0, i-2), min(len(lines), i+30)):
            print(f"{j+1}: {lines[j].rstrip()}")
        break
