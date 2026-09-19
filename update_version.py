import os
path = 'app/build.gradle.kts'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('versionCode = 13', 'versionCode = 14')
content = content.replace('versionName = "1.9.3"', 'versionName = "1.9.4"')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
