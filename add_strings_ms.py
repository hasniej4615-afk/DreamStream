import os

path = 'app/src/main/res/values-ms/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

insert_string = '''    <string name="click_to_check_updates">Klik untuk Semakan Kemaskini</string>
    <string name="checking_for_updates">Menyemak kemaskini...</string>
</resources>'''

content = content.replace("</resources>", insert_string)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
