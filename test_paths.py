import urllib.request
import re

base = 'https://billofrightsforum.org'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# Test alternative paths for broken genres
test_paths = [
    '/genre/drama/',
    '/genre/sci-fi/',
    '/genre/science-fiction/',
    '/genre/sci-fi-fantasy/',
    '/drama/',
    '/sci-fi/',
    '/genre/action/page/2/',
    '/genre/horror/page/2/',
    '/genre/horror/page/3/',
    '/box-office/page/2/',
]

for path in test_paths:
    try:
        req = urllib.request.Request(f'{base}{path}', headers=headers)
        with urllib.request.urlopen(req, timeout=15) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            articles = len(re.findall(r'<article', html))
            post_ids = len(re.findall(r'id=[\"|\']post-\d+', html))
            total = max(articles, post_ids)
            print(f'{path:40s}: articles={articles:3d}, post-ids={post_ids:3d} | best={total} | status={resp.status}')
    except Exception as e:
        print(f'{path:40s}: ERROR - {e}')
