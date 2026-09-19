import os
import re

strings = {
    'About App': 'about_app',
    'Auto Download Subtitles': 'auto_download_subtitles',
    'Changelog': 'changelog',
    'Check / Discover Domain': 'check_discover_domain',
    'Clear Cache': 'clear_cache',
    'Clear Watch History': 'clear_watch_history',
    'Enable Mobile Landscape': 'enable_mobile_landscape',
    'Join our Telegram Channel': 'join_telegram',
    'Manual Domain Update': 'manual_domain_update',
    'Restore Defaults': 'restore_defaults',
    'Show Calibration Border': 'show_calibration_border',
    'Version Info': 'version_info',
    'View App Logcat': 'view_app_logcat',
    'Add to List': 'add_to_list',
    'Allow the main app UI to rotate on mobile devices': 'allow_main_app_ui_rotate',
    'Automatically select and load preferred language': 'auto_select_load_preferred_language',
    'Back': 'back',
    'Decrease': 'decrease',
    'Displays a red border at the edges to help with alignment': 'displays_red_border_alignment',
    'Get the latest updates, announcements, and support. Tap here to join: t.me/DMXStream': 'join_telegram_desc',
    'Increase': 'increase',
    'Manually specify the primary backend URL': 'manually_specify_backend_url',
    'Picture in Picture': 'picture_in_picture',
    'Quality': 'quality',
    'Read and copy recent system logcat output': 'read_copy_logcat',
    \"Remove all videos from 'Continue Watching'\": 'remove_all_videos_continue_watching',
    'Reset all display adjustments to original values': 'reset_display_adjustments',
    'Select Server': 'select_server',
    'Subtitle Sync': 'subtitle_sync',
    'Toggle Fullscreen': 'toggle_fullscreen',
    'View the latest updates and improvements in this release.': 'view_latest_updates',
    'Voice Search': 'voice_search',
    'Watched': 'watched',
    'Your premium portal to unlimited entertainment. Enjoy a massive catalog of high-quality movies and TV series across all your Android devices, including TV.': 'about_app_desc',
    'Base URL updated manually': 'base_url_updated',
    'Copied to clipboard': 'copied_to_clipboard',
    'Current domain is up to date': 'current_domain_up_to_date',
    'Failed to discover domain': 'failed_to_discover_domain',
    'History cleared': 'history_cleared',
    'Invalid URL format': 'invalid_url_format',
    'No app can open this link.': 'no_app_can_open_link',
    'SETTINGS': 'settings_caps',
    'Loading logcat...': 'loading_logcat',
    'Searching...': 'searching'
}

def replace_in_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    
    for text, res_id in strings.items():
        if \"'\" in text:
            # handle cases where text has single quote
            content = content.replace(f'\"{text}\"', f'stringResource(R.string.{res_id})')
        else:
            content = content.replace(f'\"{text}\"', f'stringResource(R.string.{res_id})')
            
    # For ViewModel, we need context.getString
    if 'ViewModel' in filepath:
        for text, res_id in strings.items():
            content = content.replace(f'stringResource(R.string.{res_id})', f'context.getString(R.string.{res_id})')
    
    if content != original:
        # check imports
        if 'ViewModel' not in filepath and 'import androidx.compose.ui.res.stringResource' not in content:
            content = 'import androidx.compose.ui.res.stringResource\\n' + content
        if 'import com.duta.movie.R' not in content:
            content = 'import com.duta.movie.R\\n' + content
            
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f\"Updated {filepath}\")

for root, dirs, files in os.walk('app/src/main/java/com/duta/movie'):
    for file in files:
        if file.endswith('.kt'):
            replace_in_file(os.path.join(root, file))

