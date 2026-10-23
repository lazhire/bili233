/* Homepage presentation: original nodes, links, scroll container and paging stay owned by Bilibili. */
(function(){
  const options={enabled:HOME_LAYOUT,promotions:HIDE_PROMOTIONS,carousel:HIDE_CAROUSEL};
  if(window.__questHome){window.__questHome.configure(options);return;}
  const root=document.documentElement;
  const style=document.createElement('style');style.id='quest-home-style';
  style.textContent=`
    html[data-quest-home] .bili-header__banner,html[data-quest-home] .bili-header__channel,html[data-quest-home] .header-channel{display:none!important}
    html[data-quest-home] [data-quest-home-grid]{display:grid!important;grid-template-columns:repeat(auto-fill,minmax(240px,1fr))!important;gap:28px 22px!important;width:100%!important;max-width:1600px!important;min-height:0!important;box-sizing:border-box!important;margin:0 auto!important;padding:12px 24px 28px!important;align-items:start!important}
    html[data-quest-home] [data-quest-home-grid]>:is(.bili-feed-card,.feed-card,.bili-video-card){min-width:0!important;width:auto!important;height:auto!important;margin:0!important;grid-column:auto!important;grid-row:auto!important}
    html[data-quest-home] .bili-video-card__wrap{border-radius:12px!important;overflow:hidden;background:var(--bg2,#fff)}
    html[data-quest-home] .bili-video-card__image--wrap{aspect-ratio:16/9}
    html[data-quest-home] .bili-video-card__info--tit{font-size:16px!important;line-height:1.5!important;display:-webkit-box!important;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;min-height:3em}
    html[data-quest-home] .bili-video-card a{color:var(--text1,#252a34);text-decoration:none}
    html[data-quest-home] [data-quest-home-grid]>.recommended-swipe{grid-column:span 2!important;grid-row:span 2!important;min-width:0!important}
    #quest-home-heading{display:none}
    html[data-quest-home] #quest-home-heading{display:flex;align-items:center;flex-wrap:wrap;gap:12px;max-width:1600px;margin:0 auto;padding:24px 24px 12px;box-sizing:border-box;color:var(--text1,#252a34);font:16px/1.5 sans-serif}
    #quest-home-heading strong{font-size:24px;margin-right:18px}
    #quest-home-heading a{padding:10px 18px;border-radius:10px;text-decoration:none;color:inherit;background:var(--bg2,#f1f2f4)}
    #quest-home-heading a[aria-current]{color:#fb7299;background:#fb729922}
    #quest-home-heading a:focus-visible{outline:2px solid #fb7299;outline-offset:2px}
    html[data-quest-home][data-quest-hide-carousel] .recommended-swipe{display:none!important}
    html[data-quest-promotions] .bili-header a[href^="https://game.bilibili.com"],html[data-quest-promotions] .bili-header a[href^="//game.bilibili.com"],html[data-quest-promotions] .bili-header a[href^="https://show.bilibili.com"],html[data-quest-promotions] .bili-header .download-entry{display:none!important}
    @media(max-width:650px){html[data-quest-home] [data-quest-home-grid]{grid-template-columns:repeat(2,minmax(0,1fr))!important;padding:12px!important;gap:18px 12px!important}#quest-home-heading strong{width:100%}}
    @media(max-width:420px){html[data-quest-home] [data-quest-home-grid]{grid-template-columns:minmax(0,1fr)!important}html[data-quest-home] [data-quest-home-grid]>.recommended-swipe{grid-column:span 1!important;grid-row:auto!important}}
  `;
  let config=options,timer=0,heading=null;
  const grids=new Set(),pendingCards=new Set(),headingAttempted=new WeakSet();
  let scans=0,updates=0;
  function inspect(node){if(!(node instanceof Element)||node===heading||node===style||node.closest('#quest-home-heading'))return;if(node.matches('.bili-video-card'))pendingCards.add(node);for(const card of node.querySelectorAll('.bili-video-card'))pendingCards.add(card);}
  function discover(){scans++;inspect(root);}
  const isBili=/(^|\.)bilibili\.com$/i.test(location.hostname);
  function home(){return /^(www\.)?bilibili\.com$/i.test(location.hostname)&&/^\/(?:zh-Hans|zh-Hant|en)?\/?$/i.test(location.pathname);}
  function apply(){
    timer=0;updates++;
    if(!style.isConnected)(document.head||root).appendChild(style);
    root.toggleAttribute('data-quest-promotions',isBili&&config.promotions);
    root.toggleAttribute('data-quest-hide-carousel',config.carousel);
    const active=config.enabled&&home();
    // Enable only after recognizing a feed container; unknown site layouts remain usable.
    if(active){
      for(const card of pendingCards){
        if(!card.isConnected)continue;
        if(card.closest('.recommended-swipe'))continue;
        let slot=card.closest('.bili-feed-card,.feed-card')||card;while(slot.parentElement&&slot.parentElement.matches('.bili-feed-card,.feed-card'))slot=slot.parentElement;const grid=slot.parentElement;
        if(!grid||grid===document.body||grid===root||!/(container|feed|list|recommend)/i.test(grid.className))continue;
        grid.setAttribute('data-quest-home-grid','');grids.add(grid);
      }
    }
    pendingCards.clear();
    for(const grid of grids)if(!grid.isConnected){grid.removeAttribute('data-quest-home-grid');grids.delete(grid);}
    const first=Array.from(grids).find(n=>n.isConnected);
    root.toggleAttribute('data-quest-home',active&&!!first);
    if(active&&first){
      if(!heading){heading=document.createElement('nav');heading.id='quest-home-heading';heading.setAttribute('aria-label','首页分类');
        const title=document.createElement('strong');title.textContent='首页';heading.appendChild(title);
        for(const [name,url] of [['推荐','https://www.bilibili.com/'],['热门','https://www.bilibili.com/v/popular/all/'],['排行榜','https://www.bilibili.com/v/popular/rank/all/'],['动态','https://t.bilibili.com/']]){
          const a=document.createElement('a');a.textContent=name;a.href=url;if(name==='推荐')a.setAttribute('aria-current','page');heading.appendChild(a);
        }
      }
      if(!headingAttempted.has(first)){headingAttempted.add(first);first.before(heading);}
    }else if(heading&&heading.isConnected)heading.remove();
  }
  function schedule(){if(!timer)timer=setTimeout(apply,180);}
  const observer=new MutationObserver(records=>{
    if(!config.enabled||!home())return;
    let changed=false;
    for(const record of records){
      // Ignore title animation, playback counters and cover descendants inside an existing card.
      if(record.target instanceof Element&&record.target.closest('.bili-video-card,#quest-home-heading'))continue;
      for(const node of record.addedNodes){const before=pendingCards.size;inspect(node);if(pendingCards.size!==before)changed=true;}
      for(const node of record.removedNodes)if(node instanceof Element&&Array.from(grids).some(g=>node===g||node.contains(g)))changed=true;
    }
    if(changed)schedule();
  });
  observer.observe(root,{subtree:true,childList:true});
  for(const name of ['pushState','replaceState']){const original=history[name];history[name]=function(){const result=original.apply(this,arguments);discover();schedule();return result;};}
  window.addEventListener('popstate',()=>{discover();schedule();});
  window.__questHome={configure(value){config=value;if(value.enabled){headingAttempted.delete(Array.from(grids)[0]);discover();}apply();},diagnostics(){return {home:root.hasAttribute('data-quest-home'),cards:document.querySelectorAll('.bili-video-card').length,filtered:document.querySelectorAll('[data-quest-ad-card]').length,grids:grids.size,fullScans:scans,layoutUpdates:updates};}};
  discover();apply();
})();
