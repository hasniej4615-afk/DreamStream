import os

path = 'app/src/main/java/com/duta/movie/ui/ActressProfileScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('contentDescription = "Search"', 'contentDescription = stringResource(R.string.search)')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
    
path = 'app/src/main/java/com/duta/movie/ui/CategoryResultsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('contentDescription = "Search"', 'contentDescription = stringResource(R.string.search)')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
    
path = 'app/src/main/java/com/duta/movie/ui/PlayerControls.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('contentDescription = "Subtitles"', 'contentDescription = stringResource(R.string.subtitles)')
content = content.replace('contentDescription = "Episodes"', 'contentDescription = stringResource(R.string.episodes)')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
    
path = 'app/src/main/java/com/duta/movie/ui/VideoDetailScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('contentDescription = "My List"', 'contentDescription = stringResource(R.string.my_list)')
content = content.replace('contentDescription = "Settings"', 'contentDescription = stringResource(R.string.settings)')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
