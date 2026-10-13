// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
(() => {
 const results=[],parse=s=>new DOMParser().parseFromString(s,'application/xml'),xml=n=>new XMLSerializer().serializeToString(n);
 const test=(name,fn)=>{try{fn();results.push({name,success:true});}catch(e){results.push({name,success:false,error:String(e)});}};
 const equal=(a,b)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(JSON.stringify({a,b}));};
 const reject=fn=>{let caught=false;try{fn();}catch(_){caught=true;}if(!caught)throw Error('Expected rejection');};
 const note=(voice,staff,rest=false,duration=1920)=>`<note${rest?' print-object="no"':''}>${rest?'<rest/>':'<pitch><step>C</step><octave>4</octave></pitch>'}<duration>${duration}</duration><voice>${voice}</voice><staff>${staff}</staff></note>`;
 const backup='<backup><duration>1920</duration></backup>';
 const fixture=()=>({native:parse('<museScore><Score><Part><Staff id="7"/><Staff id="8"/></Part><Staff id="7"><Measure><voice><Chord/></voice></Measure></Staff><Staff id="8"><Measure><voice><Chord/></voice></Measure></Staff></Score></museScore>'),layout:parse('<score-partwise><part id="P1"><measure><attributes><staves>2</staves></attributes>'+note(1,1)+backup+note(2,1,true,549)+note(2,1,true,1372)+backup+note(5,2)+'</measure></part></score-partwise>'),owners:[{staff:'7',voiceIndex:1,firstMeasure:0,lastMeasure:0}]});
 const clean=f=>HairpinExport.removeCarriers(f.layout,f.native,f.owners);
 const atomicReject=f=>{const before=[xml(f.layout),xml(f.native)];reject(()=>clean(f));equal([xml(f.layout),xml(f.native)],before);};
 test('remove only owned hidden rests and preceding backup',()=>{const f=fixture(),r=clean(f);equal(r.proof.removedCarrierRests,2);equal(r.proof.removedBackups,1);equal(r.document.querySelectorAll('note').length,2);equal(r.document.querySelectorAll('backup').length,1);});
 test('input layout and native DOMs unchanged',()=>{const f=fixture(),before=[xml(f.layout),xml(f.native)];clean(f);equal([xml(f.layout),xml(f.native)],before);});
 test('original note content and coordinates retained',()=>{const f=fixture();f.layout.querySelector('note').setAttribute('default-x','35.7');const before=Array.from(f.layout.querySelectorAll('note')).filter(n=>n.querySelector('pitch')).map(xml),r=clean(f);equal(Array.from(r.document.querySelectorAll('note')).map(xml),before);});
 test('both staff carrier voices removed independently',()=>{const f=fixture();f.layout.querySelector('measure').insertAdjacentHTML('beforeend',backup+note(6,2,true));f.owners.push({staff:'8',voiceIndex:1,firstMeasure:0,lastMeasure:0});const r=clean(f);equal(r.proof.removedCarrierRests,3);equal(r.proof.removedBackups,2);});
 test('original hidden written rest outside owner voice retained',()=>{const f=fixture();f.layout.querySelector('measure').insertAdjacentHTML('beforeend',note(5,2,true,480));const r=clean(f);equal(r.document.querySelectorAll('rest').length,1);equal(r.document.querySelector('rest').parentNode.querySelector('voice').textContent,'5');});
 test('visible carrier rest rejected atomically',()=>{const f=fixture();f.layout.querySelector('rest').parentNode.removeAttribute('print-object');atomicReject(f);});
 test('pitched note in owned voice rejected atomically',()=>{const f=fixture(),n=f.layout.querySelector('rest');n.outerHTML='<pitch><step>C</step><octave>4</octave></pitch>';atomicReject(f);});
 test('original occupied voice rejected atomically',()=>{const f=fixture();f.native.querySelector('Score > Staff > Measure').insertAdjacentHTML('beforeend','<voice><Chord/></voice>');atomicReject(f);});
 test('unknown native staff rejected atomically',()=>{const f=fixture();f.owners[0].staff='99';atomicReject(f);});
 test('exported staff topology must match native part',()=>{const f=fixture();f.layout.querySelector('staves').textContent='1';atomicReject(f);});
 test('interleaved timing movement rejected atomically',()=>{const f=fixture();f.layout.querySelector('rest').parentNode.insertAdjacentHTML('afterend','<forward><duration>1</duration></forward>');atomicReject(f);});
 test('wrong exported staff does not acquire ownership',()=>{const f=fixture();f.layout.querySelectorAll('note > rest').forEach(n=>n.parentNode.querySelector('staff').textContent='2');atomicReject(f);});
 test('owned grace or chord rest rejected atomically',()=>{for(const tag of ['grace','chord']){const f=fixture();f.layout.querySelector('rest').parentNode.insertAdjacentHTML('afterbegin','<'+tag+'/>');atomicReject(f);}});
 test('no carrier proof leaves every node unchanged',()=>{const f=fixture();f.owners=[];equal(clean(f).document,f.layout);equal(clean(f).proof.removedBackups,0);});
 test('out of bounds range rejected atomically',()=>{const f=fixture();f.owners[0].lastMeasure=9;atomicReject(f);});
 test('duplicate owner cannot delete two backups',()=>{const f=fixture();f.owners.push({...f.owners[0]});atomicReject(f);});
 test('missing preceding backup rejected atomically',()=>{const f=fixture();f.layout.querySelector('backup').remove();atomicReject(f);});
 test('missing owned layout voice rejected atomically',()=>{const f=fixture();f.layout.querySelectorAll('rest').forEach(n=>n.parentNode.remove());atomicReject(f);});
 globalThis.hairpinExportControls=results;
})();
