// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
(() => {
 const results=[],parse=s=>new DOMParser().parseFromString(s,'application/xml'),xml=n=>new XMLSerializer().serializeToString(n);
 const test=(name,fn)=>{try{fn();results.push({name,success:true});}catch(e){results.push({name,success:false,error:String(e)});}};
 const equal=(a,b)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(JSON.stringify({actual:a,expected:b}));};
 const reject=fn=>{let rejected=false;try{fn();}catch(_){rejected=true;}if(!rejected)throw Error('Expected rejection');};
 const sourceNote=(step='C',duration=4,voice=1)=>`<note><pitch><step>${step}</step><octave>4</octave></pitch><duration>${duration}</duration><voice>${voice}</voice></note>`;
 const source=(body,extra='')=>parse(`<score-partwise><part-list><score-part id="A"/></part-list><part id="A"><measure><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes><direction><direction-type><wedge type="crescendo" number="1"/></direction-type><offset>1</offset></direction><direction><direction-type><wedge type="stop" number="1"/></direction-type><offset>3</offset></direction>${extra}${body||sourceNote()}</measure></part></score-partwise>`);
 const chord=(pitch=60,type='whole')=>`<Chord><durationType>${type}</durationType><Note><pitch>${pitch}</pitch></Note></Chord>`;
 const head=(extra='')=>`<Spanner type="HairPin"><HairPin><subtype>0</subtype>${extra}</HairPin><next><location/></next></Spanner><Spanner type="HairPin"><prev><location/></prev></Spanner>`;
 const native=(body,voices='',extra='')=>parse(`<museScore><Score><Part><Staff id="7"/></Part><Staff id="7"><Measure><voice>${head(extra)}${body||chord()}</voice>${voices}</Measure></Staff></Score></museScore>`);
 test('exact endpoints have explicit native fractions',()=>{const proof=HairpinNative.repair(source(),native());equal(proof.proof.hiddenRests,3);equal(Array.from(proof.document.querySelectorAll('location > fractions')).map(n=>n.textContent),['1/2','-1/2']);equal(Array.from(proof.document.querySelectorAll('Rest > duration')).map(n=>n.textContent),['1/4','1/2','1/4']);});
 test('original DOMs unchanged',()=>{const s=source(),n=native(),before=[xml(s),xml(n)];HairpinNative.repair(s,n);equal([xml(s),xml(n)],before);});
 test('all new rests hidden',()=>{const p=HairpinNative.repair(source(),native());equal(Array.from(p.document.querySelectorAll('Rest')).map(r=>r.querySelector('visible')?.textContent),['0','0','0']);});
 test('original written rest retained',()=>{const s=source('<note><rest/><duration>2</duration></note>'+sourceNote('C',2)),n=native('<Rest><durationType>half</durationType></Rest>'+chord(60,'half'));const old=xml(n.querySelector('Rest')),p=HairpinNative.repair(s,n);equal(xml(p.document.querySelector('Rest')),old);});
 test('unrelated slur retained',()=>{const n=native(),spanner=n.createElement('Spanner');spanner.setAttribute('type','Slur');spanner.innerHTML='<Slur><up>1</up></Slur><next><location><fractions>1/4</fractions></location></next>';n.querySelector('voice').appendChild(spanner);const before=xml(spanner),p=HairpinNative.repair(source(),n);equal(xml(p.document.querySelector('Spanner[type="Slur"]')),before);});
 test('native hairpin formatting retained',()=>{const p=HairpinNative.repair(source(),native(null,'','<beginText>Original</beginText><visible>0</visible>'));equal(p.document.querySelector('HairPin > beginText').textContent,'Original');equal(p.document.querySelector('HairPin > visible').textContent,'0');});
 test('full four voices rejected atomically',()=>{const pitches=[60,62,64,65],steps=['C','D','E','F'];const s=source(steps.map((step,i)=>(i?'<backup><duration>4</duration></backup>':'')+sourceNote(step,4,i+1)).join(''));const n=native(chord(pitches[0]),pitches.slice(1).map(p=>'<voice>'+chord(p)+'</voice>').join(''));const before=[xml(s),xml(n)];reject(()=>HairpinNative.repair(s,n));equal([xml(s),xml(n)],before);});
 const occupied=(quarter=true)=>{
  const steps=['C','D','E','F'],pitches=[60,62,64,65];
  const body=steps.map((step,i)=>(i?'<backup><duration>4</duration></backup>':'')+(quarter?Array.from({length:4},()=>sourceNote(step,1,i+1)).join(''):sourceNote(step,4,i+1))).join('');
  const voices=pitches.map(p=>quarter?chord(p,'quarter').repeat(4):chord(p));
  return {s:source(body),n:native(voices[0],voices.slice(1).map(v=>'<voice>'+v+'</voice>').join(''))};
 };
 test('four occupied voices use existing exact onsets without rests',()=>{
  const {s,n}=occupied(),before=[xml(s),xml(n)],p=HairpinNative.repair(s,n);
  equal(p.proof.hiddenRests,0);equal(p.document.querySelectorAll('voice').length,4);
  equal(Array.from(p.document.querySelectorAll('Chord')).map(xml),Array.from(n.querySelectorAll('Chord')).map(xml));
  equal(Array.from(p.document.querySelectorAll('Spanner > next > location > fractions')).map(x=>x.textContent),['1/2']);
  equal([xml(s),xml(n)],before);
 });
 test('four occupied voices retain distinct head formatting',()=>{
  const {s,n}=occupied();n.querySelector('HairPin').insertAdjacentHTML('beforeend','<beginText>Keep</beginText><lineWidth>0.3</lineWidth>');
  const p=HairpinNative.repair(s,n);equal(p.document.querySelector('HairPin > beginText').textContent,'Keep');equal(p.document.querySelector('HairPin > lineWidth').textContent,'0.3');
 });
 test('four occupied voices off-onset endpoints reject atomically',()=>{
  const {s,n}=occupied();s.querySelector('offset').textContent='0.5';const before=[xml(s),xml(n)];reject(()=>HairpinNative.repair(s,n));equal([xml(s),xml(n)],before);
 });
 test('four occupied voices cross-measure endpoints use reciprocal anchors',()=>{
  const {s,n}=occupied();const stop=s.querySelectorAll('direction')[1];stop.remove();const measure=s.querySelector('measure').cloneNode(true);measure.querySelector('direction').remove();measure.insertBefore(stop,measure.firstChild);s.querySelector('part').appendChild(measure);
  const m=n.querySelector('Score > Staff > Measure').cloneNode(true);Array.from(m.querySelectorAll('Spanner')).forEach(x=>x.remove());n.querySelector('Score > Staff').appendChild(m);
  n.querySelector('Spanner > next > location').innerHTML='<measures>1</measures>';
  const endpoint=n.querySelector('Spanner > prev').parentNode;endpoint.remove();endpoint.querySelector('location').innerHTML='<measures>-1</measures>';m.querySelector('voice').insertBefore(endpoint,m.querySelector('voice').firstChild);
  const p=HairpinNative.repair(s,n);equal(p.proof.hiddenRests,0);equal(Array.from(p.document.querySelectorAll('Spanner > next > location > measures')).map(x=>x.textContent),['1']);
 });
 test('score-start zero interval binds exact first-voice chord end',()=>{
  const {s,n}=occupied();n.querySelector('Spanner > next > location').innerHTML='<fractions>1/4</fractions>';
  const endpoint=n.querySelector('Spanner > prev').parentNode;endpoint.remove();endpoint.querySelector('location').innerHTML='<fractions>-1/4</fractions>';const voice=n.querySelector('voice');voice.insertBefore(endpoint,voice.querySelectorAll('Chord')[1]);
  const p=HairpinNative.repair(s,n);equal(p.proof.hiddenRests,0);equal(Array.from(p.document.querySelectorAll('Spanner > next > location > fractions')).map(x=>x.textContent),['1/2']);
 });
 test('score-start coercion cannot bind an arbitrary later endpoint',()=>{
  const {s,n}=occupied();n.querySelector('Spanner > next > location').innerHTML='<fractions>1/2</fractions>';const endpoint=n.querySelector('Spanner > prev').parentNode;endpoint.remove();endpoint.querySelector('location').innerHTML='<fractions>-1/2</fractions>';const voice=n.querySelector('voice');voice.insertBefore(endpoint,voice.querySelectorAll('Chord')[2]);const before=[xml(s),xml(n)];reject(()=>HairpinNative.repair(s,n));equal([xml(s),xml(n)],before);
 });
 test('score-start coercion preserves the bound head formatting',()=>{
  const {s,n}=occupied();n.querySelector('HairPin').insertAdjacentHTML('beforeend','<hairpinCircledTip>1</hairpinCircledTip><beginText>Original</beginText>');
  n.querySelector('Spanner > next > location').innerHTML='<fractions>1/4</fractions>';const endpoint=n.querySelector('Spanner > prev').parentNode;endpoint.remove();endpoint.querySelector('location').innerHTML='<fractions>-1/4</fractions>';const voice=n.querySelector('voice');voice.insertBefore(endpoint,voice.querySelectorAll('Chord')[1]);
  const p=HairpinNative.repair(s,n);equal(p.document.querySelector('HairPin > hairpinCircledTip').textContent,'1');equal(p.document.querySelector('HairPin > beginText').textContent,'Original');
 });
 test('nonzero source start never acquires score-start coercion',()=>{
  const {s,n}=occupied();s.querySelectorAll('direction').forEach(x=>x.remove());const first=n.querySelector('voice');const body=s.querySelector('measure');const directions=source().querySelectorAll('direction');const notes=body.querySelectorAll('note');for(const d of directions)body.insertBefore(s.importNode(d,true),notes[1]);
  n.querySelector('Spanner > next > location').innerHTML='<fractions>1/4</fractions>';const endpoint=n.querySelector('Spanner > prev').parentNode;endpoint.remove();endpoint.querySelector('location').innerHTML='<fractions>-1/4</fractions>';first.insertBefore(endpoint,first.querySelectorAll('Chord')[1]);reject(()=>HairpinNative.repair(s,n));
 });
 test('third voice used without touching second',()=>{const s=source(sourceNote()+'<backup><duration>4</duration></backup>'+sourceNote('D',4,2)),n=native(null,'<voice>'+chord(62)+'</voice>'),before=xml(n.querySelectorAll('voice')[1]),p=HairpinNative.repair(s,n);equal(p.document.querySelectorAll('voice').length,3);equal(xml(p.document.querySelectorAll('voice')[1]),before);});
 test('unknown native-only hairpin rejected',()=>{const n=native();n.querySelector('voice').insertAdjacentHTML('beforeend',head());reject(()=>HairpinNative.repair(source(),n));});
 test('missing native connector rejected',()=>{const n=native();n.querySelectorAll('Spanner')[1].remove();reject(()=>HairpinNative.repair(source(),n));});
 test('note identity mismatch rejected atomically',()=>{const n=native(chord(61)),before=xml(n);reject(()=>HairpinNative.repair(source(),n));equal(xml(n),before);});
 test('distinct duplicate formatting not discarded',()=>{const extra='<direction><direction-type><wedge type="crescendo" number="2"/></direction-type><offset>1</offset></direction><direction><direction-type><wedge type="stop" number="2"/></direction-type><offset>2</offset></direction>';const s=source(null,extra),n=native();n.querySelector('voice').insertAdjacentHTML('beforeend',head('<beginText>Different</beginText>'));reject(()=>HairpinNative.repair(s,n));});
 test('distinct formatting binds by imported measure not subtype order',()=>{
  const s=source(),n=native(null,'','<beginText>First</beginText><placement>0</placement>');
  const second=s.querySelector('measure').cloneNode(true);second.querySelector('step').textContent='D';s.querySelector('part').appendChild(second);
  const measure=n.createElement('Measure');measure.innerHTML='<voice>'+head('<beginText>Second</beginText><lineWidth>0.3</lineWidth>')+chord(62)+'</voice>';n.querySelector('Score > Staff').appendChild(measure);
  const before=[xml(s),xml(n)],p=HairpinNative.repair(s,n);
  equal(Array.from(p.document.querySelectorAll('HairPin > beginText')).map(x=>x.textContent),['First','Second']);
  equal(p.document.querySelectorAll('HairPin > placement')[0].textContent,'0');equal(p.document.querySelector('HairPin > lineWidth').textContent,'0.3');
  equal([xml(s),xml(n)],before);
 });
 test('equal imported anchors with identical formatting remain supported',()=>{
  const extra='<direction><direction-type><wedge type="crescendo" number="2"/></direction-type><offset>2</offset></direction><direction><direction-type><wedge type="stop" number="2"/></direction-type><offset>3</offset></direction>';
  const s=source(null,extra),n=native();n.querySelector('voice').insertAdjacentHTML('afterbegin',head());
  equal(HairpinNative.repair(s,n).proof.repairedHairpins,2);
 });
 test('ambiguous equal imported anchors with distinct formatting rejected atomically',()=>{
  const extra='<direction><direction-type><wedge type="crescendo" number="2"/></direction-type><offset>2</offset></direction><direction><direction-type><wedge type="stop" number="2"/></direction-type><offset>3</offset></direction>';
  const s=source(null,extra),n=native();n.querySelector('voice').insertAdjacentHTML('afterbegin',head('<beginText>Different</beginText>'));
  const before=[xml(s),xml(n)];reject(()=>HairpinNative.repair(s,n));equal([xml(s),xml(n)],before);
 });
 test('reciprocal endpoints must match even when total connector count agrees',()=>{
  const s=source(),n=native();n.querySelector('Spanner > prev > location').innerHTML='<fractions>-1/4</fractions>';
  const before=[xml(s),xml(n)];reject(()=>HairpinNative.repair(s,n));equal([xml(s),xml(n)],before);
 });
 test('nonzero document cursor is separate from printed direction offset',()=>{
  const s=source(sourceNote('C',1)+sourceNote('D',3)),notes=s.querySelectorAll('note');
  const dirs=Array.from(s.querySelectorAll('direction'));dirs[0].querySelector('offset').textContent='0';dirs[1].querySelector('offset').textContent='2';
  for(const d of dirs)s.querySelector('measure').insertBefore(d,notes[1]);
  const n=native();n.querySelector('voice').innerHTML=chord(60,'quarter')+head('<beginText>After attack</beginText>')+chord(62,'half').replace('</durationType>','</durationType><dots>1</dots>');
  const p=HairpinNative.repair(s,n);equal(p.plan.spans[0].importStart,'1/4');equal(p.plan.spans[0].start,'1/4');equal(p.plan.spans[0].end,'3/4');
  equal(p.document.querySelector('HairPin > beginText').textContent,'After attack');
 });
 test('native cross-staff connector cannot be guessed from subtype',()=>{
  const n=native();n.querySelector('next > location').innerHTML='<staves>1</staves>';reject(()=>HairpinNative.repair(source(),n));
 });
 const sharedStart=(boundary=false)=>{
  const direction=(type,number,offset)=>`<direction><direction-type><wedge type="${type}" number="${number}"/></direction-type><offset>${offset}</offset></direction>`;
  const s=parse(`<score-partwise><part-list><score-part id="A"/></part-list><part id="A"><measure><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>${direction('crescendo',1,1)+direction('crescendo',2,2)+sourceNote()}</measure><measure>${direction('stop',1,boundary?4:2)+sourceNote('D')}</measure><measure>${direction('stop',2,3)+sourceNote('E')}</measure></part></score-partwise>`);
  const start=(delta,text)=>`<Spanner type="HairPin"><HairPin><subtype>0</subtype><beginText>${text}</beginText></HairPin><next><location><measures>${delta}</measures></location></next></Spanner>`;
  const stop=delta=>`<Spanner type="HairPin"><prev><location><measures>${delta}</measures></location></prev></Spanner>`;
  const n=parse(`<museScore><Score><Part><Staff id="7"/></Part><Staff id="7"><Measure><voice>${start(2,'Long')+start(1,'Short')+chord()}</voice></Measure><Measure><voice>${stop(-1)+chord(62)}</voice></Measure><Measure><voice>${stop(-2)+chord(64)}</voice></Measure></Staff></Score></museScore>`);
  return {s,n};
 };
 test('shared imported start binds distinct endpoint measures despite reversed head order',()=>{
  const {s,n}=sharedStart(),before=[xml(s),xml(n)],p=HairpinNative.repair(s,n);
  equal(Array.from(p.document.querySelectorAll('HairPin > beginText')).map(x=>x.textContent),['Short','Long']);
  equal([xml(s),xml(n)],before);
 });
 test('printed boundary normalization retains original imported endpoint identity',()=>{
  const {s,n}=sharedStart(true),p=HairpinNative.repair(s,n),span=p.plan.spans[0];
  equal([span.endMeasure,span.end,span.importEndMeasure,span.importEnd],[2,'0/1',1,'0/1']);
  equal(Array.from(p.document.querySelectorAll('HairPin > beginText')).map(x=>x.textContent),['Short','Long']);
 });
 globalThis.hairpinNativeControls=results;
})();
