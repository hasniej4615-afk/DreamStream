import os

path = 'app/src/main/java/com/duta/movie/ui/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('label = "Screen Edge Margin (Safe Area)",', 'label = stringResource(R.string.screen_edge_margin),')
content = content.replace('label = "Headliner (Hero) Height Offset",', 'label = stringResource(R.string.headliner_height_offset),')
content = content.replace('label = "Overall UI Scale",', 'label = stringResource(R.string.overall_ui_scale),')
content = content.replace('label = "Thumbnail (Poster) Size",', 'label = stringResource(R.string.thumbnail_size),')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

import xml.etree.ElementTree as ET
from xml.dom import minidom

additional_strings = {
    'screen_edge_margin': 'Screen Edge Margin (Safe Area)',
    'headliner_height_offset': 'Headliner (Hero) Height Offset',
    'overall_ui_scale': 'Overall UI Scale',
    'thumbnail_size': 'Thumbnail (Poster) Size',
}

additional_malay = {
    'screen_edge_margin': 'Margin Tepi Skrin (Kawasan Selamat)',
    'headliner_height_offset': 'Ketinggian Pengepala (Hero)',
    'overall_ui_scale': 'Skala UI Keseluruhan',
    'thumbnail_size': 'Saiz Lakaran Kenit (Poster)',
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

