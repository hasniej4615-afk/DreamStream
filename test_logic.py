
def identifyMirrorName(name, url):
    lowUrl = url.lower()
    lowName = name.lower()
    
    if 'server' in lowName or 's1' in lowName or 's2' in lowName or 's3' in lowName or 's4' in lowName or len(lowName) <= 3:
        provider = None
        if 'player=1' in lowUrl or 'player=4' in lowUrl or 'player=5' in lowUrl or 'player=7' in lowUrl or 'hgcloud' in lowUrl or 'hgplayer' in lowUrl or 'dhcplay' in lowUrl:
            provider = 'Hgcloud'
        elif 'player=2' in lowUrl or 'player=3' in lowUrl or 'player=6' in lowUrl or 'player=8' in lowUrl or 'indostream' in lowUrl or 'amt' in lowUrl:
            provider = 'IndoStream'
            
        if provider:
            import re
            cleanName = re.sub(r'(?i)server\s*', 'S', name)
            cleanName = re.sub(r'(?i)mirror\s*', 'M', cleanName)
            if not cleanName: cleanName = 'S1'
            return f'{provider}-VIP ({cleanName})'
            
    if not lowName or lowName == 'server' or lowName == 's1': return 'Server 1 (Main)'
    return name

def isGenuineMirror(name, url):
    lowName = name.lower()
    lowUrl = url.lower()
    if '/movie/' in lowUrl or '/tv/' in lowUrl or '/horror/' in lowUrl or '/action/' in lowUrl:
        if 'player=' not in lowUrl and 'mirror=' not in lowUrl and 'action=' not in lowUrl:
            return False
    
    adDomains = ['zeus', 'klik', 'vingaming', 'pingaming', 'chiptaylor', 'ketik.live', 'poker', 'slot', 'bet', 'jud', 'bola', 'win', '88', '138', 'jackpot']
    for d in adDomains:
        if d in lowUrl or d in lowName:
            return False
            
    if 'youtube' in lowUrl or 'trailer' in lowUrl or 'preview' in lowUrl or 'google.com' in lowUrl:
        return False
        
    if 'server' in lowName or 'mirror' in lowName or 'vip' in lowName or 's1' in lowName or 's2' in lowName or 's3' in lowName or 's4' in lowName:
        return True
        
    return False

print(isGenuineMirror('Server 2', 'https://katakatamutiara.com/this-that-and-everything-in-between-2026/?player=2'))
print(identifyMirrorName('Server 2', 'https://katakatamutiara.com/this-that-and-everything-in-between-2026/?player=2'))

