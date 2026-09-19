import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Instant visual feedback for language selection
old_lang_block = '''                                item {
                                    val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                                    val currentLanguage = if (locales.isEmpty) "en" else locales.get(0)?.language ?: "en"

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        LanguageSelectionCard(
                                            title = "English",
                                            isSelected = currentLanguage == "en",
                                            onClick = {
                                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                                    androidx.core.os.LocaleListCompat.forLanguageTags("en")
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        LanguageSelectionCard(
                                            title = "Bahasa Melayu",
                                            isSelected = currentLanguage == "ms",
                                            onClick = {
                                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                                    androidx.core.os.LocaleListCompat.forLanguageTags("ms")
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }'''

new_lang_block = '''                                item {
                                    val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                                    var currentLanguage by remember { mutableStateOf(if (locales.isEmpty) "en" else locales.get(0)?.language ?: "en") }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        LanguageSelectionCard(
                                            title = "English",
                                            isSelected = currentLanguage == "en",
                                            onClick = {
                                                currentLanguage = "en"
                                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                                    androidx.core.os.LocaleListCompat.forLanguageTags("en")
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        LanguageSelectionCard(
                                            title = "Bahasa Melayu",
                                            isSelected = currentLanguage == "ms",
                                            onClick = {
                                                currentLanguage = "ms"
                                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                                    androidx.core.os.LocaleListCompat.forLanguageTags("ms")
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }'''

content = content.replace(old_lang_block, new_lang_block)


# Fix 2: Text Alignment in LanguageSelectionCard
old_card_block = '''            Text(
                text = title,
                color = if (isSelected) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 18.sp
            )'''

new_card_block = '''            Text(
                text = title,
                color = if (isSelected) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )'''

content = content.replace(old_card_block, new_card_block)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
