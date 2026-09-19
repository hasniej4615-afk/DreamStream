import os
import re

ui_dir = 'app/src/main/java/com/duta/movie/ui/'

for root, _, files in os.walk(ui_dir):
    for file in files:
        if file.endswith('.kt'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
            
            new_lines = []
            seen_r = False
            seen_str = False
            for line in lines:
                if 'import com.duta.movie.R' in line:
                    if seen_r: continue
                    seen_r = True
                if 'import androidx.compose.ui.res.stringResource' in line:
                    if seen_str: continue
                    seen_str = True
                new_lines.append(line)
                
            content = ''.join(new_lines)
            
            # Fix stringResource inside ClipData.newPlainText
            content = content.replace('ClipData.newPlainText(stringResource(R.string.logcat)', 'ClipData.newPlainText(context.getString(R.string.logcat)')
            
            # Fix SettingsScreen broken string
            content = content.replace('Text(stringResource(R.string.fixed)Video Details Not Loading\" issue for specific legacy titles.",', 'Text(" Fixed \\"Video Details Not Loading\\" issue for specific legacy titles.",')

            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
