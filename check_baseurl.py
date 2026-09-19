import os
path = 'app/src/main/java/com/duta/movie/util/VideoExtractor.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'fun getBaseUrl' in line:
        for j in range(i, min(len(lines), i+10)):
            print(f'{j+1}: {lines[j].rstrip()}')
        break
