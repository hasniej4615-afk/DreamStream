import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "Text(text = description, color = Color.Gray, fontSize = 14.sp)" in line:
        # Check if we are inside SettingsActionCard
        if "SettingsActionCard(" in "".join(lines[i-40:i]):
            lines[i] = '''            if (annotatedDescription != null) {
                Text(text = annotatedDescription, fontSize = 14.sp)
            } else if (description != null) {
                Text(text = description, color = Color.Gray, fontSize = 14.sp)
            }
'''

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
