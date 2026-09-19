import urllib.request
import re

req = urllib.request.Request(
    'https://dsvplay.com/e/94915l0xudzk',
    headers={'User-Agent': 'Mozilla/5.0'}
)
try:
    html = urllib.request.urlopen(req).read().decode('utf-8')
    # print button classes
    buttons = re.findall(r'class=[\'\"]([^\'\"]*play[^\'\"]*)[\'\"]', html, re.IGNORECASE)
    print("Play buttons found:", set(buttons))
    
    # print all ids that contain play
    ids = re.findall(r'id=[\'\"]([^\'\"]*play[^\'\"]*)[\'\"]', html, re.IGNORECASE)
    print("Play ids found:", set(ids))
except Exception as e:
    print("Error:", e)
