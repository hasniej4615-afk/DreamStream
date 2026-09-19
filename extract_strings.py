import os
import re

ui_dir = 'app/src/main/java/com/duta/movie/ui/'
strings = {}
counter = 1

def slugify(text):
    text = text.lower()
    text = re.sub(r'[^a-z0-9]', '_', text)
    text = re.sub(r'_+', '_', text)
    return text.strip('_')

for root, _, files in os.walk(ui_dir):
    for file in files:
        if file.endswith('.kt'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Find Text("...") patterns that don't contain $ (variables)
            # and only contain basic punctuation/letters
            matches = re.finditer(r'Text\(\s*"([^"\$]+?)"', content)
            
            new_content = content
            for match in matches:
                text_val = match.group(1).strip()
                if not text_val or len(text_val) < 2 or text_val.isdigit(): continue
                
                key = slugify(text_val)
                if not key: continue
                if len(key) > 30: key = key[:30]
                
                if key not in strings:
                    strings[key] = text_val
                
                # Replace in content
                # We need to replace exactly this instance
                original = f'Text("{match.group(1)}"'
                replacement = f'Text(stringResource(R.string.{key})'
                new_content = new_content.replace(original, replacement)
            
            if new_content != content:
                # Add import if missing
                if 'androidx.compose.ui.res.stringResource' not in new_content:
                    new_content = new_content.replace('import androidx.compose.runtime.*', 'import androidx.compose.runtime.*\nimport androidx.compose.ui.res.stringResource\nimport com.duta.movie.R')
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(new_content)

print(f"Extracted {len(strings)} strings")
for k,v in strings.items():
    print(f"{k}: {v}")
