// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Remove owned scratch voices from the layout-only MusicXML payload. The
// renderer's coarse export grid can otherwise leave a later staff off by a tick.
(() => {
 'use strict';
 const need=(value,message)=>{if(!value)throw Error(message);};
 const children=(node,name)=>Array.from(node.children).filter(n=>n.localName===name);
 const field=(node,name,fallback)=>{const found=children(node,name);need(found.length<=1,'Duplicate layout '+name);return found.length?found[0].textContent.trim():fallback;};
 const xml=node=>new XMLSerializer().serializeToString(node);
 function removeCarriers(layout,originalNative,owners,lengths=null){
  need(Array.isArray(owners),'Missing carrier voice ownership');
  if(!owners.length)return {document:layout,proof:{removedCarrierRests:0,removedBackups:0,originalNotesPreserved:true}};
  const candidate=new DOMParser().parseFromString(xml(layout),'application/xml');need(!candidate.querySelector('parsererror'),'Invalid layout XML');
  const score=children(originalNative.documentElement,'Score')[0];need(score,'Missing original native score');
  const nativeParts=children(score,'Part'),parts=children(candidate.documentElement,'part');need(nativeParts.length===parts.length,'Layout part topology differs');
  const nativeStaffs=new Map(children(score,'Staff').map(s=>[s.getAttribute('id'),s])),owned=new Set(),remove=new Set();let rests=0,backups=0;
  const before=Array.from(candidate.querySelectorAll('note')).filter(n=>children(n,'rest').length===0).map(xml);
  for(const owner of owners){
   const {staff,voiceIndex,firstMeasure,lastMeasure}=owner;
   need(typeof staff==='string' && !owned.has(staff),'Duplicate carrier staff');owned.add(staff);
   need(Number.isSafeInteger(voiceIndex)&&voiceIndex>=0&&voiceIndex<4&&Number.isSafeInteger(firstMeasure)&&Number.isSafeInteger(lastMeasure)&&firstMeasure>=0&&lastMeasure>=firstMeasure,'Invalid carrier voice range');
   const locations=[];nativeParts.forEach((part,pi)=>children(part,'Staff').forEach((s,si)=>{if(s.getAttribute('id')===staff)locations.push({pi,si,count:children(part,'Staff').length});}));
   need(locations.length===1 && nativeStaffs.has(staff),'Unknown carrier staff');
   const {pi,si,count}=locations[0],measures=children(parts[pi],'measure');
   let original=children(nativeStaffs.get(staff),'Measure');
   if(lengths!==null){
    need(Object.hasOwn(lengths,staff)&&Array.isArray(lengths[staff]),'Missing logical native measure lengths');
    original=HairpinIdentity.logicalMeasures(nativeStaffs.get(staff),lengths[staff]);
   }else need(!original.some(m=>children(m,'multiMeasureRest').length),'Multi-rest carriers require logical ownership');
   need(measures.length===original.length && lastMeasure<measures.length,'Layout carrier measure range differs');
   const declared=parts[pi].querySelector('attributes > staves');need(Number(declared?declared.textContent:1)===count,'Layout staff topology differs');
   // The bundled exporter labels voices by their native track within the part.
   const voice=String(4*si+voiceIndex+1),staffNumber=String(si+1);
   for(let mi=firstMeasure;mi<=lastMeasure;mi++){
    need(children(original[mi],'voice').length<=voiceIndex,'Carrier voice was occupied in the original score');
    const measure=measures[mi],notes=children(measure,'note').filter(n=>field(n,'voice','1')===voice && field(n,'staff','1')===staffNumber);
    need(notes.length>0,'Carrier layout voice is missing');
    need(notes.every(n=>children(n,'rest').length===1 && children(n,'pitch').length===0 && children(n,'unpitched').length===0 && children(n,'grace').length===0 && children(n,'chord').length===0 && n.getAttribute('print-object')==='no'),'Carrier layout voice contains original notation');
    let preceding=notes[0].previousElementSibling;
    while(preceding && !['note','backup','forward'].includes(preceding.localName))preceding=preceding.previousElementSibling;
    need(preceding && preceding.localName==='backup' && Number(field(preceding,'duration','0'))>0,'Carrier layout voice has no preceding backup');
    const range=Array.from(measure.children),start=range.indexOf(preceding),end=range.indexOf(notes.at(-1)),noteSet=new Set(notes);
    need(range.slice(start+1,end+1).every(n=>!['backup','forward'].includes(n.localName)&&(n.localName!=='note'||noteSet.has(n))),'Carrier layout voice is interleaved');
    need(!remove.has(preceding),'Carrier backups overlap');remove.add(preceding);backups++;
    for(const n of notes){remove.add(n);rests++;}
   }
  }
  for(const n of remove)n.remove();
  const after=Array.from(candidate.querySelectorAll('note')).filter(n=>children(n,'rest').length===0).map(xml);
  need(JSON.stringify(before)===JSON.stringify(after),'Original layout notes changed');
  return {document:candidate,proof:{removedCarrierRests:rests,removedBackups:backups,originalNotesPreserved:true}};
 }
 globalThis.HairpinExport=Object.freeze({removeCarriers});
})();
