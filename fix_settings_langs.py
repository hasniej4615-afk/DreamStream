import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

additional_strings = {
    'lang_english': 'English',
    'lang_indonesian': 'Indonesian',
    'lang_malay': 'Malay',
    'lang_japanese': 'Japanese',
    'lang_chinese': 'Chinese',
    'lang_thai': 'Thai',
    'lang_arabic': 'Arabic'
}

additional_malay = {
    'lang_english': 'Inggeris',
    'lang_indonesian': 'Indonesia',
    'lang_malay': 'Melayu',
    'lang_japanese': 'Jepun',
    'lang_chinese': 'Cina',
    'lang_thai': 'Thai',
    'lang_arabic': 'Arab'
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

mapper = '''@Composable
fun translateLanguageName(name: String): String {
    return when (name) {
        "English" -> stringResource(R.string.lang_english)
        "Indonesian" -> stringResource(R.string.lang_indonesian)
        "Malay" -> stringResource(R.string.lang_malay)
        "Japanese" -> stringResource(R.string.lang_japanese)
        "Chinese" -> stringResource(R.string.lang_chinese)
        "Thai" -> stringResource(R.string.lang_thai)
        "Arabic" -> stringResource(R.string.lang_arabic)
        else -> name
    }
}'''

if 'translateLanguageName' not in content:
    content = content + '\n\n' + mapper

content = content.replace('Text(text = language, color = Color.White', 'Text(text = translateLanguageName(language), color = Color.White')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
