import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update SettingsActionCard signature
sig_old = '''fun SettingsActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    descriptionColor: Color = Color.Gray,
    onClick: () -> Unit
)'''

sig_new = '''fun SettingsActionCard(
    title: String,
    description: String? = null,
    annotatedDescription: androidx.compose.ui.text.AnnotatedString? = null,
    icon: ImageVector,
    onClick: () -> Unit
)'''

content = content.replace(sig_old, sig_new)

# 2. Update SettingsActionCard body text
body_old = '''            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = Color.Gray, fontSize = 14.sp)'''

body_new = '''            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (annotatedDescription != null) {
                Text(text = annotatedDescription, fontSize = 14.sp, color = Color.Gray)
            } else if (description != null) {
                Text(text = description, color = Color.Gray, fontSize = 14.sp)
            }'''

content = content.replace(body_old, body_new)

# 3. Update the Version Info card usage
usage_old = '''                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        description = if (isCheckingUpdate) "Checking for updates..." else "Click to Check for Updates",
                                        icon = Icons.Default.Info,
                                        descriptionColor = Color.Red,
                                        onClick = {'''

usage_new = '''                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        annotatedDescription = androidx.compose.ui.text.buildAnnotatedString {
                                            append("DreamStream, Premium v1.9.2 Owl's Eye\\n")
                                            androidx.compose.ui.text.withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color.Red)) {
                                                append(if (isCheckingUpdate) "Checking for updates..." else "Click to Check for Updates")
                                            }
                                        },
                                        icon = Icons.Default.Info,
                                        onClick = {'''

content = content.replace(usage_old, usage_new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
