const url = 'https://ww44.pencurimovie.baby/movie/sheriff-narko-integriti-2024/'; 
fetch(url).then(r=>r.text()).then(h => { 
    const iframes = [...h.matchAll(/iframe[^>]+src=["']([^"']+)["']/gi)].map(m=>m[1]); 
    console.log('IFRAMES:', iframes); 
    const dataUrl = [...h.matchAll(/data-url=["']([^"']+)["']/gi)].map(m=>m[1]); 
    console.log('DATA-URL:', dataUrl); 
});
