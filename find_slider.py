import os
path = 'app/src/main/java/com/duta/movie/ui/PlayerControls.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if "Slider(" in line:
        for j in range(i-5, i+20):
            if j < len(lines):
                print(f"{j+1}: {lines[j].rstrip()}")
        break
