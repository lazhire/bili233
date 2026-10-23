(()=>{
const s=PAGE_STATE;if(location.href!==s.url)return;
let stopped=false,attempt=0,mediaDone=s.time===null,scrollDone=false;
const stop=()=>{stopped=true;clearInterval(timer);for(const e of ['pointerdown','wheel','keydown'])document.removeEventListener(e,stop,true);};
for(const e of ['pointerdown','wheel','keydown'])document.addEventListener(e,stop,true);
function restore(){if(stopped||location.href!==s.url||++attempt>100){stop();return;}
if(!scrollDone){window.scrollTo(s.x,s.y);scrollDone=Math.abs(scrollY-s.y)<4;}
const v=document.querySelector('.bpx-player-container video,#bilibili-player video,.bilibili-player video');
if(!mediaDone&&v&&v.readyState>=1&&Number.isFinite(v.duration)&&v.duration>0){try{v.currentTime=Math.min(s.time,Math.max(0,v.duration-.1));v.volume=Math.max(0,Math.min(1,s.volume));v.muted=s.muted;v.playbackRate=s.rate||1;if(s.playing)v.play().catch(()=>{});else v.pause();mediaDone=true;}catch(_){}}
if(mediaDone&&scrollDone)stop();}
const timer=setInterval(restore,200);restore();
})();