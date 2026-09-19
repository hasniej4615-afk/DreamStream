import os
import re

def insert_strings(path, lang):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    if lang == 'en':
        new_strings = '''
    <string name="changelog_v1_9_1">Changelog v1.9.1</string>
    <string name="added_malay_translation">Added full Malay localization.</string>
    <string name="fixed_tv_scroll_barrier">Fixed TV mode scroll barrier on empty categories.</string>
    <string name="fixed_tv_video_details_silent_fail">Fixed TV mode video details silent failure caused by RAM cache limit.</string>
    <string name="v1_9_updates">v1.9 Updates:</string>'''
    else:
        new_strings = '''
    <string name="changelog_v1_9_1">Log Perubahan v1.9.1</string>
    <string name="added_malay_translation">Menambah terjemahan bahasa Melayu sepenuhnya.</string>
    <string name="fixed_tv_scroll_barrier">Membetulkan halangan tatal (scroll) Mod TV pada kategori kosong.</string>
    <string name="fixed_tv_video_details_silent_fail">Membetulkan kegagalan butiran video dalam Mod TV akibat had cache RAM.</string>
    <string name="v1_9_updates">Kemas kini v1.9:</string>'''

    # Insert before </resources>
    content = content.replace('</resources>', f'{new_strings}\n</resources>')

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

insert_strings('app/src/main/res/values/strings.xml', 'en')
insert_strings('app/src/main/res/values-ms/strings.xml', 'ms')

