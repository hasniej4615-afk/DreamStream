const url = 'https://billofrightsforum.org/country/malaysia/';
fetch(url).then(r=>r.text()).then(html=>{
  const matches = [...html.matchAll(/href=["']([^"']+)["']/g)].map(m=>m[1]).filter(u=>u.includes('/movie/') || u.includes('/series/'));
  console.log([...new Set(matches)].slice(0, 5));
});
