// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Original synthetic fixtures only; no score captures or native runtime.
(() => {
 const results=[],parse=s=>new DOMParser().parseFromString(s,'application/xml'),xml=n=>new XMLSerializer().serializeToString(n);
 const test=(name,fn)=>{try{fn();results.push({name,success:true});}catch(e){results.push({name,success:false,error:String(e)});}};
 const equal=(a,b)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(JSON.stringify({actual:a,expected:b}));};
 const reject=fn=>{let caught=false;try{fn();}catch(_){caught=true;}if(!caught)throw Error('Expected rejection');};
 const rest='<Rest><durationType>measure</durationType></Rest>';
 const chord='<Chord><durationType>whole</durationType><Note><pitch>60</pitch></Note></Chord>';
 const graphic='<Measure len="2"><multiMeasureRest>2</multiMeasureRest><voice><Rest><durationType>measure</durationType><duration>2</duration></Rest></voice></Measure>';
 const sourceRest=v=>`<note><rest/><duration>4</duration><voice>${v}</voice></note>`;
 const wedge=type=>`<direction><direction-type><wedge type="${type}" number="1"/></direction-type><offset>1</offset></direction>`;
 const source=()=>parse('<score-partwise><part-list><score-part id="A"/></part-list><part id="A"><measure><attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>'+wedge('crescendo')+sourceRest(1)+'</measure><measure>'+sourceRest(1)+'<backup><duration>4</duration></backup>'+sourceRest(2)+'</measure><measure>'+wedge('stop')+'<note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note></measure></part></score-partwise>');
 const original=(connectors=false)=>parse('<museScore><Score><Part><Staff id="7"/></Part><Staff id="7"><Measure><voice>'+(connectors?'<Spanner type="HairPin"><HairPin><subtype>0</subtype></HairPin><next><location><measures>2</measures></location></next></Spanner>':'')+rest+'</voice></Measure>'+graphic+'<Measure><voice>'+rest+'</voice><voice>'+rest+'</voice></Measure><Measure><voice>'+(connectors?'<Spanner type="HairPin"><prev><location><measures>-2</measures></location></prev></Spanner>':'')+chord+'</voice></Measure></Staff></Score></museScore>');
 const lengths={'7':['1','1','1']};
 const owners=[{staff:'7',voiceIndex:1,firstMeasure:2,lastMeasure:2}];
 const layout=()=>parse('<score-partwise><part id="A"><measure><attributes><divisions>1</divisions></attributes>'+sourceRest(1)+'</measure><measure>'+sourceRest(1)+'<backup><duration>4</duration></backup>'+sourceRest(2)+'</measure><measure><note default-x="37"><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice><staff>1</staff><notations><articulations><accent/></articulations></notations></note><backup><duration>4</duration></backup><note print-object="no"><rest/><duration>4</duration><voice>2</voice><staff>1</staff></note></measure></part></score-partwise>');
 test('repair indexes logical bars across a collapsed multi-rest graphic',()=>{
  const s=source(),n=original(true),before=[xml(s),xml(n)],display=xml(n.querySelector('Measure[multiMeasureRest]')||n.querySelector('multiMeasureRest').parentNode);
  const p=HairpinNative.repair(s,n),logical=HairpinIdentity.logicalMeasures(p.document.querySelector('Score > Staff'),lengths['7']);
  equal(p.proof.repairedHairpins,1);equal(logical.length,3);
  equal(logical.map(m=>Array.from(m.children).filter(n=>n.localName==='voice').length),[3,3,3]);
  equal(xml(p.document.querySelector('multiMeasureRest').parentNode),display);
  equal(HairpinIdentity.identify(s,p.document).noteCount,1);
  equal(Array.from(p.document.querySelectorAll('Note > pitch')).map(n=>n.textContent),['60']);
  equal([xml(s),xml(n)],before);
 });
 test('export carrier ownership uses logical third bar and preserves original nodes',()=>{
  const l=layout(),n=original(),before=[xml(l),xml(n)],ms=Array.from(l.querySelectorAll('part > measure'));
  const retained=[xml(ms[0]),xml(ms[1]),xml(ms[2].querySelector('note'))];
  const p=HairpinExport.removeCarriers(l,n,owners,lengths),after=Array.from(p.document.querySelectorAll('part > measure'));
  equal(p.proof,{removedCarrierRests:1,removedBackups:1,originalNotesPreserved:true});
  equal([xml(after[0]),xml(after[1]),xml(after[2].querySelector('note'))],retained);
  equal(after[2].querySelectorAll('note').length,1);equal(after[2].querySelectorAll('backup').length,0);
  equal([xml(l),xml(n)],before);
 });
 test('multi-rest export without logical ownership refuses atomically',()=>{
  const l=layout(),n=original(),before=[xml(l),xml(n)];
  reject(()=>HairpinExport.removeCarriers(l,n,owners));equal([xml(l),xml(n)],before);
 });
 globalThis.hairpinMultirestControls=results;
})();
