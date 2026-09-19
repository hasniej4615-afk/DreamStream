import urllib.request
import re

base = 'https://billofrightsforum.org'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# Check 'Para Perasuk' detail page to see what country it's actually tagged with
req = urllib.request.Request(f'{base}/?s=Para+Perasuk', headers=headers)
with urllib.request.urlopen(req, timeout=15) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

# Find the movie link
links = re.findall(r'href=[\"\'](https?://[^\"\']*para-perasuk[^\"\']*)[\"|\']', html, re.IGNORECASE)
print(f'Search results for Para Perasuk: {links}')

if links:
    # Fetch the movie detail page
    req2 = urllib.request.Request(links[0], headers=headers)
    with urllib.request.urlopen(req2, timeout=15) as resp2:
        detail_html = resp2.read().decode('utf-8', errors='ignore')
    
    # Extract country/genre tags
    country_links = re.findall(r'href=[\"\'](https?://[^\"\']*?/country/([^/\"\']+))[\"|\']', detail_html)
    genre_links = re.findall(r'href=[\"\'](https?://[^\"\']*?/genre/([^/\"\']+))[\"|\']', detail_html)
    
    print(f'\\nCountry tags on detail page: {[c[1] for c in country_links]}')
    print(f'Genre tags on detail page: {[g[1] for g in genre_links]}')
    
    # Also check for any metadata block
    meta_block = re.search(r'(?:Country|Negara|Nation)[^<]*?<[^>]+>([^<]+)', detail_html, re.IGNORECASE)
    if meta_block:
        print(f'Metadata country: {meta_block.group(1).strip()}')
