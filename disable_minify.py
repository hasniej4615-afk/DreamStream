import os
path = 'app/build.gradle.kts'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("isMinifyEnabled = true", "isMinifyEnabled = false")
content = content.replace("isShrinkResources = true", "isShrinkResources = false")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
