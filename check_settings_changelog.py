import os
path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'fun ChangelogDialog' in line or 'class ChangelogDialog' in line or 'stringResource(R.string.changelog_' in line:
        for j in range(max(0, i-5), min(len(lines), i+30)):
            print(f'{j+1}: {lines[j].rstrip()}')
        break
