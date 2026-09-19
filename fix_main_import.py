import os

path = 'app/src/main/java/com/duta/movie/MainActivity.kt'
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

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
