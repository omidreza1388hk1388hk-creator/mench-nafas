export const PWA_ICON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#e0457b"/><stop offset="1" stop-color="#5b2a86"/></linearGradient></defs><rect width="64" height="64" rx="14" fill="#14101a"/><path d="M18,6 H46 A14,14 0 0 1 60,20 V36 A14,14 0 0 1 46,50 H28 L14,62 L16,50 H18 A14,14 0 0 1 4,36 V20 A14,14 0 0 1 18,6 Z" fill="url(#g)" transform="translate(4 2) scale(.88)"/><path d="M32,43 C32,43 17,34 17,24.5 C17,20 20.6,17 25,17 C28.2,17 30.8,18.7 32,21.6 C33.2,18.7 35.8,17 39,17 C43.4,17 47,20 47,24.5 C47,34 32,43 32,43 Z" fill="#fff" transform="translate(3 2) scale(.88)"/></svg>`;

export const PWA_MANIFEST = JSON.stringify({
  name: 'MENCH',
  short_name: 'MENCH',
  lang: 'fa',
  dir: 'rtl',
  start_url: './',
  scope: './',
  display: 'standalone',
  background_color: '#14101a',
  theme_color: '#a81d56',
  icons: [{ src: 'icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any maskable' }],
});

// Network-first for the shell so updates arrive; never caches API calls (they live outside this scope).
export const PWA_SW = `
const SHELL='mench-web-shell-v1';
self.addEventListener('install',e=>{self.skipWaiting();});
self.addEventListener('activate',e=>{e.waitUntil(self.clients.claim());});
self.addEventListener('fetch',e=>{
  const u=new URL(e.request.url);
  if(e.request.method!=='GET'||u.origin!==location.origin||!u.pathname.startsWith(new URL('./',self.location).pathname))return;
  e.respondWith(fetch(e.request).then(r=>{const c=r.clone();caches.open(SHELL).then(x=>x.put(e.request,c));return r;}).catch(()=>caches.match(e.request)));
});
`;

export const PWA_HTML = `<!doctype html>
<html lang="fa" dir="rtl"><head><meta charset="utf-8">
<base href="__BASE__">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#a81d56">
<link rel="manifest" href="manifest.webmanifest"><link rel="icon" href="icon.svg">
<title>MENCH</title>
<style>
:root{--bg:#faf6fb;--card:rgba(255,255,255,.78);--fg:#241a2b;--mut:#7a6b86;--acc:#c2185b;--me:#c2185b;--meFg:#fff;--them:#fff;--line:rgba(0,0,0,.08)}
@media(prefers-color-scheme:dark){:root{--bg:#14101a;--card:rgba(40,30,52,.72);--fg:#f3eef7;--mut:#a99bb8;--them:#2a2034;--line:rgba(255,255,255,.1)}}
*{box-sizing:border-box}html,body{height:100%;margin:0}
body{font-family:Vazirmatn,Tahoma,system-ui,sans-serif;background:var(--bg);color:var(--fg);-webkit-tap-highlight-color:transparent}
.hero{background:linear-gradient(135deg,#a81d56,#5b2a86);color:#fff}
#login{min-height:100%;display:flex;align-items:center;justify-content:center;padding:24px;text-align:center}
.glass{background:var(--card);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);border:1px solid var(--line);border-radius:22px;padding:24px;max-width:420px;width:100%}
#qr{width:240px;height:240px;margin:12px auto;background:#fff;border-radius:16px;padding:8px}
#qr svg{width:100%;height:100%}
button{font:inherit;border:0;border-radius:14px;padding:10px 16px;background:var(--acc);color:#fff;cursor:pointer}
button.sec{background:transparent;color:var(--acc);border:1px solid var(--acc)}
#app{display:none;height:100%;grid-template-columns:minmax(260px,340px) 1fr}
#app.on{display:grid}
aside{border-inline-end:1px solid var(--line);overflow:auto;background:var(--card)}
aside header,main header{padding:14px 16px;font-weight:700;display:flex;justify-content:space-between;align-items:center}
.conv{padding:12px 16px;border-bottom:1px solid var(--line);cursor:pointer}
.conv.sel,.conv:hover{background:rgba(194,24,91,.10)}
.conv small{display:block;color:var(--mut)}
main{display:flex;flex-direction:column;min-width:0}
#msgs{flex:1;overflow:auto;padding:14px;display:flex;flex-direction:column;gap:6px}
.b{max-width:75%;padding:8px 12px;border-radius:16px;background:var(--them);word-wrap:break-word;white-space:pre-wrap;align-self:flex-start;border:1px solid var(--line)}
.b.me{background:var(--me);color:var(--meFg);align-self:flex-end;border:0}
.b time{display:block;font-size:11px;opacity:.7;margin-top:2px}
form{display:flex;gap:8px;padding:10px;border-top:1px solid var(--line);background:var(--card)}
input{flex:1;font:inherit;padding:11px 14px;border-radius:14px;border:1px solid var(--line);background:var(--bg);color:var(--fg)}
.empty{margin:auto;color:var(--mut)}
@media(max-width:700px){#app.on{grid-template-columns:1fr}#app.chat aside{display:none}#app:not(.chat) main{display:none}}
</style></head><body>
<div id="login" class="hero"><div class="glass" style="color:var(--fg)">
<h2 style="margin:0 0 4px">MENCH</h2>
<p id="hint" style="color:var(--mut);margin:0">در برنامه‌ی منچ: تنظیمات ← پیوند دستگاه، سپس این کد را اسکن کن.</p>
<div id="qr"></div><p id="state" style="color:var(--mut)"></p><button id="retry" class="sec" hidden>کد جدید</button>
</div></div>
<div id="app"><aside><header><span>گفتگوها</span><button class="sec" id="out">خروج</button></header><div id="list"></div></aside>
<main><header><button class="sec" id="back" style="display:none">بازگشت</button><span id="title"></span></header>
<div id="msgs"><div class="empty">یک گفتگو را انتخاب کن</div></div>
<form id="f" hidden><input id="t" autocomplete="off" placeholder="پیام..."><button>ارسال</button></form></main></div>
<script>
(function(){
var API=new URL('../',document.baseURI).pathname.replace(/\\/$/,'');
var S={access:null,refresh:null,user:null,conv:null,after:0,msgs:[],timer:null};
var $=function(i){return document.getElementById(i);};
function save(){try{localStorage.setItem('mench_web',JSON.stringify({a:S.access,r:S.refresh,u:S.user}));}catch(e){}}
function load(){try{var j=JSON.parse(localStorage.getItem('mench_web')||'null');if(j){S.access=j.a;S.refresh=j.r;S.user=j.u;}}catch(e){}}
function jal(iso,opts){try{return new Intl.DateTimeFormat('fa-IR-u-ca-persian',opts||{dateStyle:'short',timeStyle:'short'}).format(new Date(iso));}catch(e){return iso;}}
function api(path,opt,retry){opt=opt||{};opt.headers=Object.assign({'Content-Type':'application/json'},opt.headers||{});
 if(S.access)opt.headers.Authorization='Bearer '+S.access;
 return fetch(API+path,opt).then(function(r){
  if(r.status===401&&S.refresh&&!retry){return fetch(API+'/auth/token/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:S.refresh})})
   .then(function(x){if(!x.ok)throw new Error('auth');return x.json();}).then(function(t){S.access=t.accessToken;S.refresh=t.refreshToken;save();return api(path,opt,true);})
   .catch(function(){signOut();throw new Error('auth');});}
  if(!r.ok)throw new Error('HTTP '+r.status);return r.status===204?null:r.json();});}
function signOut(){if(S.timer)clearInterval(S.timer);try{localStorage.removeItem('mench_web');}catch(e){}S.access=S.refresh=null;$('app').className='';$('login').style.display='flex';startLogin();}
// ---- QR login ----
var poll=null;
function startLogin(){if(poll)clearInterval(poll);$('retry').hidden=true;$('state').textContent='';
 fetch(API+'/web-login/start',{method:'POST'}).then(function(r){return r.json();}).then(function(c){
  $('qr').innerHTML=c.qrSvg;var left=c.expiresInSeconds;
  poll=setInterval(function(){left-=2;if(left<=0){clearInterval(poll);$('state').textContent='کد منقضی شد';$('retry').hidden=false;return;}
   fetch(API+'/web-login/'+c.id+'/claim',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({secret:c.secret})})
   .then(function(r){if(r.status===410){clearInterval(poll);$('state').textContent='درخواست رد یا منقضی شد';$('retry').hidden=false;return null;}return r.ok?r.json():null;})
   .then(function(t){if(t&&t.status==='approved'){clearInterval(poll);S.access=t.accessToken;S.refresh=t.refreshToken;S.user=t.userId;save();enter();}});},2000);
 }).catch(function(){$('state').textContent='اتصال برقرار نشد';$('retry').hidden=false;});}
$('retry').onclick=startLogin;
// ---- app ----
function enter(){$('login').style.display='none';$('app').className='on';loadConvs();S.timer=setInterval(tick,4000);}
function name(c){return c.title||c.otherUserDisplayName||c.otherUserPhoneE164||'—';}
function loadConvs(){api('/conversations').then(function(list){var el=$('list');el.textContent='';
 list.sort(function(a,b){return String(b.lastMessageAt||'').localeCompare(String(a.lastMessageAt||''));});
 list.forEach(function(c){var d=document.createElement('div');d.className='conv'+(S.conv&&S.conv.id===c.id?' sel':'');
  d.appendChild(document.createTextNode(name(c)));var s=document.createElement('small');s.textContent=c.lastMessageAt?jal(c.lastMessageAt):'';d.appendChild(s);
  d.onclick=function(){openConv(c);};el.appendChild(d);});}).catch(function(){});}
function openConv(c){S.conv=c;S.after=0;S.msgs=[];$('title').textContent=name(c);$('f').hidden=false;$('msgs').textContent='';$('app').classList.add('chat');$('back').style.display='';fetchMsgs(true);loadConvs();}
$('back').onclick=function(){$('app').classList.remove('chat');$('back').style.display='none';};
function render(){var box=$('msgs');var stick=box.scrollTop+box.clientHeight>=box.scrollHeight-60;box.textContent='';
 if(!S.msgs.length){var e=document.createElement('div');e.className='empty';e.textContent='هنوز پیامی نیست';box.appendChild(e);return;}
 S.msgs.forEach(function(m){var d=document.createElement('div');d.className='b'+(m.senderId===S.user?' me':'');
  d.appendChild(document.createTextNode(m.deletedAt?'پیام حذف شد':(m.body||('['+(m.attachment?m.attachment.originalFilename:m.kind)+']'))));
  var t=document.createElement('time');t.textContent=jal(m.createdAt);d.appendChild(t);box.appendChild(d);});
 if(stick)box.scrollTop=box.scrollHeight;}
function fetchMsgs(first){if(!S.conv)return;var id=S.conv.id;
 api('/conversations/'+id+'/messages?after='+S.after+'&limit=100').then(function(list){if(!S.conv||S.conv.id!==id)return;
  if(list.length){list.forEach(function(m){var i=S.msgs.findIndex(function(x){return x.id===m.id;});if(i>=0)S.msgs[i]=m;else S.msgs.push(m);if(m.sequence>S.after)S.after=m.sequence;});
   S.msgs.sort(function(a,b){return a.sequence-b.sequence;});render();if(first)$('msgs').scrollTop=$('msgs').scrollHeight;
   api('/conversations/'+id+'/read',{method:'POST',body:JSON.stringify({lastReadSequence:S.after})}).catch(function(){});}
  else if(first)render();}).catch(function(){});}
function tick(){fetchMsgs(false);if(!S.conv||Date.now()%5===0)loadConvs();}
$('f').onsubmit=function(ev){ev.preventDefault();var v=$('t').value.trim();if(!v||!S.conv)return;$('t').value='';
 var cid=(self.crypto&&crypto.randomUUID)?crypto.randomUUID():String(Date.now())+Math.random();
 api('/conversations/'+S.conv.id+'/messages',{method:'POST',body:JSON.stringify({clientMsgId:cid,body:v})}).then(function(){fetchMsgs(false);}).catch(function(){$('t').value=v;});};
$('out').onclick=function(){if(S.refresh){fetch(API+'/auth/logout',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:S.refresh})}).catch(function(){});}signOut();};
if('serviceWorker' in navigator){navigator.serviceWorker.register('sw.js').catch(function(){});}
load();if(S.access&&S.refresh){enter();}else{startLogin();}
})();
</script></body></html>`;
