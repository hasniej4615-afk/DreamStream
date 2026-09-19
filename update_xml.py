import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

additional_strings = {
    'home': 'Home',
    'movies': 'Movies',
    'tv_shows': 'TV Shows',
    'my_list': 'My List',
    'search': 'Search',
    'settings': 'Settings',
    'stream': 'Stream',
    'newly_updated': 'Newly Updated',
    'tv_series': 'TV Series',
    'action': 'Action',
    'anime': 'Anime',
    'apple_tv': 'Apple TV+',
    'disney_plus': 'Disney+',
    'hbo': 'HBO',
    'horror': 'Horror',
    'indonesia': 'Indonesia',
    'korea': 'Korea',
    'malaysia': 'Malaysia',
    'netflix': 'Netflix',
    'sci_fi': 'Sci-Fi',
    'thailand': 'Thailand',
    'synopsis': 'Sinopsis',
    'cast': 'Cast',
    'episodes': 'Episodes',
    'season_1': 'Season 1',
    'servers': 'Servers',
    'trailer': 'Trailer',
    'no_trailer': 'No Trailer',
    'more': 'Selengkapnya',
    'hide': 'Sembunyikan',
    'no_synopsis': 'Belum ada sinopsis untuk film ini.'
}

additional_malay = {
    'home': 'Utama',
    'movies': 'Filem',
    'tv_shows': 'Rancangan TV',
    'my_list': 'Senarai Saya',
    'search': 'Cari',
    'settings': 'Tetapan',
    'stream': 'Strim',
    'newly_updated': 'Terkini',
    'tv_series': 'Siri TV',
    'action': 'Aksi',
    'anime': 'Anime',
    'apple_tv': 'Apple TV+',
    'disney_plus': 'Disney+',
    'hbo': 'HBO',
    'horror': 'Seram',
    'indonesia': 'Indonesia',
    'korea': 'Korea',
    'malaysia': 'Malaysia',
    'netflix': 'Netflix',
    'sci_fi': 'Sains Fiksyen',
    'thailand': 'Thailand',
    'synopsis': 'Sinopsis',
    'cast': 'Pelakon',
    'episodes': 'Episod',
    'season_1': 'Musim 1',
    'servers': 'Pelayan',
    'trailer': 'Treler',
    'no_trailer': 'Tiada Treler',
    'more': 'Selengkapnya',
    'hide': 'Sembunyikan',
    'no_synopsis': 'Belum ada sinopsis untuk filem ini.'
}

def update_strings_xml(data_dict, file_path):
    tree = ET.parse(file_path)
    root = tree.getroot()
    existing = {el.get('name'): el for el in root.findall('string')}
    
    for key, value in data_dict.items():
        if key not in existing:
            string_elem = ET.SubElement(root, 'string', name=key)
            string_elem.text = value.replace("'", "\\'")
            
    xmlstr = minidom.parseString(ET.tostring(root)).toprettyxml(indent="    ")
    xmlstr = os.linesep.join([s for s in xmlstr.splitlines() if s.strip()])
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(xmlstr)

update_strings_xml(additional_strings, 'app/src/main/res/values/strings.xml')
update_strings_xml(additional_malay, 'app/src/main/res/values-ms/strings.xml')
print("Strings updated")
