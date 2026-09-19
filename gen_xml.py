import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

# Strings extracted from previous step
strings_dict = {
    'videos': 'Videos',
    'retry': 'Retry',
    'next': 'Next',
    'video_quality': 'Video Quality',
    'auto': 'Auto',
    'close': 'Close',
    'manual_base_url': 'Manual Base URL',
    'enter_a_new_domain_e_g_https_e': 'Enter a new domain (e.g. https://example.com). This will override the current Base URL.',
    'update': 'Update',
    'cancel': 'Cancel',
    'app_logcat': 'App Logcat',
    'logcat': 'Logcat',
    'copy': 'Copy',
    'fine_tune_the_interface_to_fit': 'Fine-tune the interface to fit your TV screen perfectly.',
    'default_language': 'Default Language',
    'manage_home_categories': 'Manage Home Categories',
    'choose_which_categories_to_dis': 'Choose which categories to display on the Home screen.',
    'changelog_v1_9': 'Changelog v1.9',
    'improved_auto_heal_engine_to_f': 'Improved Auto-Heal engine to flawlessly migrate URLs.',
    'fixed': 'Fixed',
    'v1_8_updates': 'v1.8 Updates:',
    'added_auto_heal_migration_for_': 'Added auto-heal migration for legacy video domains.',
    'fixed_a_ui_bug_where_tv_displa': 'Fixed a UI bug where TV display adjustments were showing on mobile devices.',
    'configured_secure_release_sign': 'Configured secure release signing.',
    'added_a_link_to_the_official_t': 'Added a link to the official Telegram channel in the About section.',
    'play': 'Play',
    'exit_app': 'Exit App',
    'are_you_sure_you_want_to_close': 'Are you sure you want to close DMStreaM?',
    'exit': 'Exit',
    'watch_now': 'Watch Now',
    'search_movies_actors': 'Search movies, actors...',
    'dmstream': 'DMStreaM',
    'resolution_log': 'Resolution Log',
    'resume_playback': 'Resume Playback?',
    'would_you_like_to_resume_from_': 'Would you like to resume from where you left off?',
    'resume': 'Resume',
    'start_over': 'Start Over',
    'subtitles': 'Subtitles',
    'none_embedded': 'None / Embedded',
    'subtitle_sync': 'Subtitle Sync',
    'adjust_subtitle_timing': 'Adjust subtitle timing',
    '0_5s': '-0.5s',
    'done': 'Done',
    'select_episode': 'Select Episode',
    'casting_to_device': 'Casting to Device...',
    'try_another_mirror': 'Try Another Mirror',
    'force_webview': 'Force WebView',
    'view_log': 'View Log',
    'player_error': 'Player Error',
    'use_webview': 'Use WebView',
    'switch_server': 'Switch Server',
    'log': 'Log',
    'select_server': 'Select Server',
    'settings': 'Settings',
    'language': 'Language / Bahasa'
}

# Malay translations
malay_dict = {
    'videos': 'Video',
    'retry': 'Cuba Lagi',
    'next': 'Seterusnya',
    'video_quality': 'Kualiti Video',
    'auto': 'Auto',
    'close': 'Tutup',
    'manual_base_url': 'URL Asas Manual',
    'enter_a_new_domain_e_g_https_e': 'Masukkan domain baru (cth. https://example.com). Ini akan menggantikan URL Asas semasa.',
    'update': 'Kemas kini',
    'cancel': 'Batal',
    'app_logcat': 'Logcat Aplikasi',
    'logcat': 'Logcat',
    'copy': 'Salin',
    'fine_tune_the_interface_to_fit': 'Selaraskan antara muka agar sesuai dengan skrin TV anda dengan sempurna.',
    'default_language': 'Bahasa Lalai',
    'manage_home_categories': 'Urus Kategori Utama',
    'choose_which_categories_to_dis': 'Pilih kategori mana yang ingin dipaparkan di skrin Utama.',
    'changelog_v1_9': 'Log Perubahan v1.9',
    'improved_auto_heal_engine_to_f': 'Enjin Pemulihan Automatik yang dipertingkatkan untuk mengalihkan URL tanpa ralat.',
    'fixed': 'Dibaiki',
    'v1_8_updates': 'Kemas kini v1.8:',
    'added_auto_heal_migration_for_': 'Menambah pengalihan pemulihan automatik untuk domain video warisan.',
    'fixed_a_ui_bug_where_tv_displa': 'Membetulkan pepijat UI di mana tetapan paparan TV dipaparkan pada peranti mudah alih.',
    'configured_secure_release_sign': 'Telah mengkonfigurasi tandatangan keluaran yang selamat.',
    'added_a_link_to_the_official_t': 'Menambah pautan ke saluran Telegram rasmi di bahagian Tentang.',
    'play': 'Main',
    'exit_app': 'Keluar Aplikasi',
    'are_you_sure_you_want_to_close': 'Adakah anda pasti mahu menutup DMStreaM?',
    'exit': 'Keluar',
    'watch_now': 'Tonton Sekarang',
    'search_movies_actors': 'Cari filem, pelakon...',
    'dmstream': 'DMStreaM',
    'resolution_log': 'Log Resolusi',
    'resume_playback': 'Teruskan Main Semula?',
    'would_you_like_to_resume_from_': 'Adakah anda ingin meneruskan dari tempat anda berhenti?',
    'resume': 'Teruskan',
    'start_over': 'Mula Semula',
    'subtitles': 'Sarikata',
    'none_embedded': 'Tiada / Terbenam',
    'subtitle_sync': 'Penyegerakan Sarikata',
    'adjust_subtitle_timing': 'Laras pemasaan sarikata',
    '0_5s': '-0.5s',
    'done': 'Selesai',
    'select_episode': 'Pilih Episod',
    'casting_to_device': 'Menghantar ke Peranti...',
    'try_another_mirror': 'Cuba Pelayan Lain',
    'force_webview': 'Paksa WebView',
    'view_log': 'Lihat Log',
    'player_error': 'Ralat Pemain',
    'use_webview': 'Guna WebView',
    'switch_server': 'Tukar Pelayan',
    'log': 'Log',
    'select_server': 'Pilih Pelayan',
    'settings': 'Tetapan',
    'language': 'Language / Bahasa'
}

def create_strings_xml(data_dict, file_path):
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    root = ET.Element('resources')
    
    # Check if file exists to preserve app_name
    existing_app_name = "DMStreaM"
    if os.path.exists(file_path):
        try:
            tree = ET.parse(file_path)
            for el in tree.getroot().findall('string'):
                if el.get('name') == 'app_name':
                    existing_app_name = el.text
        except: pass

    # Always add app_name
    string_elem = ET.SubElement(root, 'string', name='app_name')
    string_elem.text = existing_app_name
    
    for key, value in data_dict.items():
        if key == 'app_name': continue
        string_elem = ET.SubElement(root, 'string', name=key)
        # Escape single quotes
        safe_value = value.replace("'", "\\'")
        string_elem.text = safe_value
        
    xmlstr = minidom.parseString(ET.tostring(root)).toprettyxml(indent="    ")
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(xmlstr)

create_strings_xml(strings_dict, 'app/src/main/res/values/strings.xml')
create_strings_xml(malay_dict, 'app/src/main/res/values-ms/strings.xml')

print("XML files generated!")
