import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Update version string
content = content.replace('DreamStream, Premium v1.9.2 Owl\'s Eye', 'DreamStream, Premium v1.9.3 Owl\'s Eye')

# Update dialog title
content = content.replace('title = { Text(stringResource(R.string.changelog_v1_9_2), color = Color.White) },', 'title = { Text(stringResource(R.string.changelog_v1_9_3), color = Color.White) },')

# Insert new changelog items
old_changelog_content = '''                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.added_dynamic_mobile_hero), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_language_highlight_lag), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_language_btn_alignment), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))'''

new_changelog_content = '''                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.fixed_tv_dpad_seekbar), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_2_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_dynamic_mobile_hero), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_language_highlight_lag), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_language_btn_alignment), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))'''

content = content.replace(old_changelog_content, new_changelog_content)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
