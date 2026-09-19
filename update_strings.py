import os
path = 'app/src/main/res/values/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

new_strings = """    <string name="changelog_v1_9_4">Changelog v1.9.4</string>
    <string name="fixed_category_pagination">Fixed category pagination and paths to fetch more items.</string>
    <string name="fixed_cast_state">Fixed Chromecast getting stuck on previous video when navigating.</string>
    <string name="v1_9_3_updates">v1.9.3 Updates:</string>
</resources>"""

content = content.replace('</resources>', new_strings)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
