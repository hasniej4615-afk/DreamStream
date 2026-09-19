import os

path = 'app/src/main/java/com/duta/movie/MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('label = "Search"', 'label = stringResource(R.string.search)')
content = content.replace('label = "Settings"', 'label = stringResource(R.string.settings)')
content = content.replace('Text("Settings",', 'Text(stringResource(R.string.settings),')
content = content.replace('Text("Search",', 'Text(stringResource(R.string.search),')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
