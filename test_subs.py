import urllib.request
try:
    req = urllib.request.Request('https://subdl.com', headers={'User-Agent': 'Mozilla/5.0'})
    res = urllib.request.urlopen(req)
    print("SubDL Status:", res.status)
except Exception as e:
    print("SubDL Error:", e)

try:
    req = urllib.request.Request('https://subtitlecat.com', headers={'User-Agent': 'Mozilla/5.0'})
    res = urllib.request.urlopen(req)
    print("SubtitleCat Status:", res.status)
except Exception as e:
    print("SubtitleCat Error:", e)

try:
    req = urllib.request.Request('https://api.opensubtitles.com/api/v1/subtitles', headers={'User-Agent': 'Mozilla/5.0'})
    res = urllib.request.urlopen(req)
    print("OpenSubs Status:", res.status)
except Exception as e:
    print("OpenSubs Error:", e)

