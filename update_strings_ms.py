import os
path = 'app/src/main/res/values-ms/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

new_strings = """    <string name="changelog_v1_9_4">Log Perubahan v1.9.4</string>
    <string name="fixed_category_pagination">Memperbaiki pepijat halaman kategori untuk memuatkan lebih banyak filem.</string>
    <string name="fixed_cast_state">Memperbaiki isu Chromecast yang tersekat pada video sebelumnya.</string>
    <string name="v1_9_3_updates">Kemas kini v1.9.3:</string>
</resources>"""

content = content.replace('</resources>', new_strings)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
