import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

helper = '''
@Composable
fun translateSettingsSection(label: String): String {
    return when(label) {
        "LANGUAGE / BAHASA" -> stringResource(R.string.language)
        "DISPLAY ADJUSTMENT (TV)" -> "DISPLAY ADJUSTMENT (TV)"
        "SUBTITLES" -> stringResource(R.string.subtitles)
        "MANAGE CATEGORIES" -> stringResource(R.string.manage_home_categories)
        "STORAGE" -> "STORAGE"
        "ABOUT" -> "ABOUT"
        "DEBUG" -> "DEBUG"
        else -> label
    }
}
'''
if 'fun translateSettingsSection' not in content:
    content = content + '\n' + helper

content = content.replace('text = selectedSection.label,', 'text = translateSettingsSection(selectedSection.label),')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
