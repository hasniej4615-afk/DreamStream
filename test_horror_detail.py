import urllib.request
import re

base = 'https://billofrightsforum.org'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# Fetch Horror page 1 and dump article titles to see what's being scraped
req = urllib.request.Request(f'{base}/genre/horror/', headers=headers)
with urllib.request.urlopen(req, timeout=15) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

# Find article blocks
articles = re.findall(r'<article[^>]*>(.*?)</article>', html, re.DOTALL)
print(f'Total <article> blocks: {len(articles)}')

# Extract titles from each article  
for i, art in enumerate(articles):
    title_match = re.search(r'<h2[^>]*>(.*?)</h2>', art, re.DOTALL)
    if not title_match:
        title_match = re.search(r'title=[\"](.*?)[\"]', art)
    title = title_match.group(1) if title_match else '(no title found)'
    title = re.sub(r'<[^>]+>', '', title).strip()[:80]
    print(f'  [{i+1}] {title}')

# Also check for pagination
pagination = re.findall(r'page/(\d+)', html)
print(f'\nPagination links found: {sorted(set(pagination))}')

# Check the main content container
containers = re.findall(r'id=[\"\'](archive-content|gmr-main-load|main-content|primary|content)', html)
print(f'Content containers: {containers}')
