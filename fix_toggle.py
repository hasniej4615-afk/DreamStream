import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "Text(text = description, color = descriptionColor, fontSize = 14.sp)" in line:
        # Check if we are inside SettingsToggleCard (which is around line 737)
        if i > 740:
            lines[i] = line.replace("descriptionColor", "Color.Gray")

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
