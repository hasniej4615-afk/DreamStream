import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add LANGUAGE to enum
if 'LANGUAGE("LANGUAGE' not in content:
    content = content.replace('DISPLAY("DISPLAY ADJUSTMENT (TV)", Icons.Default.Tv),', 'LANGUAGE("LANGUAGE / BAHASA", Icons.Default.Translate),\n    DISPLAY("DISPLAY ADJUSTMENT (TV)", Icons.Default.Tv),')

# Add icon import
if 'import androidx.compose.material.icons.filled.Translate' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.*', 'import androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.filled.Translate')

# Add the UI for LANGUAGE
if 'SettingsSection.LANGUAGE ->' not in content:
    ui_code = '''
                            SettingsSection.LANGUAGE -> {
                                item {
                                    Text(
                                        "Select your preferred language / Pilih bahasa pilihan anda",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                item {
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
                                }
                            }
    '''
    content = content.replace('when (selectedSection) {\n                            \n\n                            SettingsSection.DISPLAY -> {', 'when (selectedSection) {\n' + ui_code + '\n                            SettingsSection.DISPLAY -> {')

# Add LanguageSelectionCard composable
if 'fun LanguageSelectionCard' not in content:
    card_code = '''
@Composable
fun LanguageSelectionCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.Red.copy(alpha = 0.2f) else Color(0xFF1E1E1E)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color.Red else Color.DarkGray
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 18.sp
            )
        }
    }
}
'''
    content = content + '\n' + card_code

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
