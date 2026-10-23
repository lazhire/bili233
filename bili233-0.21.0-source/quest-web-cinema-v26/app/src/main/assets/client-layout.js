/* Stable browser theme and player input; with reversible filtering of explicitly marked advertising cards. */
(function(){
  const dark=DARK,filterAds=FILTER_ADS;
  const bili=/(^|\.)bilibili\.com$/i.test(location.hostname);
  let api=window.__questBrowser;
  if(api){api.theme(dark);api.ads(filterAds);return;}
  const style=document.createElement('style');style.id='quest-browser-theme';
  (document.head||document.documentElement).appendChild(style);
  const rules=`:root,body{color-scheme:dark;--bg1:#080808;--bg2:#161616;--bg3:#222;--text1:#e7e8ed;--text2:#b3b6bf;--text3:#9398a3;--graph_bg_regular:#080808;--graph_bg_thick:#161616;--graph_bg_thin:#101010;--graph_bg_bright:#080808;--graph_bg_white:#080808}html,body,#app{background-color:#080808!important;color:#e7e8ed!important}[data-quest-surface]{background-color:#161616!important}[data-quest-ink]{color:#e7e8ed!important}input,textarea,select{color:#e7e8ed!important;background-color:#151515!important;border-color:#333!important}`;
  const lightRules=`:root,body{color-scheme:light;--bg1:#ffffff;--bg2:#f5f6f8;--bg3:#e7e9ed;--text1:#20232a;--text2:#505763;--text3:#69717e;--graph_bg_regular:#fff;--graph_bg_thick:#f5f6f8;--graph_bg_thin:#f5f6f8;--graph_bg_bright:#fff;--graph_bg_white:#fff;--line_regular:#dfe2e8;--line_light:#edf0f4}html,body,#app{background-color:#fff!important;color:#20232a!important}[data-quest-light-ink]{color:#20232a!important}input,textarea,select{color:#20232a!important;background-color:#f5f6f8!important;border-color:#d5d9e0!important}input::placeholder,textarea::placeholder{color:#68717e!important;opacity:1}.bili-header__bar,.bili-video-card__wrap,.v-popover-content,.bili-dyn-item{background-color:#fff!important;color:#20232a!important}`;
  const textRules=`.bili-video-card__info--tit,.bili-video-card__info--tit a,.bili-video-card__info--owner,.bili-video-card__info--author,.bili-video-card__info--date,.bili-dyn-title{color:var(--text1)!important}.bili-video-card__info--bottom{color:var(--text2)!important}`;
  const edgeRules=`.bili-dyn-home--member,.bili-dyn-home--visitor,.bili-dyn-list,.bili-dyn-item,.bili-dyn-publishing,.bili-dyn-sidebar,.bili-dyn-up-list,.bili-dyn-topic-box,.bili-dyn-card-video,.bili-dyn-card-article,.bili-dyn-card-common{background-color:#161616!important;color:#e7e8ed!important;border-color:#343740!important;box-shadow:none!important}.bili-rich-text,.bili-dyn-content__orig,.bili-dyn-title,.bili-dyn-card-video__title,.bili-dyn-card-article__title{color:#e7e8ed!important}.bili-dyn-content__repost,.bili-dyn-item__orig{background-color:#101010!important;border-color:#292c34!important}.bili-dyn-action,.bili-dyn-time{color:#a6adba!important}:root,body{--line_regular:#343740;--line_light:#292c34;--graph_weak:#2b2e36;--graph_bg_bright:#14161b}[data-quest-border]{border-color:#343740!important;outline-color:#343740!important}[data-quest-shadow]{box-shadow:0 5px 18px #0008!important}.bili-video-card__wrap,.v-popover-content,.bili-dyn-item,.bili-header__bar{border-color:#343740!important;box-shadow:0 4px 16px #0005!important}input::placeholder,textarea::placeholder{color:#a6adba!important;opacity:1}`;
  const playerSurface='.bpx-player-container,#bilibili-player,.bilibili-player,.bili-video-card__image--wrap,.bili-video-card__cover,.bili-video-card__stats,.bili-video-card__image';
  function brightSurface(n){for(let p=n;p;p=p.parentElement){const color=getComputedStyle(p).backgroundColor;const parts=color.match(/[\d.]+/g);if(parts&&parts.length>=3&&(parts.length<4||+parts[3]>.75))return light(color);}return !isDark;}
  // Presentation only; leave the feed grid, virtualized list and paging under site control.
  const clientStyle=`@media(prefers-reduced-motion:no-preference){.bili-video-card__info--owner,.bili-video-card__info--tit a{transition:color 140ms ease}}.bili-video-card__image--wrap,.bili-video-card__cover,.bili-video-card__wrap{border-radius:12px!important}.bili-video-card__info--tit{font-size:15px!important;line-height:1.5!important}.bili-video-card__info--bottom{line-height:1.6!important}.bili-video-card:focus-within{outline:2px solid #fb7299;outline-offset:3px;border-radius:12px}.bili-video-card__info--owner:hover{color:#fb7299!important}`;
  let isDark=dark,scheduled=false;const queue=[],queued=new WeakSet(),deepQueued=new WeakSet();
  const ui='div,main,section,header,footer,aside,nav,article,li,a,p,span,label,h1,h2,h3,h4,button,input';
  function enqueue(n,deep=true){if(!(n instanceof Element)||n.matches('video,img,canvas,svg,iframe,style,script'))return;if(deep)deepQueued.add(n);if(queued.has(n))return;queued.add(n);queue.push(n);if(!scheduled){scheduled=true;requestAnimationFrame(scan);}}
  function light(c){const n=c.match(/[\d.]+/g);return n&&n.length>=3&&(n.length<4||+n[3]>.75)&&(+n[0]*.2126 + +n[1]*.7152 + +n[2]*.0722)>185;}
  function scan(){scheduled=false;let budget=240;const deadline=performance.now()+4;while(queue.length&&budget--&&performance.now()<deadline){
    const n=queue.shift();queued.delete(n);const deep=deepQueued.has(n);deepQueued.delete(n);if(!n.isConnected)continue;
    if(bili&&n.matches(ui)&&!n.closest(playerSurface)){
      const c=getComputedStyle(n);
      if(isDark){
        if(light(c.backgroundColor))n.setAttribute('data-quest-surface','');
        const rgb=c.color.match(/[\d.]+/g);if(rgb&&Math.max(+rgb[0],+rgb[1],+rgb[2])<120)n.setAttribute('data-quest-ink','');
        if(['Top','Right','Bottom','Left'].some(side=>parseFloat(c['border'+side+'Width'])>0&&light(c['border'+side+'Color']))||parseFloat(c.outlineWidth)>0&&light(c.outlineColor))n.setAttribute('data-quest-border','');
        if(c.boxShadow!=='none'&&(c.boxShadow.match(/rgba?\([^)]*\)/g)||[]).some(light))n.setAttribute('data-quest-shadow','');
      }else if(light(c.color)&&brightSurface(n))n.setAttribute('data-quest-light-ink','');
    }
    if(deep)for(const child of n.children)enqueue(child);
  }if(queue.length&&!scheduled){scheduled=true;requestAnimationFrame(scan);}}
  function theme(value){
    if(isDark!==value)document.querySelectorAll('[data-quest-surface],[data-quest-ink],[data-quest-border],[data-quest-shadow],[data-quest-light-ink]').forEach(n=>{for(const a of ['surface','ink','border','shadow','light-ink'])n.removeAttribute('data-quest-'+a);});
    isDark=value;if(window.__questStopEarlyTheme)window.__questStopEarlyTheme();const early=document.getElementById('quest-start-theme');if(early)early.remove();document.documentElement.style.colorScheme=value?'dark':'light';document.documentElement.setAttribute('data-quest-theme',value?'dark':'light');
    style.textContent=(bili?clientStyle+textRules+(isDark?rules+edgeRules:lightRules):'')+`.bpx-player-ctrl-volume-bar,.bpx-player-ctrl-volume-slider,.bilibili-player-video-volume-slider,.bpx-player-progress-wrap,.bpx-player-progress-schedule-wrap,.bilibili-player-video-progress-slider{touch-action:none!important}`;
    if(bili)enqueue(document.documentElement);
  }
  // Cosmetic filtering only: preserve the site's data, paging and normal cards.
  const cardSelector='.bili-video-card';
  const adStyle=document.createElement('style');adStyle.id='quest-ad-filter';
  adStyle.textContent='html[data-quest-filter-ads] [data-quest-ad-card]{display:none!important}';
  let adsEnabled=false,adTimer=0;
  const adQueue=new Set(),hiddenCards=new Map();
  function adCard(card){
    const badge=Array.from(card.querySelectorAll('.bili-video-card__stats--text')).some(n=>n.textContent.trim()==='骞垮憡');
    const link=Array.from(card.querySelectorAll('a[href]')).some(a=>{try{return new URL(a.getAttribute('href'),location.href).hostname==='cm.bilibili.com';}catch(_){return false;}});
    return badge||link;
  }
  function checkAd(card){
    const previous=hiddenCards.get(card);
    let target=null;
    if(card.isConnected&&adsEnabled&&adCard(card)){
      let wrapper=card.closest('.bili-feed-card,.feed-card');while(wrapper&&wrapper.parentElement&&wrapper.parentElement.matches('.bili-feed-card,.feed-card')&&wrapper.parentElement.querySelectorAll(cardSelector).length===1)wrapper=wrapper.parentElement;
      target=wrapper&&wrapper.querySelectorAll(cardSelector).length===1?wrapper:card;
    }
    if(previous===target)return;
    if(previous)previous.removeAttribute('data-quest-ad-card');
    hiddenCards.delete(card);
    if(target){target.setAttribute('data-quest-ad-card','');hiddenCards.set(card,target);}
  }
  function queueAds(node){
    if(!adsEnabled)return;
    const el=node instanceof Element?node:node.parentElement;
    if(!el||el===adStyle)return;
    const card=el.closest(cardSelector);if(card)adQueue.add(card);
    if(el.matches(cardSelector))adQueue.add(el);
    for(const child of el.querySelectorAll(cardSelector))adQueue.add(child);
    if(adQueue.size&&!adTimer)adTimer=setTimeout(flushAds,60);
  }
  function flushAds(){
    adTimer=0;if(!adsEnabled)return;
    let budget=40;
    for(const card of adQueue){adQueue.delete(card);checkAd(card);if(--budget===0)break;}
    for(const [card,target] of hiddenCards){if(!card.isConnected){target.removeAttribute('data-quest-ad-card');hiddenCards.delete(card);}}
    if(adQueue.size)adTimer=setTimeout(flushAds,40);
  }
  const adObserver=new MutationObserver(records=>{
    if(!adStyle.isConnected)(document.head||document.documentElement).appendChild(adStyle);
    for(const r of records){
      if(r.type==='childList'){
        // Check just the changed card and newly inserted subtrees, not the whole feed.
        const card=r.target instanceof Element?r.target.closest(cardSelector):null;if(card)queueAds(card);
        if(r.target instanceof Element&&r.target.matches('.bili-feed-card,.feed-card'))queueAds(r.target);
        if(r.removedNodes.length&&!adTimer)adTimer=setTimeout(flushAds,60);
        for(const n of r.addedNodes)queueAds(n);
      }else queueAds(r.target);
    }
  });
  function ads(enabled){
    enabled=!!enabled&&bili;if(enabled===adsEnabled)return;
    adsEnabled=enabled;adObserver.disconnect();clearTimeout(adTimer);adTimer=0;adQueue.clear();
    document.documentElement.toggleAttribute('data-quest-filter-ads',enabled);
    if(!enabled){for(const target of hiddenCards.values())target.removeAttribute('data-quest-ad-card');hiddenCards.clear();return;}
    if(!adStyle.isConnected)(document.head||document.documentElement).appendChild(adStyle);
    queueAds(document.documentElement);
    adObserver.observe(document.documentElement,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['href','class']});
  }
  const videos=new WeakMap();let lastVolume=.5;
  try{const v=+sessionStorage.getItem('quest-audible-volume');if(v>0&&v<=1)lastVolume=v;}catch(_){}
  function primaryVideo(){return document.querySelector('.bpx-player-container video,#bilibili-player video,.bilibili-player video')||(/\/video\//.test(location.pathname)?document.querySelector('video'):null);}
  function prepare(v){if(v!==primaryVideo()||videos.has(v))return;const state={started:false,intent:false};videos.set(v,state);v.defaultMuted=false;v.muted=false;if(v.volume===0)v.volume=lastVolume;
    v.addEventListener('play',()=>{if(!state.started){state.started=true;if(!state.intent){v.defaultMuted=false;v.muted=false;if(v.volume===0)v.volume=lastVolume;setTimeout(()=>{if(!state.intent&&!v.paused){v.muted=false;if(v.volume===0)v.volume=lastVolume;}},120);}}});
    v.addEventListener('volumechange',()=>{if(v.volume>0){lastVolume=v.volume;try{sessionStorage.setItem('quest-audible-volume',String(lastVolume));}catch(_){}}});
  }
  function volume(value){const v=primaryVideo();if(!v)return;prepare(v);const state=videos.get(v);if(state)state.intent=true;v.volume=Math.max(0,Math.min(1,value));v.muted=v.volume===0;}
  const mediaKeys=new WeakMap();let mediaSequence=0;
  const mediaSession=Date.now().toString(36)+Math.random().toString(36).slice(2);
  function mediaState(){
    const v=primaryVideo();if(!v)return {available:false};
    let entry=mediaKeys.get(v),source=v.currentSrc||v.src||'';
    if(!entry||entry.source!==source){entry={source,id:mediaSession+':'+(++mediaSequence)};mediaKeys.set(v,entry);}
    const duration=Number.isFinite(v.duration)?v.duration:0;
    const ranges=[];for(let i=0;i<v.seekable.length;i++)ranges.push([v.seekable.start(i),v.seekable.end(i)]);
    const start=ranges.length?ranges[0][0]:0,end=ranges.length?ranges[ranges.length-1][1]:duration;
    return {available:true,id:entry.id,time:Number.isFinite(v.currentTime)?v.currentTime:0,duration,start,end,seekable:end>start,paused:v.paused,volume:v.muted?0:v.volume};
  }
  function control(action,value,id){
    const state=mediaState(),v=primaryVideo();if(!v||!state.available||state.id!==id)return false;
    if(action==='seek'||action==='step'){
      if(!state.seekable||!Number.isFinite(value))return false;
      let target=Math.max(state.start,Math.min(state.end-.01,action==='step'?v.currentTime+value:value));
      // Snap gaps to the nearest real seekable range instead of seeking into unavailable data.
      if(v.seekable.length){let best=state.start,distance=Infinity;for(let i=0;i<v.seekable.length;i++){const candidate=Math.max(v.seekable.start(i),Math.min(v.seekable.end(i)-.01,target));if(Math.abs(candidate-target)<distance){best=candidate;distance=Math.abs(candidate-target);}}target=best;}
      v.currentTime=Math.max(0,target);return true;
    }
    if(action==='volume'){volume(value);return true;}
    if(action==='volumeStep'){volume((v.muted?0:v.volume)+value);return true;}
    if(action==='play'){if(v.paused)v.play().catch(()=>{});else v.pause();return true;}
    return false;
  }
  const volumeRails='.bpx-player-ctrl-volume-bar,.bpx-player-ctrl-volume-slider,.bilibili-player-video-volume-slider';
  const seekRails='.bpx-player-progress-wrap,.bpx-player-progress-schedule-wrap,.bilibili-player-video-progress-slider';
  const controlSelector='.bpx-player-ctrl-volume,.bilibili-player-video-volume';
  const railSelector=volumeRails+','+seekRails;
  let magneticRail=null;
  const magneticDot=document.createElement('div');magneticDot.id='quest-seek-magnet';
  magneticDot.style.cssText='display:none;position:fixed;pointer-events:none;z-index:2147483647;width:12px;height:12px;border-radius:50%;background:#fb7299;box-shadow:0 0 0 5px #fb729944;transform:translate(-50%,-50%)';
  function clearSeekMagnet(){magneticRail=null;magneticDot.style.display='none';}
  function nearbySeek(e){
    if(!(e.target instanceof Element)||e.target.closest('button,input,textarea,select,[role="button"],#quest-home-heading'))return null;
    let nearest=null,best=Infinity;
    for(const rail of document.querySelectorAll(seekRails)){
      const r=rail.getBoundingClientRect();if(r.width<80||r.height<1||r.bottom<0||r.top>innerHeight)continue;
      if(rail.checkVisibility&&!rail.checkVisibility({checkOpacity:true,checkVisibilityCSS:true}))continue;
      const center=r.top+r.height/2,distance=Math.abs(e.clientY-center),range=rail===magneticRail?28:18;
      if(e.clientX<r.left||e.clientX>r.right||distance>range||distance>=best)continue;
      const hit=document.elementFromPoint(e.clientX,Math.max(0,Math.min(innerHeight-1,center)));
      if(!hit||!(rail===hit||rail.contains(hit)))continue;
      nearest=rail;best=distance;
    }
    return nearest;
  }
  function showSeekMagnet(rail,x){
    magneticRail=rail;if(!rail){clearSeekMagnet();return;}
    const surface=document.fullscreenElement||document.webkitFullscreenElement||document.body;if(magneticDot.parentElement!==surface)surface.appendChild(magneticDot);
    const r=rail.getBoundingClientRect();magneticDot.style.left=Math.max(r.left,Math.min(r.right,x))+'px';magneticDot.style.top=(r.top+r.height/2)+'px';magneticDot.style.display='block';
  }
  let drag=null,suppressed=null;
  const preview=document.createElement('div');
  preview.style.cssText='display:none;position:fixed;z-index:2147483647;pointer-events:none;background:#202028;color:white;padding:6px 10px;border-radius:6px;font:14px sans-serif;box-shadow:0 2px 8px #0008';
  function seconds(n){n=Math.max(0,Math.floor(n));return (n>=3600?Math.floor(n/3600)+':':'')+String(Math.floor(n/60)%60).padStart(2,'0')+':'+String(n%60).padStart(2,'0');}
  function stop(e){if(e.cancelable)e.preventDefault();e.stopImmediatePropagation();}
  function update(e){
    if(!drag)return;
    const d=drag,r=d.rect;
    const raw=d.vertical?1-(e.clientY-r.top)/r.height:(e.clientX-r.left)/r.width;
    if(!Number.isFinite(raw))return;
    d.value=Math.max(0,Math.min(1,raw));
    // Live volume updates are throttled; seek performs only one media seek on release.
    if(d.kind==='volume'&&performance.now()-d.lastApply>=40){volume(d.value);d.lastApply=performance.now();}
    preview.textContent=d.kind==='seek'?seconds(d.value*d.video.duration)+' / '+seconds(d.video.duration):'闊抽噺 '+Math.round(d.value*100)+'%';
    const surface=document.fullscreenElement||document.webkitFullscreenElement||document.body;if(preview.parentElement!==surface)surface.appendChild(preview);
    preview.style.display='block';
    preview.style.left=Math.max(6,Math.min(innerWidth-170,e.clientX-60))+'px';
    preview.style.top=Math.max(6,Math.min(innerHeight-40,r.top-38))+'px';
  }
  function finish(commit){
    if(!drag)return;const d=drag;drag=null;
    if(commit&&d.video===primaryVideo()&&d.video.isConnected){
      if(d.kind==='volume')volume(d.value);
      else if(Number.isFinite(d.video.duration)&&d.video.duration>0)d.video.currentTime=Math.min(Math.max(0,d.value*d.video.duration),Math.max(0,d.video.duration-.01));
    }
    suppressed={rail:d.rail,group:d.group,until:performance.now()+550};
    try{if(d.rail.hasPointerCapture(d.id))d.rail.releasePointerCapture(d.id);}catch(_){}
    preview.style.display='none';
    if(d.previousTouchAction)d.rail.style.touchAction=d.previousTouchAction;else d.rail.style.removeProperty('touch-action');
  }
  document.addEventListener('pointerdown',e=>{
    if(drag){if(e.pointerId!==drag.id)return;finish(false);}
    if(!e.isPrimary||e.button>0)return;
    const target=e.target instanceof Element?e.target:null;if(!target)return;
    const rail=target.closest('.bpx-player-progress-wrap')||target.closest(railSelector)||nearbySeek(e),v=primaryVideo();
    if(target.closest(controlSelector)&&v){prepare(v);const state=videos.get(v);if(state)state.intent=true;}
    if(!rail||!v)return;
    const kind=rail.matches(volumeRails)?'volume':'seek';
    if(kind==='seek'&&(!Number.isFinite(v.duration)||v.duration<=0))return;
    const r=rail.getBoundingClientRect();if(r.width<1||r.height<1)return;
    drag={id:e.pointerId,kind,rail,group:rail.closest(controlSelector)||rail,rect:r,vertical:kind==='volume'&&r.height>r.width,video:v,value:kind==='volume'?v.volume:v.currentTime/v.duration,lastApply:-Infinity,previousTouchAction:rail.style.touchAction};
    rail.style.touchAction='none';try{rail.setPointerCapture(e.pointerId);}catch(_){}
    clearSeekMagnet();stop(e);update(e);
  },true);
  document.addEventListener('pointermove',e=>{if(!drag){const v=primaryVideo();showSeekMagnet(v&&Number.isFinite(v.duration)&&v.duration>0?nearbySeek(e):null,e.clientX);return;}if(e.pointerId!==drag.id)return;stop(e);update(e);},true);
  document.addEventListener('pointerup',e=>{if(!drag||e.pointerId!==drag.id)return;stop(e);update(e);finish(true);},true);
  document.addEventListener('pointercancel',e=>{if(drag&&e.pointerId===drag.id){stop(e);finish(false);}},true);
  document.addEventListener('lostpointercapture',e=>{if(drag&&e.pointerId===drag.id)finish(false);},true);
  window.addEventListener('blur',()=>{clearSeekMagnet();finish(false);});
  document.addEventListener('scroll',clearSeekMagnet,true);
  document.addEventListener('pointerout',e=>{if(!e.relatedTarget)clearSeekMagnet();},true);
  document.addEventListener('visibilitychange',()=>{if(document.hidden){clearSeekMagnet();finish(false);}});
  document.addEventListener('click',e=>{if(!suppressed||performance.now()>suppressed.until||!(e.target instanceof Element))return;if(suppressed.rail.contains(e.target)||suppressed.group.contains(e.target))stop(e);},true);
  // Keep the player UI and its magnetic rail in the fullscreen top layer together.
  function fullscreenChanged(){clearSeekMagnet();finish(false);const surface=document.fullscreenElement||document.webkitFullscreenElement||document.body;if(surface&&surface.tagName!=='VIDEO'){surface.appendChild(magneticDot);surface.appendChild(preview);}}
  document.addEventListener('fullscreenchange',fullscreenChanged);
  document.addEventListener('webkitfullscreenchange',fullscreenChanged);
  window.addEventListener('resize',()=>{clearSeekMagnet();finish(false);});
  if(bili){for(const name of ['requestFullscreen','webkitRequestFullscreen']){
    const original=HTMLVideoElement.prototype[name];
    if(typeof original!=='function')continue;
    HTMLVideoElement.prototype[name]=function(...args){const wrapper=this===primaryVideo()?this.closest('.bpx-player-container,#bilibili-player,.bilibili-player'):null;return original.apply(wrapper||this,args);};
  }}
  document.addEventListener('scroll',()=>{for(const x of [innerWidth*.25,innerWidth*.75])for(const y of [40,innerHeight*.5,innerHeight-40]){const n=document.elementFromPoint(x,y);if(n)enqueue(n.closest('.bili-feed-card,.feed-card,.bili-dyn-item')||n);}}, {passive:true});
  const observer=new MutationObserver(records=>{if(!style.isConnected)(document.head||document.documentElement).appendChild(style);for(const r of records){if(r.type==='childList')for(const n of r.addedNodes)enqueue(n);else enqueue(r.target,true);}const v=primaryVideo();if(v)prepare(v);});
  observer.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style']});
  api=window.__questBrowser={theme,ads,volume,mediaState,control,playing:()=>{const v=primaryVideo();return !!v&&!v.paused&&!v.ended;}};theme(dark);ads(filterAds);const v=primaryVideo();if(v)prepare(v);
})();
