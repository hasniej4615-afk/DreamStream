import urllib.request
import re

base = 'https://billofrightsforum.org'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# Fetch France category and extract all movie titles
categories_to_check = {
    'France': '/country/france/',
    'Indonesia': '/country/indonesia/',
    'Korea': '/country/korea/',
    'Thailand': '/country/thailand/',
    'Japan': '/country/japan/',
    'India': '/country/india/',
}

for cat_name, path in categories_to_check.items():
    try:
        req = urllib.request.Request(f'{base}{path}', headers=headers)
        with urllib.request.urlopen(req, timeout=15) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
        
        # Extract article titles
        articles = re.findall(r'<article[^>]*>(.*?)</article>', html, re.DOTALL)
        titles = []
        for art in articles:
            title_match = re.search(r'<h2[^>]*>(.*?)</h2>', art, re.DOTALL)
            if not title_match:
                title_match = re.search(r'title=[\"](.*?)[\"]', art)
            if title_match:
                title = re.sub(r'<[^>]+>', '', title_match.group(1)).strip()[:80]
                titles.append(title)
        
        print(f'\\n=== {cat_name} ({path}) === [{len(titles)} titles]')
        for i, t in enumerate(titles):
            print(f'  [{i+1}] {t}')
    except Exception as e:
        print(f'\\n=== {cat_name} ({path}) === ERROR: {e}')
