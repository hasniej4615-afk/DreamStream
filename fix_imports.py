import os

path = 'app/src/main/java/com/duta/movie/MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
seen = set()

for line in lines:
    if line.startswith('import '):
        if line not in seen:
            seen.add(line)
            new_lines.append(line)
    else:
        new_lines.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
