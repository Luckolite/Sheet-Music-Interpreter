// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
(() => {
 const results=[],F=HairpinSource.Fraction,z=F.from(0),style={minimum:0.5,height:1.2,continuationHeight:0.5,lineWidth:0.12};
 const test=(name,fn)=>{try{fn();results.push({name,success:true});}catch(e){results.push({name,success:false,error:String(e)});}};
 const equal=(a,b)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(JSON.stringify({actual:a,expected:b}));};
 const reject=fn=>{let failed=false;try{fn();}catch(_){failed=true;}if(!failed)throw Error('Expected rejection');};
 const xml=n=>new XMLSerializer().serializeToString(n);
 const span=(staff='1',start='1/7',end='4/7',kind=0)=>({staff,startMeasure:0,start,endMeasure:1,end,subtype:kind});
 function fixture(spans,lens={'1':['1/1','1/1']}){
  const d=new DOMParser().parseFromString('<museScore><Score/></museScore>','application/xml'),score=d.querySelector('Score');
  const append=(p,name,text)=>{const n=d.createElement(name);if(text!==undefined)n.textContent=String(text);p.appendChild(n);return n;};
  for(const [sid,lengths] of Object.entries(lens)){
   const staff=append(score,'Staff');staff.setAttribute('id',sid);
   lengths.forEach((length,mi)=>{
    const measure=append(staff,'Measure'),voice=append(measure,'voice'),events=new Map();
    const add=(point,value)=>{const k=F.from(point).toString();if(!events.has(k))events.set(k,[]);events.get(k).push(value);};
    for(const s of spans.filter(s=>s.staff===sid)){
     if(s.startMeasure===mi)add(s.start,{direction:'next',measures:s.endMeasure-mi,fraction:F.from(s.end).subtract(F.from(s.start)),kind:s.subtype});
     if(s.endMeasure===mi)add(s.end,{direction:'prev',measures:s.startMeasure-mi,fraction:F.from(s.start).subtract(F.from(s.end))});
    }
    const points=Array.from(events.keys()).map(F.from);if(!points.some(p=>p.compare(F.from(length))===0))points.push(F.from(length));points.sort((a,b)=>a.compare(b));let cursor=z;
    for(const point of points){
     if(point.compare(cursor)>0){const rest=append(voice,'Rest');append(rest,'visible','0');append(rest,'durationType','quarter');append(rest,'duration',point.subtract(cursor).toString());}cursor=point;
     for(const event of events.get(point.toString())||[]){const spanner=append(voice,'Spanner');spanner.setAttribute('type','HairPin');if(event.kind!==undefined)append(append(spanner,'HairPin'),'subtype',event.kind);const location=append(append(spanner,event.direction),'location');append(location,'measures',event.measures);append(location,'fractions',event.fraction.toString());}
    }
   });
  }
  return {document:d,plan:{spans,lengths:lens}};
 }
 const pair=()=>fixture([span(),span('1','3/7','2/7',1)]);
 const floor=(head,value)=>{const d=head.ownerDocument,segment=d.createElement('Segment'),minimum=d.createElement('minDistance');minimum.textContent=String(value);segment.appendChild(minimum);head.appendChild(segment);};
 test('default overlap lane separated',()=>{const f=pair(),p=HairpinLayout.place(f.document,f.plan,style);equal(p.proof.lanes.map(l=>l.lane),[0,1]);equal(p.proof.constraints.map(c=>c.floor),[3]);});
 test('nonoverlap reuses lane',()=>{const f=fixture([{...span(),endMeasure:0,end:'3/7'},{...span('1','3/7','5/7',1),endMeasure:0}]);equal(HairpinLayout.place(f.document,f.plan,style).proof.constraints,[]);});
 test('three simultaneous intervals',()=>{const f=fixture([span(),span('1','2/7','3/7',1),span('1','3/7','2/7',0)]);equal(HairpinLayout.place(f.document,f.plan,style).proof.constraints.map(c=>c.floor),[3,5.5]);});
 test('staff lanes independent',()=>{const f=fixture([span(),span('2','1/7','4/7',1)],{'1':['1','1'],'2':['1','1']});equal(HairpinLayout.place(f.document,f.plan,style).proof.constraints,[]);});
 test('above and below lanes independent',()=>{const f=pair(),head=f.document.querySelectorAll('HairPin')[1],placement=f.document.createElement('placement');placement.textContent='0';head.appendChild(placement);equal(HairpinLayout.place(f.document,f.plan,style).proof.constraints,[]);});
 test('larger custom wedge gets larger separation',()=>{const f=pair();equal(HairpinLayout.place(f.document,f.plan,{...style,height:4,lineWidth:0.5}).proof.constraints[0].floor,6.5);});
 test('existing custom distance retained',()=>{const f=pair();floor(f.document.querySelector('HairPin'),10);const p=HairpinLayout.place(f.document,f.plan,style);equal(p.proof.constraints[0].floor,12.5);equal(p.document.querySelector('HairPin minDistance').textContent,'10');});
 test('all new continuation segments constrained and idempotent',()=>{const f=pair(),p=HairpinLayout.place(f.document,f.plan,style),head=p.document.querySelectorAll('HairPin')[1];for(const kind of [1,2,3]){const segment=p.document.createElement('Segment'),type=p.document.createElement('subtype');type.textContent=String(kind);segment.appendChild(type);head.appendChild(segment);}const a=HairpinLayout.propagate(p.document,f.plan,p.proof);equal(a.changedFields,3);equal(Array.from(a.document.querySelectorAll('HairPin')[1].querySelectorAll('minDistance')).map(n=>n.textContent),['3','3','3','3']);equal(HairpinLayout.propagate(a.document,f.plan,p.proof).changedFields,0);});
 test('unowned native hairpin untouched',()=>{const f=pair(),other=f.document.querySelector('HairPin').parentNode.cloneNode(true);other.querySelector('fractions').textContent='1/3';floor(other.querySelector('HairPin'),23);f.document.querySelector('voice').appendChild(other);const before=xml(other),p=HairpinLayout.place(f.document,f.plan,style);equal(xml(Array.from(p.document.querySelectorAll('Spanner')).find(n=>n.querySelector('minDistance')?.textContent==='23')),before);});
 test('invalid style rejected without mutation',()=>{const f=pair(),before=xml(f.document);for(const height of [NaN,-1,Infinity])reject(()=>HairpinLayout.place(f.document,f.plan,{...style,height}));equal(xml(f.document),before);});
 test('changed endpoint rejected',()=>{const f=pair();f.document.querySelector('next fractions').textContent='1/3';reject(()=>HairpinLayout.place(f.document,f.plan,style));});
 test('duplicate native ownership rejected',()=>{const f=pair(),head=f.document.querySelector('HairPin').parentNode;head.parentNode.insertBefore(head.cloneNode(true),head);reject(()=>HairpinLayout.place(f.document,f.plan,style));});
 test('score end canonical next measure accepted',()=>{const f=fixture([{...span(),end:'1/1'}]);const loc=f.document.querySelector('next location');loc.querySelector('measures').textContent='2';loc.querySelector('fractions').textContent='-1/7';equal(HairpinLayout.place(f.document,f.plan,style).proof.lanes.length,1);});
 test('changing meter uses exact absolute time',()=>{const f=fixture([span('1','1/7','3/7'),span('1','2/7','1/7',1)],{'1':['3/4','1/2']});equal(HairpinLayout.place(f.document,f.plan,style).proof.lanes.map(l=>l.lane),[0,1]);});
 test('propagation preserves higher user floor',()=>{const f=pair(),p=HairpinLayout.place(f.document,f.plan,style),head=p.document.querySelectorAll('HairPin')[1];floor(head,20);const after=HairpinLayout.propagate(p.document,f.plan,p.proof);equal(Array.from(after.document.querySelectorAll('HairPin')[1].querySelectorAll('minDistance')).map(n=>n.textContent),['3','20']);});
 test('missing managed owner rejected without mutation',()=>{const f=pair(),p=HairpinLayout.place(f.document,f.plan,style),before=xml(p.document);reject(()=>HairpinLayout.propagate(p.document,f.plan,{constraints:[{key:'missing',floor:3}]}));equal(xml(p.document),before);});
 test('exported style values used exactly',()=>{const d=new DOMParser().parseFromString('<museScore><Style><hairpinMinDistance>0.7</hairpinMinDistance><hairpinHeight>1.15</hairpinHeight><hairpinContHeight>0.5</hairpinContHeight><hairpinWidth>0.12</hairpinWidth></Style></museScore>','application/xml');equal(HairpinLayout.styleFromDocument(d),{minimum:0.7,height:1.15,continuationHeight:0.5,lineWidth:0.12,separation:2.5});});
 test('missing style fields never guessed',()=>{const d=new DOMParser().parseFromString('<museScore><Style><hairpinHeight>1.15</hairpinHeight></Style></museScore>','application/xml');reject(()=>HairpinLayout.styleFromDocument(d));});
 test('duplicate style value rejected',()=>{const d=new DOMParser().parseFromString('<museScore><Style><hairpinMinDistance>0.7</hairpinMinDistance><hairpinMinDistance>1</hairpinMinDistance><hairpinHeight>1.15</hairpinHeight><hairpinContHeight>0.5</hairpinContHeight><hairpinWidth>0.12</hairpinWidth></Style></museScore>','application/xml');reject(()=>HairpinLayout.styleFromDocument(d));});
 test('no-op propagation preserves XML declaration',()=>{const f=pair(),p=HairpinLayout.place(f.document,f.plan,style),d=new DOMParser().parseFromString('<?xml version="1.0" encoding="UTF-8"?>'+xml(p.document),'application/xml'),before=xml(d),after=HairpinLayout.propagate(d,f.plan,p.proof);equal(after.changedFields,0);equal(xml(after.document),before);});
 globalThis.hairpinLayoutControls=results;
})();
