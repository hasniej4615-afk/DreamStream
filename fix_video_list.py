import os

path = 'app/src/main/java/com/duta/movie/ui/VideoListScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

helper = '''
@Composable
fun translateCategoryName(name: String): String {
    return when(name) {
        "Newly Updated" -> stringResource(R.string.newly_updated)
        "Movies" -> stringResource(R.string.movies)
        "TV Series" -> stringResource(R.string.tv_series)
        "Action" -> stringResource(R.string.action)
        "Anime" -> stringResource(R.string.anime)
        "Apple TV+" -> stringResource(R.string.apple_tv)
        "Disney+" -> stringResource(R.string.disney_plus)
        "HBO" -> stringResource(R.string.hbo)
        "Horror" -> stringResource(R.string.horror)
        "Indonesia" -> stringResource(R.string.indonesia)
        "Korea" -> stringResource(R.string.korea)
        "Malaysia" -> stringResource(R.string.malaysia)
        "Netflix" -> stringResource(R.string.netflix)
        "Sci-Fi" -> stringResource(R.string.sci_fi)
        "Thailand" -> stringResource(R.string.thailand)
        else -> name
    }
}
'''
if 'fun translateCategoryName' not in content:
    content = content + '\n' + helper

content = content.replace('val name = category["name"] ?: ""', 'val name = translateCategoryName(category["name"] ?: "")')
content = content.replace('val name = cat["name"] ?: ""', 'val name = translateCategoryName(cat["name"] ?: "")')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
