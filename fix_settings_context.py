import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('stringResource(R.string.copied_to_clipboard)', 'context.getString(R.string.copied_to_clipboard)')
content = content.replace('stringResource(R.string.history_cleared)', 'context.getString(R.string.history_cleared)')
content = content.replace('logcatContent = stringResource(R.string.loading_logcat)', 'logcatContent = context.getString(R.string.loading_logcat)')
content = content.replace('stringResource(R.string.no_app_can_open_link)', 'context.getString(R.string.no_app_can_open_link)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
