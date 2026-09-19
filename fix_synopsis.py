import os

path = 'app/src/main/java/com/duta/movie/ui/VideoDetailScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('"Belum ada sinopsis untuk film ini."', 'stringResource(R.string.no_synopsis)')
content = content.replace('text = "Sinopsis"', 'text = stringResource(R.string.synopsis)')
content = content.replace('"Selengkapnya"', 'stringResource(R.string.more)')
content = content.replace('"Sembunyikan"', 'stringResource(R.string.hide)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
