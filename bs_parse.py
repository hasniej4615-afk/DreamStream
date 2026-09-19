from bs4 import BeautifulSoup
import sys

with open('player3.html', 'r', encoding='utf-8') as f:
    html = f.read()

soup = BeautifulSoup(html, 'html.parser')

print('Iframes:', soup.find_all('iframe'))
print('Video:', soup.find_all('video'))
print('Embed:', soup.find_all('embed'))

# find script containing mivalyo
for script in soup.find_all('script'):
    if script.string and 'mivalyo' in script.string:
        print('Script with mivalyo:', script.string[:500])
        
