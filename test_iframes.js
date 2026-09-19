const url = 'https://ww44.pencurimovie.baby/movie/sheriff-narko-integriti-2024/';
fetch(url).then(r=>r.text()).then(h => {
    const matches = h.match(/<iframe[^>]+src=["']([^"']+)["'][^>]*>/gi);
    console.log('IFRAMES MATCH:', matches?.slice(0, 5));
});
