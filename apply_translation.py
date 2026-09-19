import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_block = '''                                item {
                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        annotatedDescription = androidx.compose.ui.text.buildAnnotatedString {
                                            append("DreamStream, Premium v1.9.2 Owl's Eye\\n")
                                            withStyle(style = SpanStyle(color = Color.Red)) {
                                                append(if (isCheckingUpdate) "Checking for updates..." else "Click to Check for Updates")
                                            }
                                        },'''

new_block = '''                                item {
                                    val checkingUpdatesText = stringResource(R.string.checking_for_updates)
                                    val clickToCheckText = stringResource(R.string.click_to_check_updates)
                                    SettingsActionCard(
                                        title = stringResource(R.string.version_info),
                                        annotatedDescription = androidx.compose.ui.text.buildAnnotatedString {
                                            append("DreamStream, Premium v1.9.2 Owl's Eye\\n")
                                            withStyle(style = SpanStyle(color = Color.Red)) {
                                                append(if (isCheckingUpdate) checkingUpdatesText else clickToCheckText)
                                            }
                                        },'''

content = content.replace(old_block, new_block)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
