import urllib.request
import re

base = 'https://billofrightsforum.org'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# Fetch homepage to discover all available genre/category links
req = urllib.request.Request(f'{base}/', headers=headers)
with urllib.request.urlopen(req, timeout=15) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

# Find all genre and category links
genre_links = set(re.findall(r'href=[\"\'](https?://[^\"\']*(?:/genre/|/country/|/network/|/type/|/release/)[^\"\']*)[\"|\']', html))
for link in sorted(genre_links):
    path = re.sub(r'https?://[^/]+', '', link)
    print(f'  {path}')

# Also find menu items
print('\\n--- All nav/menu links ---')
menu_links = set(re.findall(r'<a[^>]*href=[\"\'](https?://billofrightsforum[^\"\']+)[\"|\'][^>]*>([^<]+)</a>', html))
for href, text in sorted(menu_links, key=lambda x: x[1]):
    path = re.sub(r'https?://[^/]+', '', href)
    if any(x in path.lower() for x in ['/genre/', '/country/', '/network/', '/type/', '/release/', 'drama', 'horror', 'action', 'comedy', 'sci']):
        print(f'  {text:25s} -> {path}')
