import os
import re

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_block = '''                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        description = if (isCheckingUpdate) "Checking for updates..." else "DreamStream, Premium v1.9.2 Owl's Eye\\\\nClick to check for updates",
                                        icon = Icons.Default.Info,
                                        onClick = {'''

new_block = '''                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        description = if (isCheckingUpdate) "Checking for updates..." else "Click to Check for Updates",
                                        icon = Icons.Default.Info,
                                        descriptionColor = Color.Red,
                                        onClick = {'''

content = content.replace(old_block, new_block)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
