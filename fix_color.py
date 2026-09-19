import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("Text(text = annotatedDescription, fontSize = 14.sp)", "Text(text = annotatedDescription, color = Color.Gray, fontSize = 14.sp)")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
