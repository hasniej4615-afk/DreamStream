import os
path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if "Column(modifier = Modifier.verticalScroll(rememberScrollState())) {" in line:
        new_lines.append('                    Text(stringResource(R.string.fixed_category_pagination), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))\n')
        new_lines.append('                    Text(stringResource(R.string.fixed_cast_state), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))\n')
        new_lines.append('\n')
        new_lines.append('                    Spacer(modifier = Modifier.height(12.dp))\n')
        new_lines.append('                    Text(stringResource(R.string.v1_9_3_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))\n')

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
