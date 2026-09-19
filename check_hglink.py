import urllib.request

url = 'https://hglink.to/e/02vouj5zbo21'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
try:
    html = urllib.request.urlopen(req).read().decode('utf-8')
    print("Success. HTML Length:", len(html))
    print(html[:500])
except Exception as e:
    print("Error:", e)
