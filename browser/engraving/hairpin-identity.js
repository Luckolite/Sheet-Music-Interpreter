// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Source-bound note and instrument identity for native engraving.
(() => {
  'use strict';
  const F = HairpinSource.Fraction, z = F.from(0);
  const need = (value,message) => {if(!value)throw Error(message);};
  const nodes = (parent,name) => Array.from(parent.children).filter(n=>n.localName===name);
  const only = (parent,name,fallback=null) => {const found=nodes(parent,name);need(found.length<=1,'Duplicate '+name);return found.length?found[0].textContent.trim():fallback;};
  const number = value => {need(/^[+-]?\d+$/.test(String(value)),'Expected integer');const n=Number(value);need(Number.isSafeInteger(n),'Inexact integer');return n;};
  const durationTypes = Object.freeze({long:'4',breve:'2',whole:'1',half:'1/2',quarter:'1/4',eighth:'1/8','16th':'1/16','32nd':'1/32','64th':'1/64','128th':'1/128','256th':'1/256','512th':'1/512','1024th':'1/1024'});
  const graceTags = ['appoggiatura','acciaccatura','grace4','grace16','grace32','grace8after','grace16after','grace32after'];
  const signature = (measure,start,duration,pitch,grace) => JSON.stringify([measure,start.toString(),duration.toString(),pitch,grace]);
  const steps={C:0,D:2,E:4,F:5,G:7,A:9,B:11},tpcs={C:14,D:16,E:18,F:13,G:15,A:17,B:19};
  function carrierOwners(source,native,proof){
    const owners=new Map();if(proof==null)return owners;
    need([...source.querySelectorAll('encoding > software')].some(n=>n.textContent==='Music Sheets'),'Carrier identity requires own generated source');
    need(!source.getElementsByTagName('midi-unpitched').length,'Percussion requires instrument identity, not display carriers');
    const notes=Array.from(native.getElementsByTagName('Note'));
    need(Number.isSafeInteger(proof.count) && proof.count===notes.length && Array.isArray(proof.owners),'Invalid carrier proof');
    for(const owner of proof.owners){
      need(Number.isSafeInteger(owner.index) && owner.index>=0 && owner.index<notes.length && !owners.has(notes[owner.index]),'Invalid/duplicate carrier owner');
      const note=notes[owner.index],m=owner.marker;need(m && Object.hasOwn(steps,m.step),'Unknown carrier display step');
      const octave=number(m.octave),position=(octave+1)*12+steps[m.step];
      need(octave>=-1 && octave<=9 && position>=0 && position<=127 && m.position===position && m.tpc===tpcs[m.step],'Invalid carrier display identity');
      need(typeof m.voice==='string' && m.voice.length>0 && typeof m.staff==='string' && m.staff.length>0 && typeof m.type==='string' && typeof m.chord==='boolean','Invalid carrier source context');
      need(number(only(note,'pitch'))===position && number(only(note,'tpc'))===m.tpc && only(note,'head')==='cross' && !nodes(note,'color').length,'Native carrier display changed');
      const accidentals=nodes(note,'Accidental');
      need(Number.isSafeInteger(owner.accidentalCount) && owner.accidentalCount>=0 && owner.accidentalCount<=1 && accidentals.length===owner.accidentalCount && accidentals.every(a=>only(a,'subtype')==='accidentalNatural' && only(a,'visible')==='0'),'Carrier accidental visibility changed');
      owners.set(note,m);
    }
    return owners;
  }
  // MuseScore serializes the collapsed multi-rest graphic after its first real bar.
  // Its underlying silent bars are retained and visited; the graphic owns no timeline.
  function logicalMeasures(staff,lens) {
    lens=lens.map(F.from);
    const measures=[],graphics=[];
    for(const measure of nodes(staff,'Measure')) {
      const markers=nodes(measure,'multiMeasureRest');
      if(!markers.length){measures.push(measure);continue;}
      need(markers.length===1,'Duplicate multi-rest graphic marker');
      const count=number(markers[0].textContent.trim());need(count>=2&&count<=lens.length,'Invalid multi-rest graphic span');
      const start=measures.length-1;need(start>=0&&start+count<=lens.length,'Orphan multi-rest graphic');
      need(Array.from(measure.children).every(n=>['multiMeasureRest','voice'].includes(n.localName)),'Multi-rest graphic contains independent data');
      const voices=nodes(measure,'voice');need(voices.length===1,'Multi-rest graphic needs one display voice');
      need(Array.from(voices[0].children).every(n=>['Rest','BarLine'].includes(n.localName)),'Multi-rest graphic contains musical events');
      const rests=nodes(voices[0],'Rest');need(rests.length===1&&only(rests[0],'durationType')==='measure','Multi-rest graphic needs one display rest');
      need(Array.from(rests[0].children).every(n=>['durationType','duration'].includes(n.localName)),'Unsupported multi-rest display rest');
      const total=lens.slice(start,start+count).reduce((a,b)=>a.add(b),z);
      need(F.from(measure.getAttribute('len')||'0').compare(total)===0&&F.from(only(rests[0],'duration','0')).compare(total)===0,'Multi-rest graphic duration mismatch');
      need(!graphics.some(g=>start<g.start+g.count&&g.start<start+count),'Overlapping multi-rest graphics');
      graphics.push({start,count});
    }
    need(measures.length===lens.length,'Native measure count changed');
    for(const graphic of graphics)for(const measure of measures.slice(graphic.start,graphic.start+graphic.count)) {
      need(!measure.getElementsByTagName('Chord').length,'Multi-rest graphic covers sounding notes');
      need(nodes(measure,'voice').length>0&&Array.from(measure.getElementsByTagName('Rest')).length>0,'Multi-rest graphic lacks underlying silence');
    }
    return measures;
  }
  function walkNative(native,lengths,visit) {
    const scores=nodes(native.documentElement,'Score');need(scores.length===1,'Expected one native score');
    const seen=new Set();
    for(const staff of nodes(scores[0],'Staff')){
      const sid=staff.getAttribute('id');need(Object.hasOwn(lengths,sid) && !seen.has(sid),'Unknown native staff');seen.add(sid);
      const lens=lengths[sid].map(F.from),measures=logicalMeasures(staff,lens);
      need(measures.length===lens.length,'Native measure count changed');
      measures.forEach((measure,mi)=>{
        const explicit=measure.getAttribute('len');if(explicit)need(F.from(explicit).compare(lens[mi])===0,'Native measure length changed');
        const voices=nodes(measure,'voice');need(voices.length<=4,'Invalid native voices');
        voices.forEach((voice,vi)=>{
          let cursor=z;const ratios=[];
          for(const node of Array.from(voice.children)){
            if(node.localName==='Tuplet'){
              const normal=number(only(node,'normalNotes')),actual=number(only(node,'actualNotes'));need(normal>0 && actual>0,'Invalid tuplet');ratios.push(new F(normal,actual));
            } else if(node.localName==='endTuplet'){need(ratios.length>0,'Unmatched native tuplet');ratios.pop();}
            else if(node.localName==='location'){
              need(number(only(node,'measures','0'))===0,'Cross-measure native cursor');cursor=cursor.add(F.from(only(node,'fractions','0')));need(cursor.compare(z)>=0,'Native cursor underflow');
            } else if(node.localName==='Chord' || node.localName==='Rest'){
              const explicitDuration=only(node,'duration'),type=only(node,'durationType');let duration;
              if(explicitDuration!==null)duration=F.from(explicitDuration);
              else if(type==='measure')duration=lens[mi];
              else {need(Object.hasOwn(durationTypes,type),'Unknown native duration');duration=F.from(durationTypes[type]);const dots=number(only(node,'dots','0'));need(dots>=0 && dots<=8,'Unsupported dot count');let increment=duration;for(let i=0;i<dots;i++){increment=increment.multiply('1/2');duration=duration.add(increment);}}
              for(const ratio of ratios)duration=duration.multiply(ratio);
              const grace=graceTags.some(tag=>nodes(node,tag).length>0);if(grace)duration=z;
              need(duration.compare(z)>=0 && cursor.add(duration).compare(lens[mi])<=0,'Native duration outside measure');
              visit({sid,mi,vi,node,cursor,duration,grace});cursor=cursor.add(duration);
            } else visit({sid,mi,vi,node,cursor,duration:z,grace:false});
          }
          need(ratios.length===0,'Unclosed native tuplet');
        });
      });
    }
    need(seen.size===Object.keys(lengths).length,'Missing native staff');
  }
  // Native4.00 stores a pitch before its staff Ottava playback offset.
  // Keep that stored/display value intact; project only this identity comparison.
  function nativeOttavaTimeline(source,native,lengths,mapping) {
    const all=Array.from(native.getElementsByTagName('Spanner')).filter(n=>n.getAttribute('type')==='Ottava');
    const allHeads=Array.from(native.getElementsByTagName('Ottava'));
    const sourceMarks=Array.from(source.getElementsByTagName('octave-shift'));
    if(!all.length&&!allHeads.length&&!sourceMarks.length)return {active:false,offset:()=>0};
    need(native.documentElement.getAttribute('version')==='4.00','Unqualified native Ottava format');
    need(!['Capo','capo','capoFret','Drumset'].some(n=>native.getElementsByTagName(n).length)
      && !Array.from(native.getElementsByTagName('useDrumset')).some(n=>number(n.textContent.trim())!==0),
      'Native Ottava with capo/percussion needs separate pitch identity');
    const named=['8va','8vb','15ma','15mb','22ma','22mb'],shifts=[12,-12,24,-24,36,-36];
    const style=new Set(['subtype','numbersOnly','placement','color','visible','autoplace','offset','minDistance',
      'lineWidth','lineStyle','lineVisible','beginText','continueText','endText','beginHookType','endHookType',
      'beginHookHeight','endHookHeight','beginFontFace','continueFontFace','endFontFace','beginFontSize',
      'continueFontSize','endFontSize','beginFontStyle','continueFontStyle','endFontStyle','beginTextAlign',
      'continueTextAlign','endTextAlign','beginTextPlace','continueTextPlace','endTextPlace','beginTextOffset',
      'continueTextOffset','endTextOffset','dashLineLen','dashGapLen','Segment']);
    const attrs=(n,allowed)=>need(Array.from(n.attributes).every(a=>allowed.includes(a.name)),'Unknown native Ottava attribute');
    const starts={},lens={};
    for(const [sid,values]of Object.entries(lengths)){lens[sid]=values.map(F.from);let at=z;starts[sid]=[at];for(const v of lens[sid]){at=at.add(v);starts[sid].push(at);}}
    const absolute=(sid,mi,point)=>{need(Object.hasOwn(lens,sid)&&Number.isSafeInteger(mi)&&mi>=0&&mi<=lens[sid].length,'Native Ottava measure outside score');const p=F.from(point);need(p.compare(z)>=0&&(mi===lens[sid].length?p.compare(z)===0:p.compare(lens[sid][mi])<=0),'Native Ottava endpoint outside measure');return starts[sid][mi].add(p);};
    const sourceStaffs=new Map(mapping.map(m=>[JSON.stringify([m.partId,m.sourceStaff]),m.nativeStaff]));
    const sourceSpans=[],sourceVisited=new Set();
    for(const part of nodes(source.documentElement,'part')){
      let divisions=null;const active=new Map();
      nodes(part,'measure').forEach((measure,mi)=>{
        let cursor=z;
        for(const node of Array.from(measure.children)){
          if(node.localName==='attributes'){const d=only(node,'divisions');if(d!==null){divisions=number(d);need(divisions>0,'Invalid Ottava source divisions');}}
          else if(['note','backup','forward'].includes(node.localName)){
            need(divisions!==null,'Ottava source clock lacks divisions');if(node.localName==='note'&&(nodes(node,'chord').length||nodes(node,'grace').length))continue;
            const duration=new F(only(node,'duration',''),4*divisions);need(duration.compare(z)>0,'Invalid Ottava source duration');cursor=node.localName==='backup'?cursor.subtract(duration):cursor.add(duration);need(cursor.compare(z)>=0,'Ottava source backup underflow');
          }else if(node.localName==='direction')for(const dt of nodes(node,'direction-type'))for(const mark of nodes(dt,'octave-shift')){
            sourceVisited.add(mark);attrs(mark,['type','size','number','id','color','dash-length','space-length','default-x','default-y','relative-x','relative-y','font-family','font-size','font-style','font-weight']);
            need(mark.children.length===0&&divisions!==null,'Unknown Ottava source content');
            const sid=sourceStaffs.get(JSON.stringify([part.getAttribute('id'),only(node,'staff','1')]));need(sid!==undefined,'Unmapped Ottava source staff');
            const point=cursor.add(new F(only(node,'offset','0'),4*divisions)),at=absolute(sid,mi,point);
            const nr=number(mark.getAttribute('number')||'1');need(nr>=1&&nr<=16,'Ottava source number outside range');
            const type=mark.getAttribute('type'),prior=active.get(nr),sizeText=mark.getAttribute('size');
            const size=sizeText===null?(prior?prior.size:8):number(sizeText);need([8,15,22].includes(size),'Unknown Ottava source size');
            if(type==='up'||type==='down'){need(!prior,'Duplicate/overlapping Ottava source number');active.set(nr,{sid,start:at,size,shift:(type==='up'?-1:1)*12*((size-1)/7),continuations:[]});}
            else if(type==='continue'){need(prior&&prior.sid===sid&&prior.size===size&&at.compare(prior.start)>=0,'Contradictory Ottava source continuation');prior.continuations.push(at);}
            else if(type==='stop'){need(prior&&prior.sid===sid&&prior.size===size&&at.compare(prior.start)>0&&prior.continuations.every(p=>p.compare(at)<=0),'Unmatched/nonpositive Ottava source stop');sourceSpans.push({...prior,end:at});active.delete(nr);}
            else throw Error('Unknown Ottava source type');
          }
        }
      });need(active.size===0,'Unclosed Ottava source span');
    }
    need(sourceVisited.size===sourceMarks.length&&sourceMarks.every(n=>sourceVisited.has(n)),'Unvisited Ottava source representation');
    const endpoint=(link,sid,mi,vi,cursor)=>{
      attrs(link,[]);need(link.children.length===1&&link.children[0].localName==='location','Native Ottava needs one location');
      const l=link.children[0];attrs(l,[]);need(Array.from(l.children).every(n=>['measures','fractions','staves','voices','notes'].includes(n.localName)),'Unknown native Ottava location');
      need(number(only(l,'staves','0'))===0&&number(only(l,'notes','0'))===0,'Cross-staff/note native Ottava endpoint');
      const voice=vi+number(only(l,'voices','0'));need(voice>=0&&voice<4,'Native Ottava voice outside staff');
      return {voice,at:absolute(sid,mi+number(only(l,'measures','0')),cursor.add(F.from(only(l,'fractions','0'))))};
    };
    const key=(sid,a,at,b,bt)=>JSON.stringify([sid,a,at.toString(),b,bt.toString()]);
    const heads=new Map(),tails=new Map(),visited=new Set(),seenHeads=new Set(),events=new Map();
    walkNative(native,lengths,({sid,mi,vi,node,cursor,duration,grace})=>{
      if(node.localName==='Chord'||node.localName==='Rest'){
        const k=JSON.stringify([sid,mi,vi]);if(!events.has(k))events.set(k,[]);events.get(k).push({sid,mi,vi,node,at:absolute(sid,mi,cursor),duration,grace});
      }
      if(node.localName!=='Spanner'||node.getAttribute('type')!=='Ottava')return;
      visited.add(node);attrs(node,['type']);need(Array.from(node.children).every(n=>['Ottava','next','prev'].includes(n.localName)),'Unknown native Ottava connector');
      const h=nodes(node,'Ottava'),next=nodes(node,'next'),prev=nodes(node,'prev'),at=absolute(sid,mi,cursor);
      if(h.length){
        need(h.length===1&&next.length===1&&prev.length===0,'Ambiguous native Ottava head');attrs(h[0],[]);
        need(Array.from(h[0].children).every(n=>style.has(n.localName)),'Unknown native Ottava property');
        const value=only(h[0],'subtype','');const index=named.includes(value)?named.indexOf(value):/^[0-5]$/.test(value)?Number(value):-1;need(index>=0,'Unknown native Ottava subtype');
        const end=endpoint(next[0],sid,mi,vi,cursor);need(end.at.compare(at)>0,'Nonpositive native Ottava span');
        const k=key(sid,vi,at,end.voice,end.at);need(!heads.has(k),'Duplicate native Ottava head');heads.set(k,{sid,start:at,end:end.at,shift:shifts[index]});seenHeads.add(h[0]);
      }else{
        need(next.length===0&&prev.length===1,'Ambiguous native Ottava tail');const begin=endpoint(prev[0],sid,mi,vi,cursor);
        const k=key(sid,begin.voice,begin.at,vi,at);need(!tails.has(k),'Duplicate native Ottava tail');tails.set(k,true);
      }
    });
    need(visited.size===all.length&&all.every(n=>visited.has(n))&&seenHeads.size===allHeads.length&&allHeads.every(n=>seenHeads.has(n)),'Unvisited native Ottava representation');
    need(heads.size===tails.size&&Array.from(heads.keys()).every(k=>tails.has(k)),'Native Ottava reciprocal endpoints differ');
    const spanKey=s=>JSON.stringify([s.sid,s.start.toString(),s.end.toString(),s.shift]);
    const expectedSpans=sourceSpans.map(spanKey).sort(),actualSpans=Array.from(heads.values()).map(spanKey).sort();
    need(JSON.stringify(expectedSpans)===JSON.stringify(actualSpans),'Source/native Ottava span identity differs');
    const intervals=new Map();for(const s of heads.values()){if(!intervals.has(s.sid))intervals.set(s.sid,[]);intervals.get(s.sid).push(s);}
    for(const rows of intervals.values()){rows.sort((a,b)=>a.start.compare(b.start));for(let i=1;i<rows.length;i++)need(rows[i-1].end.compare(rows[i].start)<=0,'Overlapping native Ottava spans');}
    const at=(sid,p)=>{const rows=intervals.get(sid)||[];let lo=0,hi=rows.length;while(lo<hi){const m=(lo+hi)>>1;if(rows[m].start.compare(p)<=0)lo=m+1;else hi=m;}return lo>0&&rows[lo-1].end.compare(p)>0?rows[lo-1].shift:0;};
    const offsets=new Map();
    for(const rows of events.values()){
      const previousMetric=[],nextMetric=[];let previous=-1,next=rows.length;
      for(let i=0;i<rows.length;i++){previousMetric[i]=previous;if(!rows[i].grace)previous=i;}
      for(let i=rows.length-1;i>=0;i--){nextMetric[i]=next;if(!rows[i].grace)next=i;}
      for(let i=0;i<rows.length;i++){
      const e=rows[i];if(e.node.localName!=='Chord')continue;const shift=at(e.sid,e.at);
      const tags=Array.from(e.node.children).filter(n=>/^(?:grace|appoggiatura|acciaccatura)/.test(n.localName));
      need(tags.every(n=>graceTags.includes(n.localName)),'Unknown native grace/Ottava role');
      if(e.grace){
        need(tags.length===1,'Ambiguous native grace/Ottava role');const after=tags[0].localName.endsWith('after');
        const previous=previousMetric[i],next=nextMetric[i];
        const principal=after?(previous>=0?rows[previous]:null):(next<rows.length?rows[next]:null);
        need(principal&&principal.node.localName==='Chord','Native grace/Ottava principal is unavailable');
        // Accept only when cursor and both possible adjacent metric anchors agree.
        // At an ambiguous boundary retain refusal instead of guessing attachment.
        for(const k of [previous,next])if(k>=0&&k<rows.length)need(at(e.sid,rows[k].at)===shift,'Native grace crosses an ambiguous Ottava boundary');
      }
      offsets.set(e.node,shift);
      }
    }
    return {active:true,offset:node=>{need(offsets.has(node),'Unvisited native Ottava chord');return offsets.get(node);}};
  }
  function identify(source,native,options={}) {
    need(source.documentElement?.localName==='score-partwise' && native.documentElement?.localName==='museScore','Expected MusicXML and native score');
    need(!source.getElementsByTagName('parsererror').length && !native.getElementsByTagName('parsererror').length,'Invalid XML');
    const scores=nodes(native.documentElement,'Score');need(scores.length===1,'Expected one native score');
    const score=scores[0], parts=nodes(source.documentElement,'part'), nativeParts=nodes(score,'Part');
    need(parts.length>0 && parts.length===nativeParts.length,'Part count changed');
    const partLists=nodes(source.documentElement,'part-list');need(partLists.length===1,'Part list required');
    const declared=nodes(partLists[0],'score-part').map(n=>n.getAttribute('id'));
    need(JSON.stringify(declared)===JSON.stringify(parts.map(p=>p.getAttribute('id'))),'Part order needs explicit mapping');
    const allNative=nodes(score,'Staff'), nativeById=new Map(), mapping=[], groups=new Map(), sourceIds=new Set(), nativeTransposes=new Map();
    for(const staff of allNative){const id=staff.getAttribute('id');need(id && !nativeById.has(id),'Duplicate native staff');nativeById.set(id,staff);}
    for(let pi=0;pi<parts.length;pi++){
      const part=parts[pi], partId=part.getAttribute('id'), definitions=nodes(nativeParts[pi],'Staff');
      need(partId && !sourceIds.has(partId),'Duplicate source part');sourceIds.add(partId);
      let count=1;
      for(const measure of nodes(part,'measure'))for(const attr of nodes(measure,'attributes')){
        const staves=only(attr,'staves');if(staves!==null){const value=number(staves);need(value>0 && (count===1 || count===value),'Changing staff count');count=value;}
      }
      need(definitions.length===count,'Staff count changed');
      const ids=definitions.map(d=>d.getAttribute('id'));
      need(ids.every(id=>nativeById.has(id) && !groups.has(id)),'Native part/staff ambiguity');
      const instruments=nodes(nativeParts[pi],'Instrument');need(instruments.length<=1,'Ambiguous native instrument');
      const instrument=instruments[0], transpose=instrument?{
        chromatic:number(only(instrument,'transposeChromatic','0')),
        diatonic:number(only(instrument,'transposeDiatonic','0'))
      }:{chromatic:0,diatonic:0};
      need(Math.abs(transpose.chromatic)<=127 && Math.abs(transpose.diatonic)<=75,'Invalid native transposition');
      ids.forEach((id,index)=>{groups.set(id,ids);nativeTransposes.set(id,transpose);mapping.push({partId,sourceStaff:String(index+1),nativeStaff:id});});
    }
    need(groups.size===nativeById.size,'Unowned native staff');
    const plan=HairpinSource.plan(source,mapping), sourceEvents=new Map(mapping.map(m=>[m.nativeStaff,[]])), nativeEvents=new Map(mapping.map(m=>[m.nativeStaff,[]]));
    const lookup=new Map(mapping.map(m=>[JSON.stringify([m.partId,m.sourceStaff]),m.nativeStaff]));
    const carriers=carrierOwners(source,native,options.carriers);
    const sourceNotes=[],transposeEvents=new Map(mapping.map(m=>[m.nativeStaff,[]])),starts={};
    for(const [sid,lengths] of Object.entries(plan.lengths)){
      let total=z;starts[sid]=lengths.map(value=>{const at=total;total=total.add(F.from(value));return at;});
    }
    const absolute=(sid,mi,point)=>{need(starts[sid]?.[mi]!==undefined && point.compare(z)>=0 && point.compare(F.from(plan.lengths[sid][mi]))<=0,'Source transposition cursor outside measure');return starts[sid][mi].add(point);};
    for(const part of parts){
      let divisions=null;
      nodes(part,'measure').forEach((measure,mi)=>{
        let cursor=z;const preceding=new Map();
        for(const node of Array.from(measure.children)){
          if(node.localName==='attributes') {
            const d=only(node,'divisions');if(d!==null){divisions=number(d);need(divisions>0,'Invalid divisions');}
            for(const transpose of nodes(node,'transpose')){
              need(!nodes(transpose,'double').length,'Doubling transposition needs explicit note identity');
              const chromatic=number(only(transpose,'chromatic')),octave=number(only(transpose,'octave-change','0')),diatonicText=only(transpose,'diatonic');
              const value={chromatic:chromatic+12*octave,diatonic:diatonicText===null?null:number(diatonicText)+7*octave};
              need(Math.abs(value.chromatic)<=127 && (value.diatonic===null || Math.abs(value.diatonic)<=75),'Invalid source transposition');
              const staff=transpose.getAttribute('number');
              const targets=staff===null?mapping.filter(m=>m.partId===part.getAttribute('id')).map(m=>m.nativeStaff):[lookup.get(JSON.stringify([part.getAttribute('id'),String(number(staff))]))];
              need(targets.length>0 && targets.every(sid=>sid!==undefined),'Unmapped transposition staff');
              for(const sid of targets)transposeEvents.get(sid).push({at:absolute(sid,mi,cursor),value});
            }
          }
          else if(['note','backup','forward'].includes(node.localName)){
            need(divisions!==null,'Missing divisions');
            const grace=nodes(node,'grace').length>0;
            const duration=grace?z:new F(only(node,'duration',''),4*divisions);
            need(duration.compare(z)>=0 && (grace || duration.compare(z)>0),'Invalid duration');
            if(node.localName==='backup'){cursor=cursor.subtract(duration);need(cursor.compare(z)>=0,'Backup underflow');continue;}
            if(node.localName==='forward'){cursor=cursor.add(duration);continue;}
            const voice=only(node,'voice','1'), chord=nodes(node,'chord').length>0;
            const start=chord?preceding.get(voice):cursor;need(start instanceof F,'Orphan chord');
            if(!chord)preceding.set(voice,start);
            const sid=lookup.get(JSON.stringify([part.getAttribute('id'),only(node,'staff','1')]));need(sid!==undefined,'Unmapped note staff');
            if(!nodes(node,'rest').length){
              const unpitched=nodes(node,'unpitched'),pitched=nodes(node,'pitch');
              need(unpitched.length<=1 && (unpitched.length?pitched.length===0:pitched.length===1),'Ambiguous note kind');
              if(unpitched.length)need(carriers.size>0 && !grace && only(node,'notehead')==='x' && !nodes(node,'instrument').length && !nodes(node,'accidental').length,'Unpitched source needs verified display-carrier identity');
              const pitch=unpitched[0]||pitched[0],step=only(pitch,unpitched.length?'display-step':'step');
              need(Object.hasOwn(steps,step),'Unknown pitch step');
              const written=(number(only(pitch,unpitched.length?'display-octave':'octave'))+1)*12+steps[step]+(unpitched.length?0:number(only(pitch,'alter','0')));
              const display=unpitched.length?['unpitched-display',written,voice,only(node,'staff','1'),only(node,'type',''),chord]:null;
              sourceNotes.push({sid,mi,start,duration,written,grace,display});
            }
            if(!chord && !grace)cursor=cursor.add(duration);
          }
        }
      });
    }
    for(const events of transposeEvents.values()){
      events.sort((a,b)=>a.at.compare(b.at));
      for(let i=1;i<events.length;i++)if(events[i-1].at.compare(events[i].at)===0)
        need(JSON.stringify(events[i-1].value)===JSON.stringify(events[i].value),'Conflicting simultaneous source transposition');
    }
    let transposingNotes=0,unpitchedNotes=0;
    for(const note of sourceNotes){
      const {sid,mi,start,duration,written,grace,display}=note,at=absolute(sid,mi,start);
      let transpose={chromatic:0,diatonic:0};
      for(const event of transposeEvents.get(sid)){if(event.at.compare(at)>0)break;transpose=event.value;}
      const imported=nativeTransposes.get(sid);
      need(transpose.chromatic===imported.chromatic && (transpose.diatonic===null || transpose.diatonic===imported.diatonic),'Source/native instrument transposition differs');
      const sounding=written+transpose.chromatic;need(written>=0 && written<=127 && sounding>=0 && sounding<=127,'Pitch outside MIDI range');
      if(display){need(transpose.chromatic===0 && (transpose.diatonic===null || transpose.diatonic===0),'Display carriers cannot infer instrument transposition');unpitchedNotes++;}
      sourceEvents.get(sid).push(signature(mi,start,duration,display||sounding,grace));
      if(!display && transpose.chromatic)transposingNotes++;
    }
    need(unpitchedNotes===carriers.size,'Source/native display-carrier count differs');
    const ottava=nativeOttavaTimeline(source,native,plan.lengths,mapping);
    walkNative(native,plan.lengths,({sid,mi,node,cursor,duration,grace})=>{
      need(node.localName!=='InstrumentChange','Native instrument changes need timeline identity');
      if(node.localName!=='Chord')return;
      const ids=groups.get(sid),target=ids.indexOf(sid)+number(only(node,'staffMove','0'));need(target>=0 && target<ids.length,'Cross-part staff move');
      for(const note of nodes(node,'Note')){
        const carrier=carriers.get(note),shift=ottava.offset(node);
        need(!carrier||shift===0,'Unpitched display carrier needs an unshifted native Ottava timeline');
        const nativeStoredPitch=carrier?null:number(only(note,'pitch'));
        need(!ottava.active||carrier||nativeStoredPitch>=0&&nativeStoredPitch<=127,'Native stored pitch outside MIDI range');
        const nativeIdentityPitch=carrier?null:nativeStoredPitch+shift;
        need(!ottava.active||carrier||nativeIdentityPitch>=0&&nativeIdentityPitch<=127,'Native Ottava identity pitch outside MIDI range');
        const pitch=carrier?['unpitched-display',carrier.position,carrier.voice,carrier.staff,carrier.type,carrier.chord]:nativeIdentityPitch;
        nativeEvents.get(ids[target]).push(signature(mi,cursor,duration,pitch,grace));
      }
    });
    let noteCount=0;
    for(const sid of nativeById.keys()){
      const expected=sourceEvents.get(sid).sort(),actual=nativeEvents.get(sid).sort();
      need(JSON.stringify(expected)===JSON.stringify(actual),'Source/native note identity differs for staff '+sid);
      noteCount+=expected.length;
    }
    need(noteCount>0,'No note evidence for mapping');
    return {mapping,plan,noteCount,transposingNotes,unpitchedNotes,staffs:nativeById.size,parts:parts.length};
  }
  globalThis.HairpinIdentity=Object.freeze({identify,walkNative,logicalMeasures});
})();
