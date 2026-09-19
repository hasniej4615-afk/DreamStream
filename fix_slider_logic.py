import os
path = 'app/src/main/java/com/duta/movie/ui/PlayerControls.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "if (event.key == Key.DirectionRight || event.key == Key.DirectionLeft) {" in line:
        if "sliderPosition = (current + step).coerceIn(0f, 1f)" in lines[i+1]:
            lines[i] = "                                            if (event.key == Key.DirectionRight) {\n"

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
