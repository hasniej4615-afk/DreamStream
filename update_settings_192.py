import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Update version string
content = content.replace('"DreamStream, Premium v1.9.1 Owl\'s Eye"', '"DreamStream, Premium v1.9.2 Owl\'s Eye"')

# Update Changelog Dialog
old_dialog = '''    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = { Text(stringResource(R.string.changelog_v1_9_1), color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.added_malay_translation), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_tv_scroll_barrier), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_tv_video_details_silent_fail), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))'''

new_dialog = '''    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = { Text(stringResource(R.string.changelog_v1_9_2), color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.added_dynamic_mobile_hero), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_language_highlight_lag), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_language_btn_alignment), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_1_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.added_malay_translation), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_tv_scroll_barrier), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.fixed_tv_video_details_silent_fail), color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.v1_9_updates), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))'''

if old_dialog in content:
    content = content.replace(old_dialog, new_dialog)
else:
    print("WARNING: Could not find old dialog")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
