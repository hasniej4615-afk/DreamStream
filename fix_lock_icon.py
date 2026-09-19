import os
import re
path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Remove the lock icon logic
lock_icon_block = """                                        if (!isCategoryUnlocked) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                        }"""
content = content.replace(lock_icon_block, "")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
