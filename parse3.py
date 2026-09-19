import urllib.request
import re

url = 'https://actors-pictures.com/blood-brothers-dragons-embers-2025/?player=3'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
html = urllib.request.urlopen(req).read().decode('utf-8')

with open('player3.html', 'w', encoding='utf-8') as f:
    f.write(html)
