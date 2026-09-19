import urllib.request
import re

base = 'https://billofrightsforum.org'
categories = {
    'Action': '/genre/action/',
    'Comedy': '/genre/comedy/',
    'Drama': '/genre/drama/',
    'Horror': '/genre/horror/',
    'Sci-Fi': '/genre/sci-fi/',
    'Box-Office': '/box-office/',
    'Korea': '/country/korea/',
}

headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

for cat, path in categories.items():
    try:
        req = urllib.request.Request(f'{base}{path}', headers=headers)
        with urllib.request.urlopen(req, timeout=15) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            # Count items using the same selectors as the app
            items = len(re.findall(r'class=[\"\'](ml-item|item-movie|movie-item|post-item|gmr-item|movie-post)', html))
            articles = len(re.findall(r'<article', html))
            post_ids = len(re.findall(r'id=[\"|\']post-\d+', html))
            total = max(items, articles, post_ids)
            print(f'{cat:12s} ({path:25s}): items={items:3d}, articles={articles:3d}, post-ids={post_ids:3d} | best={total}')
    except Exception as e:
        print(f'{cat:12s}: ERROR - {e}')
