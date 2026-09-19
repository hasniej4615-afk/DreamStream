import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Fix SettingsToggleCard (around line 737)
for i, line in enumerate(lines):
    if "if (annotatedDescription != null) {" in line:
        if i > 740:  # Inside SettingsToggleCard
            lines[i] = ""
            lines[i+1] = ""
            lines[i+2] = ""
            lines[i+3] = "            Text(text = description, color = Color.Gray, fontSize = 14.sp)\n"
            lines[i+4] = ""

# Add imports for AnnotatedString
import_statements = '''import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
'''
if "import androidx.compose.ui.text.buildAnnotatedString" not in "".join(lines):
    # insert at line 30
    lines.insert(30, import_statements)

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
