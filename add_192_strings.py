import os
import re

def insert_strings(path, lang):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    if lang == 'en':
        new_strings = '''
    <string name="changelog_v1_9_2">Changelog v1.9.2</string>
    <string name="added_dynamic_mobile_hero">Added dynamic 10-second random hero cycling in mobile mode.</string>
    <string name="fixed_language_highlight_lag">Fixed delayed highlight when switching languages.</string>
    <string name="fixed_language_btn_alignment">Fixed text alignment on language toggle buttons.</string>
    <string name="v1_9_1_updates">v1.9.1 Updates:</string>'''
    else:
        new_strings = '''
    <string name="changelog_v1_9_2">Log Perubahan v1.9.2</string>
    <string name="added_dynamic_mobile_hero">Menambah kitaran hero dinamik rawak 10 saat pada mod mudah alih.</string>
    <string name="fixed_language_highlight_lag">Membetulkan serlahan (highlight) yang lewat apabila menukar bahasa.</string>
    <string name="fixed_language_btn_alignment">Membetulkan penjajaran teks pada butang tukar bahasa.</string>
    <string name="v1_9_1_updates">Kemas kini v1.9.1:</string>'''

    # Insert before </resources>
    content = content.replace('</resources>', f'{new_strings}\n</resources>')

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

insert_strings('app/src/main/res/values/strings.xml', 'en')
insert_strings('app/src/main/res/values-ms/strings.xml', 'ms')
