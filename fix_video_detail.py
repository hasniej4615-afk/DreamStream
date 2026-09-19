import os

path = 'app/src/main/java/com/duta/movie/ui/VideoDetailScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('text = "Cast"', 'text = stringResource(R.string.cast)')
content = content.replace('Text("Cast"', 'Text(stringResource(R.string.cast)')
content = content.replace('text = "Episodes"', 'text = stringResource(R.string.episodes)')
content = content.replace('Text("Episodes"', 'Text(stringResource(R.string.episodes)')
content = content.replace('text = "Servers"', 'text = stringResource(R.string.servers)')
content = content.replace('Text("Servers"', 'Text(stringResource(R.string.servers)')
content = content.replace('text = "Trailer"', 'text = stringResource(R.string.trailer)')
content = content.replace('Text("Trailer"', 'Text(stringResource(R.string.trailer)')
content = content.replace('Text("No Trailer"', 'Text(stringResource(R.string.no_trailer)')
content = content.replace('Text("Season 1"', 'Text(stringResource(R.string.season_1)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
