import urllib.request
import re

url = 'https://actors-pictures.com/blood-brothers-dragons-embers-2025/'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
html = urllib.request.urlopen(req).read().decode('utf-8')

print("Options:")
for m in re.findall(r'<ul[^>]*id=\"playeroptionsul\"[^>]*>(.*?)</ul>', html, re.DOTALL):
    print(m)

print("\nAll data-nume:")
for m in re.findall(r'data-nume=[\"\'](\d+)[\"\']', html):
    print(m)

