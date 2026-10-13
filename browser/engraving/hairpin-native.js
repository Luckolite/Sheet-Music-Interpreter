// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Restore source-owned hairpin anchors without changing original notes or rests.
(() => {
 'use strict';
 const F=HairpinSource.Fraction,z=F.from(0);
 const need=(v,m)=>{if(!v)throw Error(m);};
 const children=(n,name)=>Array.from(n.children).filter(c=>c.localName===name);
 const serialize=n=>new XMLSerializer().serializeToString(n);
 const append=(parent,name,text)=>{const n=parent.ownerDocument.createElement(name);if(text!==undefined)n.textContent=String(text);parent.appendChild(n);return n;};
 const existingEvents=doc=>Array.from(doc.querySelectorAll('Chord, Rest')).map(serialize);
 // The bundled importer ignores direction offsets, but retains the direction's
 // document cursor. Bind using that observed import behavior, independently of
 // the exact printed endpoints being restored. Never rank by array position.
 function bindHeadsForProfile(native,plan,exact){
  const starts={};
  for(const [sid,lengths] of Object.entries(plan.lengths)){
   let total=z;starts[sid]=lengths.map(value=>{const start=total;total=total.add(F.from(value));return start;});starts[sid].push(total);
  }
  const absolute=(sid,mi,point)=>{need(starts[sid] && Number.isSafeInteger(mi) && mi>=0 && mi<starts[sid].length,'Invalid imported hairpin measure');const value=F.from(point);need(value.compare(z)>=0 && (mi===plan.lengths[sid].length?value.compare(z)===0:value.compare(F.from(plan.lengths[sid][mi]))<=0),'Invalid imported hairpin cursor');return starts[sid][mi].add(value);};
  const key=(sid,start,end)=>JSON.stringify([sid,start.toString(),end.toString()]);
  const field=(node,name,fallback)=>{const found=children(node,name);need(found.length<=1,'Ambiguous imported '+name);return found.length?found[0].textContent.trim():fallback;};
  const sources=new Map(),heads=new Map(),nextPairs=new Map(),prevPairs=new Map();
  for(const span of plan.spans){
   const id=exact
    ? key(span.staff,absolute(span.staff,span.startMeasure,span.start),absolute(span.staff,span.endMeasure,span.end))
    : key(span.staff,absolute(span.staff,span.importStartMeasure,span.importStart),absolute(span.staff,span.importEndMeasure,span.importEnd));
   const typed=JSON.stringify([id,span.subtype]);if(!sources.has(typed))sources.set(typed,[]);sources.get(typed).push(span);
  }
  HairpinIdentity.walkNative(native,plan.lengths,({sid,mi,node,cursor})=>{
   if(node.localName!=='Spanner' || node.getAttribute('type')!=='HairPin')return;
   const own=children(node,'HairPin');need(own.length<=1,'Ambiguous native hairpin head');
   const direction=own.length?'next':'prev',other=own.length?'prev':'next';
   const links=children(node,direction);need(links.length===1 && children(node,other).length===0,'Ambiguous imported connector');
   const locations=children(links[0],'location');need(locations.length===1,'Missing imported connector location');
   const location=locations[0],delta=Number(field(location,'measures','0'));
   need(Number.isSafeInteger(delta) && Number(field(location,'staves','0'))===0,'Cross-staff or invalid imported connector');
   const here=absolute(sid,mi,cursor),there=absolute(sid,mi+delta,cursor.add(F.from(field(location,'fractions','0'))));
   const id=own.length?key(sid,here,there):key(sid,there,here),pairs=own.length?nextPairs:prevPairs;
   pairs.set(id,(pairs.get(id)||0)+1);
   if(own.length){
    const kind=field(own[0],'subtype','');need(kind==='0'||kind==='1','Unknown native hairpin subtype');
    const typed=JSON.stringify([id,Number(kind)]);if(!heads.has(typed))heads.set(typed,[]);heads.get(typed).push(own[0]);
   }
  });
  need(nextPairs.size===prevPairs.size && [...nextPairs].every(([id,count])=>prevPairs.get(id)===count),'Imported hairpin reciprocal endpoints differ');
  // At absolute score start the bundled renderer can extend a zero raw
  // imported interval to the first voice's first chord/rest end. Admit only
  // that observed, exact endpoint, never a nearest-onset or array-rank guess.
  const initialEnds=new Map();
  HairpinIdentity.walkNative(native,plan.lengths,({sid,mi,vi,node,cursor,duration,grace})=>{
   if(mi!==0 || vi!==0 || cursor.compare(z)!==0 || grace || !['Chord','Rest'].includes(node.localName) || duration.compare(z)<=0)return;
   need(!initialEnds.has(sid),'Ambiguous initial native anchor');initialEnds.set(sid,duration);
  });
  const rebound=new Map();
  for(const [id,spans] of sources){
   let resolved=id;
   if(!exact && !heads.has(id)){
    const first=spans[0],begin=absolute(first.staff,first.importStartMeasure,first.importStart),end=absolute(first.staff,first.importEndMeasure,first.importEnd);
    const duration=initialEnds.get(first.staff);
    if(begin.compare(z)===0 && end.compare(z)===0 && duration){
     const alternative=JSON.stringify([key(first.staff,z,duration),first.subtype]);
     if(heads.has(alternative))resolved=alternative;
    }
   }
   if(!rebound.has(resolved))rebound.set(resolved,[]);rebound.get(resolved).push(...spans);
  }
  need(rebound.size===heads.size,'Native/source imported anchors differ');
  const bindings=new Map(),nativeHeads=[...heads.values()].flat();
  const numbered=nativeHeads.some(head=>children(head,'sourceMusicXmlNumber').length);
  const sourceNumber=value=>{need(typeof value==='string' && /^(?:[1-9]|1[0-6])$/.test(value),'Invalid source wedge identity');return value;};
  const nativeNumber=head=>sourceNumber(field(head,'sourceMusicXmlNumber',''));
  if(numbered){for(const head of nativeHeads)nativeNumber(head);for(const span of plan.spans)sourceNumber(span.sourceNumber);}
  for(const [id,spans] of rebound){
   const matches=heads.get(id);need(matches && matches.length===spans.length,'Native/source imported anchors differ');
   if(numbered){
    const owners=new Map();for(const head of matches){const number=nativeNumber(head);need(!owners.has(number),'Duplicate native wedge identity');owners.set(number,head);}
    const seen=new Set();for(const span of spans){const number=sourceNumber(span.sourceNumber);need(!seen.has(number) && owners.has(number),'Missing/duplicate source wedge identity');seen.add(number);bindings.set(span,owners.get(number));}
    need(seen.size===owners.size,'Unowned native wedge identity');
   }else{
    need(matches.length===1 || new Set(matches.map(serialize)).size===1,'Distinct native hairpin formatting has ambiguous imported anchors');
    for(const span of spans)bindings.set(span,matches[0]);
   }
  }
  return bindings;
 }
 function bindHeads(native,plan){
  const valid=[];let legacyError;
  for(const exact of [false,true]){
   try{valid.push({bindings:bindHeadsForProfile(native,plan,exact),profile:exact?'exact':'legacy'});}
   catch(failure){if(!exact)legacyError=failure;}
  }
  if(!valid.length)throw legacyError;
  const chosen=valid[0];
  // Both complete clock profiles may coincide. Distinct head assignments are
  // ambiguous even when their formatting happens to look the same.
  for(const profile of valid.slice(1))for(const span of plan.spans)
   need(profile.bindings.get(span)===chosen.bindings.get(span),'Conflicting complete native hairpin clock profiles');
  chosen.bindings.importProfile=chosen.profile;return chosen.bindings;
 }
 function repair(source,native,options={}){
  // The caller must pass the fresh import of this source; the identity check
  // verifies every pitched note and its exact clock before planning edits.
  const identity=HairpinIdentity.identify(source,native,options),candidate=native.cloneNode(true);
  const score=children(candidate.documentElement,'Score')[0],staffs=new Map(children(score,'Staff').map(s=>[s.getAttribute('id'),s]));
  const grouped=new Map(),beforeEvents=existingEvents(candidate),removed=[],added=[];
  for(const span of identity.plan.spans){if(!grouped.has(span.staff))grouped.set(span.staff,[]);grouped.get(span.staff).push(span);}
  const bindings=bindHeads(candidate,identity.plan),jobs=[];
  for(const [sid,staff] of staffs){
   const spans=grouped.get(sid)||[],expected=[0,0],observed=[0,0],headsByKind=[[],[]];
   for(const span of spans)expected[span.subtype]++;
   const spanners=Array.from(staff.querySelectorAll('Spanner')).filter(n=>n.getAttribute('type')==='HairPin');
   for(const node of spanners){
    const heads=children(node,'HairPin');need(heads.length<=1,'Ambiguous native hairpin head');
    if(heads.length){const fields=children(heads[0],'subtype');need(fields.length===1 && /^[01]$/.test(fields[0].textContent.trim()),'Unknown native hairpin subtype');const kind=Number(fields[0].textContent.trim());observed[kind]++;headsByKind[kind].push(heads[0]);need(children(node,'next').length===1 && children(node,'prev').length===0,'Ambiguous native hairpin start');}
    else need(children(node,'prev').length===1 && children(node,'next').length===0,'Ambiguous native hairpin endpoint');
   }
   // Do not delete native-only/custom spanners or guess which duplicate an
   // incomplete import belonged to. Such a score requires a stronger binding.
   need(JSON.stringify(expected)===JSON.stringify(observed),'Native/source hairpin ownership differs');
   need(spanners.length===2*spans.length,'Native hairpin connector count differs');
   if(!spans.length)continue;
   const measures=children(staff,'Measure'),first=Math.min(...spans.map(s=>s.startMeasure)),last=Math.max(...spans.map(s=>s.endMeasure));
   const voiceIndex=Math.max(...measures.slice(first,last+1).map(m=>children(m,'voice').length));
   const anchored=[];let directNative=false;
   if(voiceIndex>=4){
    // Four occupied voices need no new timing carrier when a source endpoint
    // already has a real chord/rest anchor. Use the same native voice at both
    // ends; never split a note/rest or manufacture a percussion identity.
    const anchors=new Map();
    HairpinIdentity.walkNative(candidate,identity.plan.lengths,({sid:owner,mi,vi,node,cursor,duration,grace})=>{
     if(owner!==sid || grace || !['Chord','Rest'].includes(node.localName) || duration.compare(z)<=0)return;
     const key=JSON.stringify([mi,cursor.toString()]);if(!anchors.has(key))anchors.set(key,new Map());
     const voices=anchors.get(key);need(!voices.has(vi),'Ambiguous occupied voice onset');voices.set(vi,node);
    });
    for(const span of spans){
     const begin=anchors.get(JSON.stringify([span.startMeasure,F.from(span.start).toString()]));
     const end=anchors.get(JSON.stringify([span.endMeasure,F.from(span.end).toString()]));
     const shared=begin && end ? [...begin.keys()].filter(vi=>end.has(vi)).sort((a,b)=>a-b):[];
     if(!shared.length && bindings.importProfile==='exact'){directNative=true;break;}
     need(shared.length>0,'No existing same-voice endpoint anchors; retain original score');
     anchored.push({span,startNode:begin.get(shared[0]),endNode:end.get(shared[0])});
    }
   }
   jobs.push({sid,staff,spans,spanners,measures,first,last,voiceIndex,headsByKind,anchored,directNative});
  }
  for(const job of jobs){
   // A verified exact native span can use the renderer's fraction anchors.
   // Keep every original voice and reciprocal connector intact in that case.
   if(job.directNative)continue;
   const {sid,spans,spanners,measures,first,last,voiceIndex,headsByKind,anchored}=job,events=new Map();
   if(voiceIndex>=4){
    for(const node of spanners){removed.push(serialize(node));node.remove();}
    for(const {span,startNode,endNode} of anchored){
     const start=F.from(span.start),end=F.from(span.end);
     for(const event of [
      {node:startNode,direction:'next',measures:span.endMeasure-span.startMeasure,fraction:end.subtract(start),head:bindings.get(span)},
      {node:endNode,direction:'prev',measures:span.startMeasure-span.endMeasure,fraction:start.subtract(end)}]){
      const spanner=candidate.createElement('Spanner');spanner.setAttribute('type','HairPin');
      if(event.head)spanner.appendChild(event.head.cloneNode(true));
      const location=append(append(spanner,event.direction),'location');if(event.measures)append(location,'measures',event.measures);append(location,'fractions',event.fraction.toString());
      event.node.parentNode.insertBefore(spanner,event.node);
     }
    }
    continue;
   }
   const addEvent=(measure,point,event)=>{if(!events.has(measure))events.set(measure,new Map());const positions=events.get(measure),key=point.toString();if(!positions.has(key))positions.set(key,[]);positions.get(key).push(event);};
   for(const span of spans){
    const start=F.from(span.start),end=F.from(span.end);
    addEvent(span.startMeasure,start,{direction:'next',measures:span.endMeasure-span.startMeasure,fraction:end.subtract(start),head:bindings.get(span)});
    addEvent(span.endMeasure,end,{direction:'prev',measures:span.startMeasure-span.endMeasure,fraction:start.subtract(end)});
   }
   for(const node of spanners){removed.push(serialize(node));node.remove();}
   for(let mi=first;mi<=last;mi++){
    const measure=measures[mi];while(children(measure,'voice').length<=voiceIndex)append(measure,'voice');
    const carrier=children(measure,'voice')[voiceIndex],positions=events.get(mi)||new Map();
    const sorted=Array.from(positions.keys()).map(F.from).sort((a,b)=>a.compare(b));
    const length=F.from(identity.plan.lengths[sid][mi]);if(!sorted.some(p=>p.compare(length)===0))sorted.push(length);
    let cursor=z;
    for(const position of sorted){
     if(position.compare(cursor)>0){
      const rest=append(carrier,'Rest');append(rest,'visible','0');append(rest,'durationType','quarter');append(rest,'duration',position.subtract(cursor).toString());added.push(rest);
     }
     cursor=position;
     for(const event of positions.get(position.toString())||[]){
      const spanner=append(carrier,'Spanner');spanner.setAttribute('type','HairPin');
      if(event.head)spanner.appendChild(event.head.cloneNode(true));
      const location=append(append(spanner,event.direction),'location');if(event.measures)append(location,'measures',event.measures);append(location,'fractions',event.fraction.toString());
     }
    }
   }
  }
  const newRests=new Set(added);
  const preserved=Array.from(candidate.querySelectorAll('Chord, Rest')).filter(n=>!newRests.has(n)).map(serialize);
  need(JSON.stringify(beforeEvents)===JSON.stringify(preserved),'Original chord/rest content changed');
  const after=HairpinIdentity.identify(source,candidate,options);need(after.noteCount===identity.noteCount,'Note parity changed');
  return {document:candidate,proof:{mappedNotes:identity.noteCount,unpitchedDisplayNotes:identity.unpitchedNotes,repairedHairpins:identity.plan.spans.length,hiddenRests:added.length,carrierVoices:jobs.filter(j=>j.voiceIndex<4).map(j=>({staff:j.sid,voiceIndex:j.voiceIndex,firstMeasure:j.first,lastMeasure:j.last})),removedNativeConnectors:removed.length,originalChordRestContentPreserved:true,sourceUnchanged:true,originalNativeUnchanged:true},plan:identity.plan};
 }
 globalThis.HairpinNative=Object.freeze({repair});
})();
