import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

additional_strings = {
    'display_adjustment_tv': 'DISPLAY ADJUSTMENT (TV)',
    'storage': 'STORAGE',
    'about': 'ABOUT',
    'debug': 'DEBUG',
    'calculating': 'Calculating...',
    'select_preferred_language_desc': 'Select your preferred language / Pilih bahasa pilihan anda',
    'fine_tune_interface_desc': 'Fine-tune the interface to fit your TV screen perfectly.',
    'default_language': 'Default Language',
    'manage_home_categories': 'Manage Home Categories',
    'choose_categories_desc': 'Choose which categories to display on the Home screen.',
    'currently_displayed': 'Currently Displayed',
    'locked': 'Locked',
    'current_usage': 'Current usage: %1$s',
    'error_reading_logcat': 'Error reading logcat: %1$s',
    'current_url_tap_refresh': 'Current: %1$s\nTap to re-scout and refresh content',
    'video_details_not_loading': '"Video Details Not Loading" issue for specific legacy titles.'
}

additional_malay = {
    'display_adjustment_tv': 'PELARASAN PAPARAN (TV)',
    'storage': 'STORAN',
    'about': 'MENGENAI',
    'debug': 'NYAHPEPIJAT',
    'calculating': 'Mengira...',
    'select_preferred_language_desc': 'Sila pilih bahasa pilihan anda',
    'fine_tune_interface_desc': 'Laraskan antara muka untuk muat dengan sempurna di skrin TV anda.',
    'default_language': 'Bahasa Lalai',
    'manage_home_categories': 'Urus Kategori Utama',
    'choose_categories_desc': 'Pilih kategori yang ingin dipaparkan pada skrin Utama.',
    'currently_displayed': 'Sedang Dipaparkan',
    'locked': 'Dikunci',
    'current_usage': 'Penggunaan semasa: %1$s',
    'error_reading_logcat': 'Ralat membaca logcat: %1$s',
    'current_url_tap_refresh': 'Semasa: %1$s\nKetik untuk mencari semula dan muat semula kandungan',
    'video_details_not_loading': 'Isu "Butiran Video Tidak Dimuatkan" untuk tajuk-tajuk lama tertentu.'
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

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('"DISPLAY ADJUSTMENT (TV)" -> "DISPLAY ADJUSTMENT (TV)"', '"DISPLAY ADJUSTMENT (TV)" -> stringResource(R.string.display_adjustment_tv)')
content = content.replace('"STORAGE" -> "STORAGE"', '"STORAGE" -> stringResource(R.string.storage)')
content = content.replace('"ABOUT" -> "ABOUT"', '"ABOUT" -> stringResource(R.string.about)')
content = content.replace('"DEBUG" -> "DEBUG"', '"DEBUG" -> stringResource(R.string.debug)')

content = content.replace('mutableStateOf("Calculating...")', 'mutableStateOf(context.getString(R.string.calculating))')
content = content.replace('"Select your preferred language / Pilih bahasa pilihan anda"', 'stringResource(R.string.select_preferred_language_desc)')
content = content.replace('"Fine-tune the interface to fit your TV screen perfectly."', 'stringResource(R.string.fine_tune_interface_desc)')
content = content.replace('"Default Language"', 'stringResource(R.string.default_language)')
content = content.replace('"Manage Home Categories"', 'stringResource(R.string.manage_home_categories)')
content = content.replace('"Choose which categories to display on the Home screen."', 'stringResource(R.string.choose_categories_desc)')
content = content.replace('"Currently Displayed"', 'stringResource(R.string.currently_displayed)')
content = content.replace('"Locked"', 'stringResource(R.string.locked)')

content = content.replace('"Current usage: $cacheSize"', 'stringResource(R.string.current_usage, cacheSize)')
content = content.replace('"Error reading logcat: ${e.message}"', 'context.getString(R.string.error_reading_logcat, e.message ?: "")')
content = content.replace('"Current: ${activeBaseUrl ?: stringResource(R.string.searching)}\\nTap to re-scout and refresh content"', 'stringResource(R.string.current_url_tap_refresh, activeBaseUrl ?: stringResource(R.string.searching))')

content = content.replace('" \\"Video Details Not Loading\\" issue for specific legacy titles."', '" " + stringResource(R.string.video_details_not_loading)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
