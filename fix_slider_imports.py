import os
path = 'app/src/main/java/com/duta/movie/ui/PlayerControls.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

imports_to_add = [
    "import androidx.compose.ui.input.key.onPreviewKeyEvent\n",
    "import androidx.compose.ui.input.key.Key\n",
    "import androidx.compose.ui.input.key.KeyEventType\n",
    "import androidx.compose.ui.input.key.key\n",
    "import androidx.compose.ui.input.key.type\n"
]

# Find last import
last_import_idx = -1
for i, line in enumerate(lines):
    if line.startswith("import "):
        last_import_idx = i

for imp in imports_to_add:
    if imp not in "".join(lines):
        lines.insert(last_import_idx + 1, imp)

# Replace the usage in Slider modifier to not use fully qualified names since we imported them
for i, line in enumerate(lines):
    if "if (event.key == androidx.compose.ui.input.key.Key.DirectionRight" in line:
        lines[i] = "                                    if (event.key == Key.DirectionRight || event.key == Key.DirectionLeft) {\n"
    elif "if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown)" in line:
        lines[i] = "                                        if (event.type == KeyEventType.KeyDown) {\n"
    elif "if (event.key == androidx.compose.ui.input.key.Key.DirectionRight) {" in line:
        lines[i] = "                                            if (event.key == Key.DirectionRight) {\n"

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
