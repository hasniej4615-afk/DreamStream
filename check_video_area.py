import urllib.request
from bs4 import BeautifulSoup
import re

url = 'https://actors-pictures.com/blood-brothers-dragons-embers-2025/?player=4'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
html = urllib.request.urlopen(req).read().decode('utf-8')

soup = BeautifulSoup(html, 'html.parser')
videoArea = soup.select('.gmr-pagi-player, .player-wrap, .video-player, #player, .embed-responsive, .muvipro-player-wrap')
print("VideoArea empty?", len(videoArea) == 0)

if len(videoArea) > 0:
    for iframe in videoArea[0].select('iframe, embed'):
        print('Iframe in videoArea:', iframe.get('src'))
else:
    for iframe in soup.select('iframe, embed'):
        print('Iframe anywhere:', iframe.get('src'))

