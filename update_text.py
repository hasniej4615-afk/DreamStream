import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Modify SettingsActionCard signature
sig_old = '''fun SettingsActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
)'''

sig_new = '''fun SettingsActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    descriptionColor: Color = Color.Gray,
    onClick: () -> Unit
)'''

# Modify Text in SettingsActionCard
text_old = '''Text(text = description, color = Color.Gray, fontSize = 14.sp)'''
text_new = '''Text(text = description, color = descriptionColor, fontSize = 14.sp)'''

# Modify the call for Version Info
card_call_old = '''                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        description = if (isCheckingUpdate) "Checking for updates..." else "DreamStream, Premium v1.9.2 Owl's Eye\\nClick to check for updates",
                                        icon = Icons.Default.Info,
                                        onClick = {'''

card_call_new = '''                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        description = if (isCheckingUpdate) "Checking for updates..." else "Click to Check for Updates",
                                        icon = Icons.Default.Info,
                                        descriptionColor = Color.Red,
                                        onClick = {'''

content = content.replace(sig_old, sig_new)
content = content.replace(text_old, text_new)
content = content.replace(card_call_old, card_call_new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
