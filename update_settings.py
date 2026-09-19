import os
path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Update version number in the About card
content = content.replace("v1.9.3 Owl's Eye", "v1.9.4 Owl's Eye")

# Update ChangelogDialog title
content = content.replace("R.string.changelog_v1_9_3", "R.string.changelog_v1_9_4")

# Insert new v1.9.4 items and push v1.9.3 down
old_changelog_start = """                    Text(stringResource(R.string.fixed_tv_dpad_seekbar), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_2_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))"""

new_changelog_start = """                    Text(stringResource(R.string.fixed_category_pagination), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_cast_state), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_3_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_tv_dpad_seekbar), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_2_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))"""

content = content.replace(old_changelog_start, new_changelog_start)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
