// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
(() => {
 const results=[];
 const test=(name,fn)=>{try{fn();results.push({name,success:true});}catch(e){results.push({name,success:false,error:String(e)});}};
 const equal=(a,b)=>{if(JSON.stringify(a)!==JSON.stringify(b))throw Error(JSON.stringify({actual:a,expected:b}));};
 const reject=fn=>{let rejected=false;try{fn();}catch(_){rejected=true;}if(!rejected)throw Error('Expected rejection');};
 const parse=xml=>new DOMParser().parseFromString(xml,'application/xml');
 const attr=(div=1,staves=1)=>`<attributes><divisions>${div}</divisions><time><beats>4</beats><beat-type>4</beat-type></time><staves>${staves}</staves></attributes>`;
 const note=(step='C',duration=4,extra='',octave=4,staff=1,voice=1)=>`<note>${extra}<pitch><step>${step}</step><octave>${octave}</octave></pitch><duration>${duration}</duration><voice>${voice}</voice><staff>${staff}</staff></note>`;
 const rest=d=>`<note><rest/><duration>${d}</duration></note>`;
 const chord=(pitch=60,type='whole',extra='')=>`<Chord>${extra}<durationType>${type}</durationType><Note><pitch>${pitch}</pitch></Note></Chord>`;
 const nativeRest=(type='half',extra='')=>`<Rest><durationType>${type}</durationType>${extra}</Rest>`;
 const backup=d=>`<backup><duration>${d}</duration></backup>`;
 const source=(body,attributes=attr())=>parse(`<score-partwise><part-list><score-part id="A"/></part-list><part id="A"><measure>${attributes}${body}</measure></part></score-partwise>`);
 const native=(body)=>parse(`<museScore><Score><Part><Staff id="7"/></Part><Staff id="7"><Measure><voice>${body}</voice></Measure></Staff></Score></museScore>`);
 const identify=(s,n)=>HairpinIdentity.identify(s,n);
 test('single part and renamed native staff',()=>equal(identify(source(note()),native(chord())).mapping,[{partId:'A',sourceStaff:'1',nativeStaff:'7'}]));
 test('exact seventh clocks',()=>equal(identify(source(note('C',4)+rest(24),attr(7)),native(chord(60,'quarter','<duration>1/7</duration>')+nativeRest('quarter','<duration>6/7</duration>'))).noteCount,1));
 test('triplet clocks',()=>equal(identify(source(note('C',2)+note('D',2)+note('E',2)+rest(6),attr(3)),native('<Tuplet><normalNotes>2</normalNotes><actualNotes>3</actualNotes></Tuplet>'+chord(60,'quarter')+chord(62,'quarter')+chord(64,'quarter')+'<endTuplet/>'+nativeRest())).noteCount,3));
 test('nested tuplets',()=>equal(identify(source(note('C',4)+rest(56),attr(15)),native('<Tuplet><normalNotes>2</normalNotes><actualNotes>3</actualNotes></Tuplet><Tuplet><normalNotes>4</normalNotes><actualNotes>5</actualNotes></Tuplet>'+chord(60,'eighth')+'<endTuplet/><endTuplet/>'+nativeRest('quarter','<duration>14/15</duration>'))).noteCount,1));
 test('grace note has zero clock',()=>equal(identify(source(note('C',1,'<grace/>')+note('D')),native(chord(60,'quarter','<appoggiatura/>')+chord(62))).noteCount,2));
 test('chord shares onset',()=>equal(identify(source(note()+note('E',4,'<chord/>')),native('<Chord><durationType>whole</durationType><Note><pitch>60</pitch></Note><Note><pitch>64</pitch></Note></Chord>')).noteCount,2));
 test('polyphonic backup',()=>equal(identify(source(note()+backup(4)+note('D',4,'',4,1,2)),parse('<museScore><Score><Part><Staff id="7"/></Part><Staff id="7"><Measure><voice>'+chord()+'</voice><voice>'+chord(62)+'</voice></Measure></Staff></Score></museScore>')).noteCount,2));
 test('rest advances clock',()=>equal(identify(source(rest(2)+note('C',2)),native(nativeRest()+chord(60,'half'))).noteCount,1));
 test('empty voices may compress',()=>equal(identify(source(note('C',4,'',4,1,2)),native(chord())).noteCount,1));
 test('dotted clock',()=>equal(identify(source(note('C',3)+rest(1)),native(chord(60,'half','<dots>1</dots>')+nativeRest('quarter'))).noteCount,1));
 test('cursor location advances clock',()=>equal(identify(source('<forward><duration>2</duration></forward>'+note('C',2)),native('<location><fractions>1/2</fractions></location>'+chord(60,'half'))).noteCount,1));
 test('pitch mismatch rejected',()=>reject(()=>identify(source(note()),native(chord(61)))));
 test('clock mismatch rejected',()=>reject(()=>identify(source(note()),native(chord(60,'half')+nativeRest()))));
 test('extra native note rejected',()=>reject(()=>identify(source(note()),native('<Chord><durationType>whole</durationType><Note><pitch>60</pitch></Note><Note><pitch>60</pitch></Note></Chord>'))));
 test('orphan chord rejected',()=>reject(()=>identify(source(note('C',4,'<chord/>')),native(chord()))));
 test('unmatched native tuplet rejected',()=>reject(()=>identify(source(note()),native('<endTuplet/>'+chord()))));
 test('unclosed native tuplet rejected',()=>reject(()=>identify(source(note()),native('<Tuplet><normalNotes>1</normalNotes><actualNotes>1</actualNotes></Tuplet>'+chord()))));
 test('noninteger pitch rejected',()=>reject(()=>identify(source(note()),native(chord('60.5')))));
 test('no note evidence rejected',()=>reject(()=>identify(source(rest(4)),native(nativeRest('whole')))));
 test('transposition not silently ignored',()=>reject(()=>identify(source(note(),attr()+'<attributes><transpose><chromatic>-2</chromatic></transpose></attributes>'),native(chord()))));
 test('native measure count change rejected',()=>{const n=native(chord());n.getElementsByTagName('Staff')[1].appendChild(n.createElement('Measure'));reject(()=>identify(source(note()),n));});
 test('unowned native staff rejected',()=>{const n=native(chord());const staff=n.createElement('Staff');staff.setAttribute('id','9');n.getElementsByTagName('Score')[0].appendChild(staff);reject(()=>identify(source(note()),n));});
 test('two staff part ownership',()=>{
  const s=source(note()+backup(4)+note('C',4,'',3,2),attr(1,2));
  const n=parse(`<museScore><Score><Part><Staff id="7"/><Staff id="9"/></Part><Staff id="7"><Measure><voice>${chord()}</voice></Measure></Staff><Staff id="9"><Measure><voice>${chord(48)}</voice></Measure></Staff></Score></museScore>`);
  equal(identify(s,n).mapping.map(m=>m.nativeStaff),['7','9']);
  n.getElementsByTagName('pitch')[0].textContent='48';n.getElementsByTagName('pitch')[1].textContent='60';reject(()=>identify(s,n));
 });
 test('cross staff display stays in same part',()=>{
  const s=source(note('C',4,'',4,2),attr(1,2));
  const n=parse(`<museScore><Score><Part><Staff id="7"/><Staff id="9"/></Part><Staff id="7"><Measure><voice>${chord(60,'whole','<staffMove>1</staffMove>')}</voice></Measure></Staff><Staff id="9"><Measure><voice/></Measure></Staff></Score></museScore>`);
  equal(identify(s,n).noteCount,1);
 });
 test('cross part staff move rejected',()=>reject(()=>identify(source(note()),native(chord(60,'whole','<staffMove>1</staffMove>')))));
 test('part identities and order checked',()=>{
  const s=parse(`<score-partwise><part-list><score-part id="A"/><score-part id="B"/></part-list><part id="A"><measure>${attr()}${note()}</measure></part><part id="B"><measure>${attr()}${note('D')}</measure></part></score-partwise>`);
  const n=parse(`<museScore><Score><Part><Staff id="7"/></Part><Part><Staff id="9"/></Part><Staff id="7"><Measure><voice>${chord()}</voice></Measure></Staff><Staff id="9"><Measure><voice>${chord(62)}</voice></Measure></Staff></Score></museScore>`);
  equal(identify(s,n).parts,2);s.getElementsByTagName('score-part')[0].setAttribute('id','B');reject(()=>identify(s,n));
 });
 test('identity check leaves both DOMs unchanged',()=>{const s=source(note()),n=native(chord()),serialize=d=>new XMLSerializer().serializeToString(d);const before=[serialize(s),serialize(n)];identify(s,n);equal([serialize(s),serialize(n)],before);});
 const transpose=(chromatic,diatonic='',octave='',extra='')=>`<attributes><transpose>${diatonic===''?'':`<diatonic>${diatonic}</diatonic>`}<chromatic>${chromatic}</chromatic>${octave===''?'':`<octave-change>${octave}</octave-change>`}${extra}</transpose></attributes>`;
 const instrument=(n,chromatic,diatonic)=>{
  const i=n.createElement('Instrument');i.innerHTML=`<transposeChromatic>${chromatic}</transposeChromatic><transposeDiatonic>${diatonic}</transposeDiatonic>`;
  n.querySelector('Part').appendChild(i);return n;
 };
 test('B-flat written C4 matches sounding B-flat3 and instrument interval',()=>equal(identify(source(note(),attr()+transpose(-2,-1)),instrument(native(chord(58)),-2,-1)).transposingNotes,1));
 test('octave transposition adds twelve semitones and seven diatonic steps',()=>equal(identify(source(note(),attr()+transpose(0,0,1)),instrument(native(chord(72)),12,7)).transposingNotes,1));
 test('compound octave and chromatic transposition combine exactly',()=>equal(identify(source(note(),attr()+transpose(-2,-1,1)),instrument(native(chord(70)),10,6)).noteCount,1));
 test('optional diatonic transposition is not guessed',()=>equal(identify(source(note(),attr()+transpose(-2)),instrument(native(chord(58)),-2,-1)).noteCount,1));
 test('correct sounding pitch cannot hide wrong native instrument interval',()=>reject(()=>identify(source(note(),attr()+transpose(-2,-1)),instrument(native(chord(58)),-3,-1))));
 test('wrong native diatonic interval cannot change written spelling silently',()=>reject(()=>identify(source(note(),attr()+transpose(-2,-1)),instrument(native(chord(58)),-2,-2))));
 test('written pitch cannot masquerade as sounding pitch',()=>reject(()=>identify(source(note(),attr()+transpose(-2,-1)),instrument(native(chord(60)),-2,-1))));
 test('later ignored transposition change is rejected',()=>{
  const s=source(note(),attr()+transpose(-2,-1)),m=s.createElement('measure');m.innerHTML=transpose(2,1)+note();s.querySelector('part').appendChild(m);
  const n=instrument(native(chord(58)),-2,-1),nm=n.createElement('Measure');nm.innerHTML='<voice>'+chord(58)+'</voice>';n.querySelector('Score > Staff').appendChild(nm);
  reject(()=>identify(s,n));
 });
 test('fractional chromatic interval is not rounded',()=>reject(()=>identify(source(note(),attr()+transpose('-1.5')),instrument(native(chord(58)),-2,-1))));
 test('doubling cannot silently lose a second sounding note',()=>reject(()=>identify(source(note(),attr()+transpose(0,0,'','<double/>')),native(chord()))));
 test('transpose staff must exist',()=>reject(()=>identify(source(note(),attr()+transpose(-2,-1).replace('<transpose>','<transpose number="2">')),instrument(native(chord(58)),-2,-1))));
 test('native instrument change cannot use initial interval for entire score',()=>{
  const n=native(chord());n.querySelector('voice').insertAdjacentHTML('afterbegin','<InstrumentChange><Instrument><transposeChromatic>-2</transposeChromatic></Instrument></InstrumentChange>');reject(()=>identify(source(note()),n));
 });
 test('per-staff explicit same interval agrees with a shared native instrument',()=>{
  const a=attr(1,2)+transpose(-2,-1).replace('<transpose>','<transpose number="1">')+transpose(-2,-1).replace('<transpose>','<transpose number="2">');
  const s=source(note()+backup(4)+note('C',4,'',3,2),a);
  const n=parse(`<museScore><Score><Part><Staff id="7"/><Staff id="9"/></Part><Staff id="7"><Measure><voice>${chord(58)}</voice></Measure></Staff><Staff id="9"><Measure><voice>${chord(46)}</voice></Measure></Staff></Score></museScore>`);
  instrument(n,-2,-1);equal(identify(s,n).transposingNotes,2);
 });
 test('absent source transpose cannot hide a native enharmonic instrument shift',()=>reject(()=>identify(source(note()),instrument(native(chord()),0,1))));
 const crossFixture=()=>{
  const s=source(note()),sn=s.querySelector('note');sn.querySelector('pitch').outerHTML='<unpitched><display-step>C</display-step><display-octave>4</display-octave></unpitched>';
  sn.insertAdjacentHTML('beforeend','<type>whole</type><notehead>x</notehead>');
  s.documentElement.insertAdjacentHTML('afterbegin','<identification><encoding><software>Music Sheets</software></encoding></identification>');
  const n=native(chord());n.querySelector('Note').insertAdjacentHTML('beforeend','<tpc>14</tpc><head>cross</head>');
  const proof={count:1,owners:[{index:0,marker:{step:'C',octave:'4',position:60,tpc:14,voice:'1',staff:'1',type:'whole',chord:false},accidentalCount:0}]};
  return {s,n,proof};
 };
 const crossIdentify=({s,n,proof})=>HairpinIdentity.identify(s,n,{carriers:proof});
 test('verified own display carrier retains typed unpitched identity',()=>{
  const f=crossFixture(),serialize=d=>new XMLSerializer().serializeToString(d),before=[serialize(f.s),serialize(f.n)];
  equal(crossIdentify(f).unpitchedNotes,1);equal([serialize(f.s),serialize(f.n)],before);
 });
 test('unpitched display is not inferred from a tonal pitch without proof',()=>{const f=crossFixture();reject(()=>identify(f.s,f.n));});
 test('empty owners cannot claim an unpitched source',()=>{const f=crossFixture();f.proof.owners=[];reject(()=>crossIdentify(f));});
 test('carrier native pitch must equal verified display position',()=>{const f=crossFixture();f.n.querySelector('pitch').textContent='61';reject(()=>crossIdentify(f));});
 test('source display position must equal its native carrier',()=>{const f=crossFixture();f.s.querySelector('display-step').textContent='D';reject(()=>crossIdentify(f));});
 test('visible carrier accidental rejected',()=>{const f=crossFixture();f.n.querySelector('Note').insertAdjacentHTML('beforeend','<Accidental><subtype>accidentalNatural</subtype><visible>1</visible></Accidental>');f.proof.owners[0].accidentalCount=1;reject(()=>crossIdentify(f));});
 test('verified hidden carrier natural is preserved',()=>{const f=crossFixture();f.n.querySelector('Note').insertAdjacentHTML('beforeend','<Accidental><subtype>accidentalNatural</subtype><visible>0</visible></Accidental>');f.proof.owners[0].accidentalCount=1;equal(crossIdentify(f).unpitchedNotes,1);});
 test('percussion MIDI instrument cannot be guessed from display',()=>{const f=crossFixture();f.s.querySelector('score-part').insertAdjacentHTML('beforeend','<midi-instrument id="drum"><midi-unpitched>39</midi-unpitched></midi-instrument>');reject(()=>crossIdentify(f));});
 test('explicit unpitched instrument requires separate instrument identity',()=>{const f=crossFixture();f.s.querySelector('note').insertAdjacentHTML('beforeend','<instrument id="drum"/>');reject(()=>crossIdentify(f));});
 test('pitched and unpitched unison chord stay distinct',()=>{
  const f=crossFixture(),measure=f.s.querySelector('measure'),u=f.s.querySelector('note');
  const pitched=parse('<root>'+note()+'</root>').documentElement.firstElementChild;measure.insertBefore(f.s.importNode(pitched,true),u);u.insertAdjacentHTML('afterbegin','<chord/>');
  f.n.querySelector('Chord').insertAdjacentHTML('afterbegin','<Note><pitch>60</pitch><tpc>14</tpc></Note>');
  f.proof.count=2;f.proof.owners[0].index=1;f.proof.owners[0].marker.chord=true;
  equal([crossIdentify(f).noteCount,crossIdentify(f).unpitchedNotes],[2,1]);
 });
 test('duplicate carrier owners rejected',()=>{const f=crossFixture();f.proof.owners.push(f.proof.owners[0]);reject(()=>crossIdentify(f));});
 test('carrier source written type must match',()=>{const f=crossFixture();f.proof.owners[0].marker.type='quarter';reject(()=>crossIdentify(f));});
 test('carrier source voice label must match',()=>{const f=crossFixture();f.proof.owners[0].marker.voice='2';reject(()=>crossIdentify(f));});
 test('carrier source staff label must match',()=>{const f=crossFixture();f.proof.owners[0].marker.staff='2';reject(()=>crossIdentify(f));});
 test('foreign source cannot acquire app display-carrier semantics',()=>{const f=crossFixture();f.s.querySelector('identification').remove();reject(()=>crossIdentify(f));});
 test('pitched source cannot be silently relabeled as display carrier',()=>{const f=crossFixture();f.s.querySelector('unpitched').outerHTML='<pitch><step>C</step><octave>4</octave></pitch>';reject(()=>crossIdentify(f));});
 globalThis.hairpinIdentityControls=results;
})();
