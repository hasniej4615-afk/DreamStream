import urllib.request
from bs4 import BeautifulSoup

url = 'https://actors-pictures.com/blood-brothers-dragons-embers-2025/?player=4'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
html = urllib.request.urlopen(req).read().decode('utf-8')

soup = BeautifulSoup(html, 'html.parser')
for iframe in soup.find_all('iframe'):
    print('Player 4 iframe:', iframe.get('src'))
