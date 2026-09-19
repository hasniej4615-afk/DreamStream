import os
path = 'app/src/main/java/com/duta/movie/ui/PlayerControls.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if "onPreviewKeyEvent" in line:
        for j in range(i, i+15):
            print(f"{j+1}: {lines[j].rstrip()}")
        break
