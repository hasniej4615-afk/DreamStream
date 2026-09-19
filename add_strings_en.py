import os

path = 'app/src/main/res/values/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

insert_string = '''    <string name="click_to_check_updates">Click to Check for Updates</string>
    <string name="checking_for_updates">Checking for updates...</string>
</resources>'''

content = content.replace("</resources>", insert_string)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
