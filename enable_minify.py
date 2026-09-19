import os
path = 'app/build.gradle.kts'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("isMinifyEnabled = false", "isMinifyEnabled = true")
content = content.replace("isShrinkResources = false", "isShrinkResources = true")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
