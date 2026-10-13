// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Interval lanes and continuation constraints for source-owned hairpins.
(() => {
 'use strict';
 const F=HairpinSource.Fraction,z=F.from(0),need=(v,m)=>{if(!v)throw Error(m);};
 const children=(n,name)=>Array.from(n.children).filter(c=>c.localName===name);
 const field=(n,name,fallback)=>{const found=children(n,name);need(found.length<=1,'Duplicate '+name);return found.length?found[0].textContent.trim():fallback;};
 const finite=(value,label)=>{need(String(value).trim()!=='' && Number.isFinite(Number(value)),'Invalid '+label);return Number(value);};
 const key=(sid,start,end,kind)=>JSON.stringify([sid,start.toString(),end.toString(),kind]);
 const append=(parent,name,text)=>{const n=parent.ownerDocument.createElement(name);n.textContent=String(text);parent.appendChild(n);return n;};
 // cloneNode loses an XML Document's declaration/encoding metadata. Preserve
 // that metadata too, so a no-op continuation pass is byte-stable on export.
 const cloneDocument=document=>new DOMParser().parseFromString(new XMLSerializer().serializeToString(document),'application/xml');
 function timing(plan){
  const totals={},cumulative=Object.fromEntries(Object.entries(plan.lengths).map(([sid,lens])=>{let total=z;const starts=lens.map(value=>{const start=total;total=total.add(F.from(value));return start;});totals[sid]=total;return [sid,starts];}));
  const absolute=(sid,mi,point)=>{need(cumulative[sid] && Number.isSafeInteger(mi) && mi>=0 && mi<=cumulative[sid].length,'Invalid hairpin measure');if(mi===cumulative[sid].length){need(point.compare(z)===0,'Endpoint past score end');return totals[sid];}return cumulative[sid][mi].add(point);};
  return {absolute};
 }
 function findOwned(native,plan){
  const {absolute}=timing(plan),wanted=new Map(),found=new Map(),groups=new Map();
  const sourceNumber=value=>{need(typeof value==='string' && /^(?:[1-9]|1[0-6])$/.test(value),'Missing/invalid source wedge identity');return value;};
  for(const span of plan.spans){
   const id=key(span.staff,absolute(span.staff,span.startMeasure,F.from(span.start)),absolute(span.staff,span.endMeasure,F.from(span.end)),span.subtype);
   if(!groups.has(id))groups.set(id,[]);groups.get(id).push(span);
  }
  const ownedKey=(id,number)=>groups.get(id).length>1?JSON.stringify([id,sourceNumber(number)]):id;
  for(const span of plan.spans){
   const start=absolute(span.staff,span.startMeasure,F.from(span.start)),end=absolute(span.staff,span.endMeasure,F.from(span.end));
   need(start.compare(end)<0,'Nonpositive hairpin interval');
   const base=key(span.staff,start,end,span.subtype),id=ownedKey(base,span.sourceNumber);need(!wanted.has(id),'Duplicate exact source hairpin');wanted.set(id,{...span,start,end,id});
  }
  HairpinIdentity.walkNative(native,plan.lengths,({sid,mi,node,cursor})=>{
   if(node.localName!=='Spanner' || node.getAttribute('type')!=='HairPin')return;
   const heads=children(node,'HairPin');if(!heads.length)return;need(heads.length===1,'Ambiguous native head');
   const next=children(node,'next');need(next.length===1,'Missing native hairpin endpoint');const locations=children(next[0],'location');need(locations.length===1,'Missing native endpoint location');
   const delta=finite(field(locations[0],'measures','0'),'measure delta');need(Number.isSafeInteger(delta),'Fractional measure delta');
   need(finite(field(locations[0],'staves','0'),'staff delta')===0,'Cross-staff hairpin endpoint needs explicit mapping');
   const start=absolute(sid,mi,cursor),end=absolute(sid,mi+delta,cursor.add(F.from(field(locations[0],'fractions','0'))));
   const subtype=finite(field(heads[0],'subtype',''),'subtype');need(subtype===0 || subtype===1,'Unknown hairpin subtype');
   const base=key(sid,start,end,subtype);if(!groups.has(base))return;
   const number=field(heads[0],'sourceMusicXmlNumber',null),id=ownedKey(base,number);need(wanted.has(id),'Unowned native wedge identity');
   if(number!==null)need(sourceNumber(number)===sourceNumber(wanted.get(id).sourceNumber),'Native/source wedge identity differs');
   need(!found.has(id),'Duplicate native hairpin ownership');const rawPlacement=field(heads[0],'placement','1'),placement=({'0':'0','1':'1',above:'0',below:'1'})[rawPlacement];need(placement!==undefined,'Unknown hairpin placement');
   found.set(id,{...wanted.get(id),head:heads[0],placement});
  });
  need(found.size===wanted.size,'Source hairpin changed during layout');return found;
 }
 function style(values){
  const result={};for(const name of ['minimum','height','continuationHeight','lineWidth']){result[name]=finite(values?.[name],name);need(result[name]>=0 && result[name]<100,'Out-of-range '+name);}
  need(result.height>0 && result.lineWidth>0,'Nonpositive hairpin size');
  // One staff space of clearance, plus both strokes, prevents a larger custom
  // wedge from consuming the separation established by the default fixture.
  result.separation=Math.max(2.5,Math.max(result.height,result.continuationHeight)+2*result.lineWidth+1);
  return result;
 }
 function styleFromDocument(document){
  need(document.documentElement?.localName==='museScore' && !document.getElementsByTagName('parsererror').length,'Invalid exported style');
  const styles=children(document.documentElement,'Style');need(styles.length===1,'Expected one exported style');
  return style({minimum:field(styles[0],'hairpinMinDistance',''),height:field(styles[0],'hairpinHeight',''),continuationHeight:field(styles[0],'hairpinContHeight',''),lineWidth:field(styles[0],'hairpinWidth','')});
 }
 function minimum(head){return Math.max(0,...children(head,'Segment').flatMap(segment=>children(segment,'minDistance').map(n=>finite(n.textContent,'segment minimum'))));}
 function apply(head,floor){
  let changes=0,segments=children(head,'Segment');
  if(!segments.length){const segment=append(head,'Segment','');append(segment,'subtype','0');segments=[segment];}
  for(const segment of segments){
   const existing=children(segment,'minDistance');need(existing.length<=1,'Ambiguous segment floor');
   const previous=existing.length?finite(existing[0].textContent,'existing minimum'):-Infinity;
   if(previous>=floor)continue;
   const text=String(Number(floor.toFixed(9)));if(existing.length)existing[0].textContent=text;else append(segment,'minDistance',text);changes++;
  }
  return changes;
 }
 function place(native,plan,values){
  const candidate=cloneDocument(native),resolved=style(values),owned=findOwned(candidate,plan),groups=new Map();
  for(const item of owned.values()){const group=JSON.stringify([item.staff,item.placement]);if(!groups.has(group))groups.set(group,[]);groups.get(group).push(item);}
  const constraints=[],lanes=[];let changedFields=0;
  for(const items of groups.values()){
   items.sort((a,b)=>a.start.compare(b.start)||a.end.compare(b.end)||a.subtype-b.subtype);
   const base=Math.max(resolved.minimum,...items.map(item=>minimum(item.head))),ends=[];
   for(const item of items){
    let lane=ends.findIndex(end=>end.compare(item.start)<=0);if(lane<0)lane=ends.length;ends[lane]=item.end;
    lanes.push({key:item.id,lane,placement:item.placement});
    if(lane){const floor=base+lane*resolved.separation;changedFields+=apply(item.head,floor);constraints.push({key:item.id,floor});}
   }
  }
  return {document:candidate,proof:{constraints,lanes,resolvedStyle:resolved,changedFields}};
 }
 function propagate(native,plan,proof){
  const candidate=cloneDocument(native),owned=findOwned(candidate,plan),seen=new Set();let changedFields=0;
  for(const constraint of proof.constraints){need(!seen.has(constraint.key) && owned.has(constraint.key),'Missing/duplicate managed hairpin');seen.add(constraint.key);const floor=finite(constraint.floor,'managed floor');need(floor>=0 && floor<300,'Invalid managed floor');changedFields+=apply(owned.get(constraint.key).head,floor);}
  return {document:candidate,changedFields};
 }
 globalThis.HairpinLayout=Object.freeze({place,propagate,style,styleFromDocument});
})();
