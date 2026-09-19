import urllib.request
import re

url = 'https://actors-pictures.com/blood-brothers-dragons-embers-2025/'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
html = urllib.request.urlopen(req).read().decode('utf-8')

print('IFRAMES:')
for m in re.findall(r'<iframe[^>]+src=\"([^\"]+)\"', html): print(m)

print('\nPLAYERS:')
for m in re.findall(r'href=\"([^\"]*\?player=\d+)\"', html): print(m)
