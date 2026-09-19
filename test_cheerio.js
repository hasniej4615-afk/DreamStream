const url = 'https://ww44.pencurimovie.baby/movie/sheriff-narko-integriti-2024/';
fetch(url).then(r=>r.text()).then(h => {
    const cheerio = require('cheerio');
    const $ = cheerio.load(h);
    const videoArea = $(".gmr-pagi-player, .player-wrap, .video-player, #player, .embed-responsive, .muvipro-player-wrap");
    console.log("VideoArea found:", videoArea.length);
    console.log("Iframes in videoArea:", videoArea.find('iframe').length);
    console.log("Total iframes:", $('iframe').length);
});
