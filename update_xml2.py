import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

additional_strings = {
    'about_app': 'About App',
    'auto_download_subtitles': 'Auto Download Subtitles',
    'changelog': 'Changelog',
    'check_discover_domain': 'Check / Discover Domain',
    'clear_cache': 'Clear Cache',
    'clear_watch_history': 'Clear Watch History',
    'enable_mobile_landscape': 'Enable Mobile Landscape',
    'join_telegram': 'Join our Telegram Channel',
    'manual_domain_update': 'Manual Domain Update',
    'restore_defaults': 'Restore Defaults',
    'show_calibration_border': 'Show Calibration Border',
    'version_info': 'Version Info',
    'view_app_logcat': 'View App Logcat',
    'add_to_list': 'Add to List',
    'allow_main_app_ui_rotate': 'Allow the main app UI to rotate on mobile devices',
    'auto_select_load_preferred_language': 'Automatically select and load preferred language',
    'back': 'Back',
    'decrease': 'Decrease',
    'displays_red_border_alignment': 'Displays a red border at the edges to help with alignment',
    'join_telegram_desc': 'Get the latest updates, announcements, and support. Tap here to join: t.me/DMXStream',
    'increase': 'Increase',
    'manually_specify_backend_url': 'Manually specify the primary backend URL',
    'picture_in_picture': 'Picture in Picture',
    'quality': 'Quality',
    'read_copy_logcat': 'Read and copy recent system logcat output',
    'remove_all_videos_continue_watching': "Remove all videos from 'Continue Watching'",
    'reset_display_adjustments': 'Reset all display adjustments to original values',
    'select_server': 'Select Server',
    'subtitle_sync': 'Subtitle Sync',
    'toggle_fullscreen': 'Toggle Fullscreen',
    'view_latest_updates': 'View the latest updates and improvements in this release.',
    'voice_search': 'Voice Search',
    'watched': 'Watched',
    'about_app_desc': 'Your premium portal to unlimited entertainment. Enjoy a massive catalog of high-quality movies and TV series across all your Android devices, including TV.',
    'base_url_updated': 'Base URL updated manually',
    'copied_to_clipboard': 'Copied to clipboard',
    'current_domain_up_to_date': 'Current domain is up to date',
    'failed_to_discover_domain': 'Failed to discover domain',
    'history_cleared': 'History cleared',
    'invalid_url_format': 'Invalid URL format',
    'no_app_can_open_link': 'No app can open this link.',
    'settings_caps': 'SETTINGS',
    'loading_logcat': 'Loading logcat...',
    'searching': 'Searching...'
}

additional_malay = {
    'about_app': 'Mengenai Aplikasi',
    'auto_download_subtitles': 'Muat Turun Sarikata Automatik',
    'changelog': 'Log Perubahan',
    'check_discover_domain': 'Semak / Cari Domain',
    'clear_cache': 'Kosongkan Cache',
    'clear_watch_history': 'Kosongkan Sejarah Tontonan',
    'enable_mobile_landscape': 'Papar Melintang Mudah Alih',
    'join_telegram': 'Sertai Saluran Telegram Kami',
    'manual_domain_update': 'Kemas Kini Domain Manual',
    'restore_defaults': 'Pulihkan Tetapan Asal',
    'show_calibration_border': 'Papar Sempadan Kalibrasi',
    'version_info': 'Maklumat Versi',
    'view_app_logcat': 'Lihat Logcat Aplikasi',
    'add_to_list': 'Tambah ke Senarai',
    'allow_main_app_ui_rotate': 'Benarkan antara muka aplikasi berputar pada peranti mudah alih',
    'auto_select_load_preferred_language': 'Pilih dan muatkan bahasa pilihan secara automatik',
    'back': 'Kembali',
    'decrease': 'Kurangkan',
    'displays_red_border_alignment': 'Papar sempadan merah di tepi untuk membantu penjajaran',
    'join_telegram_desc': 'Dapatkan kemas kini dan sokongan terkini. Ketik di sini untuk menyertai: t.me/DMXStream',
    'increase': 'Tambahkan',
    'manually_specify_backend_url': 'Nyatakan URL bahagian pelayan utama secara manual',
    'picture_in_picture': 'Gambar dalam Gambar',
    'quality': 'Kualiti',
    'read_copy_logcat': 'Baca dan salin output logcat sistem terkini',
    'remove_all_videos_continue_watching': "Buang semua video dari 'Teruskan Menonton'",
    'reset_display_adjustments': 'Tetapkan semula semua pelarasan paparan ke nilai asal',
    'select_server': 'Pilih Pelayan',
    'subtitle_sync': 'Penyegerakan Sarikata',
    'toggle_fullscreen': 'Togol Skrin Penuh',
    'view_latest_updates': 'Lihat kemas kini dan peningkatan terkini dalam keluaran ini.',
    'voice_search': 'Carian Suara',
    'watched': 'Ditonton',
    'about_app_desc': 'Portal premium anda ke hiburan tanpa had. Nikmati katalog besar filem dan siri TV berkualiti tinggi merentasi semua peranti Android anda, termasuk TV.',
    'base_url_updated': 'URL Utama dikemas kini secara manual',
    'copied_to_clipboard': 'Disalin ke papan keratan',
    'current_domain_up_to_date': 'Domain semasa adalah yang terkini',
    'failed_to_discover_domain': 'Gagal menemui domain',
    'history_cleared': 'Sejarah dipadam',
    'invalid_url_format': 'Format URL tidak sah',
    'no_app_can_open_link': 'Tiada aplikasi boleh membuka pautan ini.',
    'settings_caps': 'TETAPAN',
    'loading_logcat': 'Memuatkan logcat...',
    'searching': 'Mencari...'
}

def update_strings_xml(data_dict, file_path):
    tree = ET.parse(file_path)
    root = tree.getroot()
    existing = {el.get('name'): el for el in root.findall('string')}
    
    for key, value in data_dict.items():
        if key not in existing:
            string_elem = ET.SubElement(root, 'string', name=key)
            string_elem.text = value.replace("'", "\\'")
            
    xmlstr = minidom.parseString(ET.tostring(root)).toprettyxml(indent="    ")
    xmlstr = os.linesep.join([s for s in xmlstr.splitlines() if s.strip()])
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(xmlstr)

update_strings_xml(additional_strings, 'app/src/main/res/values/strings.xml')
update_strings_xml(additional_malay, 'app/src/main/res/values-ms/strings.xml')
print("Strings updated")
