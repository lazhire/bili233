/* Stable browser theme and player input; never reflow or hide feed cards. */
(function(){
  const dark=DARK;
  const bili=/(^|\.)bilibili\.com$/i.test(location.hostname);
  let api=window.__questBrowser;
  if(api){api.theme(dark);return;}
  const style=document.createElement('style');style.id='quest-browser-theme';
  (document.head||document.documentElement).appendChild(style);
  const rules=`:root,body{color-scheme:dark;--bg1:#080808;--bg2:#161616;--bg3:#222;--text1:#e7e8ed;--text2:#b3b6bf;--text3:#9398a3;--graph_bg_regular:#080808;--graph_bg_thick:#161616;--graph_bg_thin:#101010;--graph_bg_bright:#080808;--graph_bg_white:#080808}html,body,#app{background-color:#080808!important;color:#e7e8ed!important}[data-quest-surface]{background-color:#161616!important}[data-quest-ink]{color:#e7e8ed!important}input,textarea,select{color:#e7e8ed!important;background-color:#151515!important;border-color:#333!important}`;
  let isDark=dark,scheduled=false;const queue=[],queued=new WeakSet();
  const ui='div,main,section,header,footer,aside,nav,article,li,a,p,span,label,h1,h2,h3,h4,button,input';
  function enqueue(n){if(!(n instanceof Element)||n===style||n.matches('video,img,canvas,svg,iframe')||queued.has(n))return;queued.add(n);queue.push(n);if(!scheduled){scheduled=true;setTimeout(scan,80);}}
  function light(c){const n=c.match(/[\d.]+/g);return n&&n.length>=3&&(n.length<4||+n[3]>.75)&&(+n[0]*.2126 + +n[1]*.7152 + +n[2]*.0722)>185;}
  function scan(){scheduled=false;let budget=100;while(queue.length&&budget--){const n=queue.shift();queued.delete(n);if(!n.isConnected)continue;if(isDark&&bili&&n.matches(ui)){const c=getComputedStyle(n);if(light(c.backgroundColor))n.setAttribute('data-quest-surface','');const rgb=c.color.match(/[\d.]+/g);if(rgb&&Math.max(+rgb[0],+rgb[1],+rgb[2])<120)n.setAttribute('data-quest-ink','');}for(const child of n.children)enqueue(child);if(n.tagName==='VIDEO')prepare(n);}if(queue.length&&!scheduled){scheduled=true;setTimeout(scan,40);}}
  function theme(value){isDark=value;style.textContent=(bili&&isDark?rules:'')+`.bpx-player-ctrl-volume-bar,.bpx-player-ctrl-volume-slider,.bilibili-player-video-volume-slider{touch-action:none!important;min-width:28px}`;if(bili&&isDark)enqueue(document.documentElement);}
  const videos=new WeakMap();let lastVolume=.5;
  try{const v=+sessionStorage.getItem('quest-audible-volume');if(v>0&&v<=1)lastVolume=v;}catch(_){}
  function primaryVideo(){return document.querySelector('.bpx-player-container video,#bilibili-player video,.bilibili-player video')||(/\/video\//.test(location.pathname)?document.querySelector('video'):null);}
  function prepare(v){if(v!==primaryVideo()||videos.has(v))return;const state={started:false,intent:false};videos.set(v,state);v.defaultMuted=false;v.muted=false;if(v.volume===0)v.volume=lastVolume;
    v.addEventListener('play',()=>{if(!state.started){state.started=true;if(!state.intent){v.defaultMuted=false;v.muted=false;if(v.volume===0)v.volume=lastVolume;setTimeout(()=>{if(!state.intent&&!v.paused){v.muted=false;if(v.volume===0)v.volume=lastVolume;}},120);}}});
    v.addEventListener('volumechange',()=>{if(v.volume>0){lastVolume=v.volume;try{sessionStorage.setItem('quest-audible-volume',String(lastVolume));}catch(_){}}});
  }
  function volume(value){const v=primaryVideo();if(!v)return;prepare(v);const state=videos.get(v);if(state)state.intent=true;v.volume=Math.max(0,Math.min(1,value));v.muted=v.volume===0;}
  const railSelector='.bpx-player-ctrl-volume-bar,.bpx-player-ctrl-volume-slider,.bilibili-player-video-volume-slider';
  const controlSelector='.bpx-player-ctrl-volume,.bilibili-player-video-volume';let drag=null,suppressUntil=0;
  function update(e){if(!drag)return;const r=drag.rect;volume(drag.vertical?1-(e.clientY-r.top)/r.height:(e.clientX-r.left)/r.width);}
  document.addEventListener('pointerdown',e=>{const target=e.target instanceof Element?e.target:null;if(!target)return;const rail=target.closest(railSelector);const v=primaryVideo();if(target.closest(controlSelector)&&v){prepare(v);const s=videos.get(v);if(s)s.intent=true;}if(!rail||!v)return;const r=rail.getBoundingClientRect();if(r.width<1||r.height<1)return;drag={id:e.pointerId,rect:r,vertical:r.height>r.width};try{rail.setPointerCapture(e.pointerId);}catch(_){}e.preventDefault();e.stopImmediatePropagation();update(e);},true);
  document.addEventListener('pointermove',e=>{if(!drag||e.pointerId!==drag.id)return;e.preventDefault();e.stopImmediatePropagation();update(e);},true);
  document.addEventListener('pointerup',e=>{if(!drag||e.pointerId!==drag.id)return;update(e);drag=null;suppressUntil=performance.now()+500;e.preventDefault();e.stopImmediatePropagation();},true);
  document.addEventListener('pointercancel',()=>{drag=null;},true);
  document.addEventListener('click',e=>{if(performance.now()<suppressUntil&&e.target instanceof Element&&e.target.closest(controlSelector+','+railSelector)){e.preventDefault();e.stopImmediatePropagation();}},true);
  const observer=new MutationObserver(records=>{if(!style.isConnected)(document.head||document.documentElement).appendChild(style);for(const r of records){if(r.type==='childList')for(const n of r.addedNodes)enqueue(n);else enqueue(r.target);}const v=primaryVideo();if(v)prepare(v);});
  observer.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style']});
  api=window.__questBrowser={theme,volume,playing:()=>{const v=primaryVideo();return !!v&&!v.paused&&!v.ended;}};theme(dark);const v=primaryVideo();if(v)prepare(v);
})();
