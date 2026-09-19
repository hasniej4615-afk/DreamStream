import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("androidx.compose.ui.text.withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color.Red))", "withStyle(style = SpanStyle(color = Color.Red))")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
