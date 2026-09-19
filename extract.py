import re

with open('player3.html', 'r', encoding='utf-8') as f:
    html = f.read()

print('IFRAMES:')
for m in re.findall(r'<iframe[^>]+src=\"([^\"]+)\"', html): print(m)

print('\nEMBEDS (any src):')
for m in re.findall(r'src=\"([^\"]+)\"', html): 
    if 'embed' in m or 'player' in m or 'video' in m:
        print(m)

