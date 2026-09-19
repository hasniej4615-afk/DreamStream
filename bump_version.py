import os
path = 'app/build.gradle.kts'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('versionCode = 14', 'versionCode = 15')
content = content.replace('versionName = "1.9.4"', 'versionName = "1.9.5"')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

path_settings = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path_settings, 'r', encoding='utf-8') as f:
    content_settings = f.read()
content_settings = content_settings.replace('v1.9.4', 'v1.9.5')
with open(path_settings, 'w', encoding='utf-8') as f:
    f.write(content_settings)
