import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('title = name,', 'title = translateCategoryName(name),')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
