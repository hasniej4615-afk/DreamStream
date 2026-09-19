import urllib.request
import re
import time

base_url = 'https://actors-pictures.com/blood-brothers-dragons-embers-2025/?player='

for i in range(2, 5):
    url = base_url + str(i)
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    html = urllib.request.urlopen(req).read().decode('utf-8')
    match = re.search(r'<iframe[^>]+src=\"([^\"]+)\"', html)
    if match:
        print(f"Player {i}: {match.group(1)}")
    else:
        print(f"Player {i}: No iframe found")
    time.sleep(1)
