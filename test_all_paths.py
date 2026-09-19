import urllib.request
import re

base = 'https://billofrightsforum.org'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# Try different paths to find working drama, sci-fi, action etc.
test_paths = [
    '/drama/', '/action/', '/horror/', '/comedy/', '/sci-fi/',
    '/thriller/', '/romance/', '/animation/', '/anime/',
    '/genre/drama/', '/genre/action/', '/genre/horror/', '/genre/comedy/',
    '/genre/thriller/', '/genre/romance/', '/genre/animation/',
    '/genre/adventure/', '/genre/fantasy/', '/genre/mystery/',
    '/genre/crime/',
    '/box-office/', '/trending/',
    '/genre/war/', '/genre/family/',
]

for path in test_paths:
    try:
        req = urllib.request.Request(f'{base}{path}', headers=headers)
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            articles = len(re.findall(r'<article', html))
            has_pagination = 'page/2' in html
            print(f'{path:30s}: {articles:3d} articles | pagination={has_pagination} | OK')
    except Exception as e:
        err = str(e)[:30]
        print(f'{path:30s}: FAILED ({err})')
