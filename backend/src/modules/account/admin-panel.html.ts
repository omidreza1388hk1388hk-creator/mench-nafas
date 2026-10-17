/**
 * Single-file admin panel. No external scripts or styles (works offline / behind strict CSP).
 * The key lives only in sessionStorage and is sent as the x-admin-key header on each call.
 * All dynamic text goes through textContent, never innerHTML, so user-supplied data cannot inject markup.
 */
export const ADMIN_PANEL_HTML = `<!doctype html>
<html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MENCH Admin</title>
<style>
:root{--bg:#14101a;--card:#1f1927;--fg:#f3eef7;--mut:#a99bb8;--acc:#e0457b;--ok:#4cc38a;--bad:#ff6b6b}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,Tahoma,sans-serif;background:var(--bg);color:var(--fg)}
header{padding:14px 16px;background:linear-gradient(90deg,#a81d56,#5b2a86);font-weight:700}
main{padding:12px;max-width:900px;margin:auto}
.card{background:var(--card);border-radius:14px;padding:14px;margin:12px 0}
h2{margin:0 0 10px;font-size:16px}
input,select,textarea,button{font:inherit;padding:9px 11px;border-radius:10px;border:1px solid #3a3045;background:#150f1c;color:var(--fg);max-width:100%}
button{background:var(--acc);border:0;cursor:pointer;font-weight:600}
button.sec{background:#3a3045}
.row{display:flex;gap:8px;flex-wrap:wrap;margin:8px 0}
.stat{flex:1 1 120px;background:#150f1c;border-radius:10px;padding:10px;text-align:center}
.stat b{display:block;font-size:20px}
table{width:100%;border-collapse:collapse;font-size:13px}td,th{padding:6px;border-bottom:1px solid #2c2436;text-align:start;word-break:break-word}
.mut{color:var(--mut)}.err{color:var(--bad)}
.tabs button{background:#3a3045}.tabs button.on{background:var(--acc)}
</style></head><body>
<header>MENCH Admin</header>
<main>
<div class="card" id="login"><h2>Admin key</h2>
<div class="row"><input id="key" type="password" placeholder="x-admin-key" style="flex:1"><button id="go">Enter</button></div>
<div id="loginErr" class="err"></div></div>
<div id="app" hidden>
<div class="row tabs" id="tabs"></div>
<div id="view"></div></div>
</main>
<script>
(function(){
var KEY=sessionStorage.getItem('mk')||'';
var base=location.pathname.replace(/\\/$/,'')+'/api/';
function el(t,a,c){var e=document.createElement(t);if(a)for(var k in a){if(k==='class')e.className=a[k];else e.setAttribute(k,a[k]);}
 (c||[]).forEach(function(x){e.appendChild(typeof x==='string'?document.createTextNode(x):x);});return e;}
function api(path,method,body){return fetch(base+path,{method:method||'GET',headers:{'x-admin-key':KEY,'x-admin-actor':(sessionStorage.getItem('ma')||''),'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined})
 .then(function(r){if(r.status===401){throw new Error('Wrong key');}if(!r.ok){throw new Error('HTTP '+r.status);}return r.status===204?null:r.json();});}
var view=document.getElementById('view');
function table(rows,cols){var t=el('table');var h=el('tr');cols.forEach(function(c){h.appendChild(el('th',0,[c]));});t.appendChild(h);
 rows.forEach(function(r){var tr=el('tr');cols.forEach(function(c){var v=r[c];tr.appendChild(el('td',0,[v==null?'':String(v)]));});t.appendChild(tr);});return t;}
var tabs={
 Stats:function(){view.textContent='...';api('stats').then(function(s){view.textContent='';var row=el('div',{class:'row'});
  Object.keys(s).forEach(function(k){row.appendChild(el('div',{class:'stat'},[el('b',0,[String(s[k])]),k]));});view.appendChild(el('div',{class:'card'},[row]));});},
 Users:function(){view.textContent='';var q=el('input',{placeholder:'search phone / name / username'});var b=el('button',0,['Search']);
  var out=el('div');function load(){api('users?q='+encodeURIComponent(q.value)).then(function(rows){out.textContent='';out.appendChild(table(rows,['display_name','username','phone_e164','created_at','deleted_at']));});}
  b.onclick=load;view.appendChild(el('div',{class:'card'},[el('div',{class:'row'},[q,b]),out]));load();},
 Feedback:function(){view.textContent='';api('feedback').then(function(rows){view.appendChild(el('div',{class:'card'},[table(rows,['category','message','app_version','device_info','has_logs','created_at'])]));});},
 Announcements:function(){view.textContent='';var t=el('input',{placeholder:'title'});var m=el('textarea',{placeholder:'body',rows:'3'});
  var l=el('select');['info','warning','critical'].forEach(function(x){l.appendChild(el('option',{value:x},[x]));});
  var b=el('button',0,['Publish']);var out=el('div');
  function load(){api('announcements').then(function(rows){out.textContent='';rows.forEach(function(r){var tg=el('button',{class:'sec'},[r.active?'Deactivate':'Activate']);
   tg.onclick=function(){api('announcements/'+r.id+'/active','POST',{active:!r.active}).then(load);};
   out.appendChild(el('div',{class:'row'},[el('span',0,[r.level+' | '+r.title+' — '+r.body]),tg]));});});}
  b.onclick=function(){api('announcements','POST',{title:t.value,body:m.value,level:l.value}).then(function(){t.value='';m.value='';load();}).catch(function(e){alert(e.message);});};
  view.appendChild(el('div',{class:'card'},[el('div',{class:'row'},[t,l]),m,el('div',{class:'row'},[b]),out]));load();},
 Flags:function(){view.textContent='';var k=el('input',{placeholder:'flag_key'});var e=el('select');['true','false'].forEach(function(x){e.appendChild(el('option',{value:x},[x]));});
  var p=el('input',{type:'number',min:'0',max:'100',value:'100'});var b=el('button',0,['Save']);var out=el('div');
  function load(){api('flags').then(function(rows){out.textContent='';out.appendChild(table(rows,['key','enabled','rollout_pct','description']));});}
  b.onclick=function(){api('flags','POST',{key:k.value,enabled:e.value==='true',rolloutPct:parseInt(p.value,10)}).then(load).catch(function(x){alert(x.message);});};
  view.appendChild(el('div',{class:'card'},[el('div',{class:'row'},[k,e,p,b]),out]));load();},
 Reports:function(){view.textContent='';var st=el('select');['open','reviewing','actioned','dismissed',''].forEach(function(x){st.appendChild(el('option',{value:x},[x||'all']));});
  var out=el('div'),detail=el('div');
  function load(){api('reports'+(st.value?'?status='+st.value:'')).then(function(rows){out.textContent='';rows.forEach(function(r){
   var b=el('button',{class:'sec'},['Open']);b.onclick=function(){open(r.id);};
   out.appendChild(el('div',{class:'row'},[el('span',0,[r.category+' | '+r.status+' | reports on user: '+r.target_report_count+' | '+r.created_at]),b]));});});}
  function open(id){api('reports/'+id).then(function(d){detail.textContent='';var c=el('div',{class:'card'});
   c.appendChild(el('h2',0,['Report '+d.report.category+' ('+d.report.status+')']));
   c.appendChild(el('div',{class:'mut'},['Reporter details: '+(d.report.details||'-')]));
   c.appendChild(el('div',0,['Reported message: '+(d.message?(d.message.text||'['+(d.message.attachment?d.message.attachment.mime:d.message.kind)+']'):'(not available)')]));
   c.appendChild(el('div',{class:'mut'},['Other reports on this user: '+d.targetHistory.length]));
   d.notes.forEach(function(n){c.appendChild(el('div',{class:'mut'},[n.author+': '+n.note]));});
   var note=el('input',{placeholder:'moderator note'});var hrs=el('input',{type:'number',value:'24',min:'1'});
   function res(s){api('reports/'+id+'/resolve','POST',{status:s,note:note.value}).then(function(){detail.textContent='';load();});}
   function restrict(k){if(!d.report.target_user_id){alert('No target user');return;}api('users/'+d.report.target_user_id+'/restrict','POST',{kind:k,hours:k==='mute'?parseInt(hrs.value,10):undefined,reason:'Report '+id}).then(function(){res('actioned');});}
   var r1=el('button',0,['Dismiss']);r1.onclick=function(){res('dismissed');};
   var r2=el('button',{class:'sec'},['Mark reviewing']);r2.onclick=function(){res('reviewing');};
   var r3=el('button',0,['Mute user (hours)']);r3.onclick=function(){restrict('mute');};
   var r4=el('button',0,['Suspend user']);r4.onclick=function(){if(confirm('Suspend and sign out this user?'))restrict('suspend');};
   c.appendChild(note);c.appendChild(el('div',{class:'row'},[hrs,r3,r4,r2,r1]));detail.appendChild(c);});}
  st.onchange=load;view.appendChild(el('div',{class:'card'},[el('div',{class:'row'},[st]),out]));view.appendChild(detail);load();},
 Moderation:function(){view.textContent='';var out=el('div');
  api('appeals?status=open').then(function(rows){out.appendChild(el('h2',0,['Open appeals']));rows.forEach(function(a){
   var up=el('button',{class:'sec'},['Uphold']);var ov=el('button',0,['Overturn']);
   up.onclick=function(){api('appeals/'+a.id+'/resolve','POST',{overturn:false}).then(tabs.Moderation);};
   ov.onclick=function(){api('appeals/'+a.id+'/resolve','POST',{overturn:true}).then(tabs.Moderation);};
   out.appendChild(el('div',{class:'row'},[el('span',0,[a.source+' | '+(a.reason||'')+' | "'+a.message+'"']),up,ov]));});
   return api('rules');}).then(function(rules){out.appendChild(el('h2',0,['Automatic rules']));rules.forEach(function(r){
   var en=el('select');['true','false'].forEach(function(x){en.appendChild(el('option',{value:x},[x]));});en.value=String(r.enabled);
   var th=el('input',{type:'number',value:String(r.threshold)});var w=el('input',{type:'number',value:String(r.window_seconds)});var h=el('input',{type:'number',value:String(r.action_hours)});
   var sv=el('button',0,['Save']);sv.onclick=function(){api('rules','POST',{key:r.key,enabled:en.value==='true',threshold:parseInt(th.value,10),windowSeconds:parseInt(w.value,10),actionHours:parseInt(h.value,10)}).then(function(){alert('Saved');});};
   out.appendChild(el('div',{class:'row'},[el('span',0,[r.key+': threshold / window s / mute h']),en,th,w,h,sv]));});
   return api('audit');}).then(function(rows){out.appendChild(el('h2',0,['Audit log']));out.appendChild(table(rows,['created_at','actor','action','target_type','target_id']));});
  view.appendChild(el('div',{class:'card'},[out]));},
 Growth:function(){view.textContent='';var out=el('div');
  api('referral-config').then(function(c){var en=el('select');['true','false'].forEach(function(x){en.appendChild(el('option',{value:x},[x]));});en.value=String(c.enabled);
   var f=function(v){return el('input',{type:'number',value:String(v)});};var qm=f(c.qualify_messages),qc=f(c.qualify_conversations),rd=f(c.reward_plus_days),mr=f(c.max_rewards_per_user);
   var sv=el('button',0,['Save referral rules']);sv.onclick=function(){api('referral-config','POST',{enabled:en.value==='true',qualifyMessages:parseInt(qm.value,10),qualifyConversations:parseInt(qc.value,10),rewardPlusDays:parseInt(rd.value,10),maxRewardsPerUser:parseInt(mr.value,10)}).then(function(){alert('Saved');});};
   out.appendChild(el('h2',0,['Referral rewards']));out.appendChild(el('div',{class:'mut'},['enabled / messages to qualify / chats to qualify / Plus days reward / max rewards per user']));
   out.appendChild(el('div',{class:'row'},[en,qm,qc,rd,mr,sv]));
   var uid=el('input',{placeholder:'user id'});var d=el('input',{type:'number',value:'30'});var g=el('button',0,['Grant Plus days']);
   g.onclick=function(){api('plus/grant','POST',{userId:uid.value,days:parseInt(d.value,10)}).then(function(){alert('Granted');}).catch(function(e){alert(e.message);});};
   out.appendChild(el('h2',0,['MENCH Plus']));out.appendChild(el('div',{class:'row'},[uid,d,g]));
   var cd=el('input',{type:'number',value:'30'});var cn=el('input',{type:'number',value:'5'});var cu=el('input',{type:'number',value:'1'});var cb=el('button',0,['Create redeem codes']);var co=el('pre');
   cb.onclick=function(){api('plus/codes','POST',{days:parseInt(cd.value,10),count:parseInt(cn.value,10),maxUses:parseInt(cu.value,10)}).then(function(c){co.textContent=c.join('\\n');});};
   out.appendChild(el('div',{class:'mut'},['days / how many codes / uses per code']));out.appendChild(el('div',{class:'row'},[cd,cn,cu,cb]));out.appendChild(co);
   return api('sticker-packs');}).then(function(p){out.appendChild(el('h2',0,['Sticker packs']));out.appendChild(table(p.map(function(x){return {name:x.name,premium:x.premium,active:x.active,glyphs:(x.glyphs||[]).join(' ')};}),['name','premium','active','glyphs']));
   var nm=el('input',{placeholder:'pack name'});var gl=el('input',{placeholder:'glyphs separated by spaces'});var pr=el('select');['false','true'].forEach(function(x){pr.appendChild(el('option',{value:x},['premium='+x]));});pr.firstChild.value='false';
   var ad=el('button',0,['Add pack']);ad.onclick=function(){api('sticker-packs','POST',{name:nm.value,premium:pr.value==='true',active:true,sort:0,glyphs:gl.value.split(/\\s+/).filter(Boolean)}).then(tabs.Growth);};
   out.appendChild(el('div',{class:'row'},[nm,gl,pr,ad]));});
  view.appendChild(el('div',{class:'card'},[out]));},
 Analytics:function(){view.textContent='';api('analytics').then(function(rows){view.appendChild(el('div',{class:'card'},[el('div',{class:'mut'},['Counters only. No user ids, no content.']),table(rows,['day','name','count'])]));});}
};
function show(name){Array.prototype.forEach.call(document.getElementById('tabs').children,function(b){b.className=b.textContent===name?'on':'';});tabs[name]();}
function start(){document.getElementById('login').hidden=true;document.getElementById('app').hidden=false;var t=document.getElementById('tabs');
 Object.keys(tabs).forEach(function(n){var b=el('button',0,[n]);b.onclick=function(){show(n);};t.appendChild(b);});show('Reports');}
document.getElementById('go').onclick=function(){KEY=document.getElementById('key').value;var who=prompt('Your name (recorded in the audit log):')||'unnamed';sessionStorage.setItem('ma',who);api('reports?limit=1').then(function(){sessionStorage.setItem('mk',KEY);start();}).catch(function(e){document.getElementById('loginErr').textContent=e.message;});};
if(KEY){api('reports?limit=1').then(start).catch(function(){sessionStorage.removeItem('mk');});}
})();
</script></body></html>`;
