import os
path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the "Locked" description logic with a proper state description
old_desc = "description = if (isCategoryUnlocked) stringResource(R.string.currently_displayed) else stringResource(R.string.locked)"
new_desc = "description = if (enabledPaths.contains(path)) stringResource(R.string.currently_displayed) else \"Hidden\""
content = content.replace(old_desc, new_desc)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
