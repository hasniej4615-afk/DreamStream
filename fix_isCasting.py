import os
path = 'app/src/main/java/com/duta/movie/ui/VideoPlayerScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_isCasting = "var isCasting by remember { mutableStateOf(false) }"
new_isCasting = "var isCasting by remember { mutableStateOf(castPlayer?.isCastSessionAvailable ?: false) }"
content = content.replace(old_isCasting, new_isCasting)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
