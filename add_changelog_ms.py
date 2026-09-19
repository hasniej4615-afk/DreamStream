import os

path = 'app/src/main/res/values-ms/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

insert_string = '''    <string name="changelog_v1_9_3">Log Perubahan v1.9.3</string>
    <string name="fixed_tv_dpad_seekbar">Memperbaiki navigasi kiri/kanan DPAD TV pada bar carian pemain.</string>
    <string name="v1_9_2_updates">Kemas kini v1.9.2:</string>
</resources>'''

content = content.replace("</resources>", insert_string)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
