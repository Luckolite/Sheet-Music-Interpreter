// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Original synthetic Ottava identity controls; no private score/native data.
(() => {
 const parse=x=>new DOMParser().parseFromString(x,"application/xml");
 const serialize=x=>new XMLSerializer().serializeToString(x);
const attr=(staves=1)=>`<attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time><staves>${staves}</staves></attributes>`;
const direction=(type,size=8,extra='',number=1,staff=1)=>`<direction><direction-type><octave-shift type="${type}"${size===null?'':` size="${size}"`} number="${number}"/></direction-type><voice>1</voice>${staff===null?'':`<staff>${staff}</staff>`}${extra}</direction>`;
const sn=(step='C',dur=1,extra='',staff=1)=>`<note>${extra}<pitch><step>${step}</step><octave>4</octave></pitch><duration>${dur}</duration><voice>1</voice><staff>${staff}</staff></note>`;
const sr=d=>`<note><rest/><duration>${d}</duration></note>`;
const nc=(pitch,type='quarter',extra='',notes='')=>`<Chord>${extra}<durationType>${type}</durationType><Note><pitch>${pitch}</pitch></Note>${notes}</Chord>`;
const nr=(type='half',dots=1)=>`<Rest><durationType>${type}</durationType>${dots?`<dots>${dots}</dots>`:''}</Rest>`;
const source=(body,a=attr())=>`<score-partwise><part-list><score-part id="A"/></part-list><part id="A"><measure>${a}${body}</measure></part></score-partwise>`;
const score=(voices,version='4.00',extra='')=>`<museScore version="${version}"><Score><Part><Staff id="7"/></Part>${extra}<Staff id="7"><Measure>${voices}</Measure></Staff></Score></museScore>`;
const native=(body,v='4.00',extra='')=>score(`<voice>${body}</voice>`,v,extra);
const head=(sub='8va',fractions='1/4',extra='',locExtra='')=>`<Spanner type="Ottava"><Ottava><subtype>${sub}</subtype>${extra}</Ottava><next><location><fractions>${fractions}</fractions>${locExtra}</location></next></Spanner>`;
const tail=(fractions='-1/4',locExtra='')=>`<Spanner type="Ottava"><prev><location><fractions>${fractions}</fractions>${locExtra}</location></prev></Spanner>`;
const names=['8va','8vb','15ma','15mb','22ma','22mb'],shifts=[12,-12,24,-24,36,-36];
function fixture(i=0,sub=names[i],style=''){const size=Math.abs(shifts[i])/12*7+1;return{source:source(direction(shifts[i]>0?'down':'up',size)+sn()+direction('stop',null)+sr(3)),native:native(head(sub,'1/4',style)+nc(60-shifts[i])+tail()+nr())};}
const rows=[];let positives=0,negatives=0;
function test(name,f,accepted=true){const s=parse(f.source),n=parse(f.native),before=[serialize(s),serialize(n)];let result,error='';try{result=HairpinIdentity.identify(s,n,f.options||{});}catch(e){error=String(e.message||e);}if(accepted?!!error:!error)throw Error(name+': '+error);if(before[0]!==serialize(s)||before[1]!==serialize(n))throw Error('DOM mutated '+name);rows.push({name,accepted,error,noteCount:result?.noteCount??null});if(accepted)positives++;else negatives++;return result;}
test('plain old native version remains accepted',{source:source(sn()+sr(3)),native:native(nc(60)+nr(),'3.02')});
test('plain capo retains previous comparison',{source:source(sn()+sr(3)),native:native(nc(60)+nr(),'4.00','<Capo/>')});
test('plain out of range retains original error priority',{source:source(sn()+sr(3)),native:native(nc(200)+nr(),'3.02')},false);
test('plain grace retains previous loose role behavior',{source:source(sn('D',1,'<grace/>')+sn()+sr(3)),native:native(nc(62,'eighth','<appoggiatura/><grace16/>')+nc(60)+nr(),'3.02')});
for(let i=0;i<6;i++){test('named subtype '+names[i],fixture(i));test('numeric subtype '+i,fixture(i,String(i)));test('styled subtype '+names[i],fixture(i,names[i],'<placement>above</placement><lineWidth>0.1</lineWidth><beginText>8va</beginText>'));}
let f=fixture();test('renamed source part stays explicitly mapped',{source:f.source.replaceAll('id="A"','id="Z"'),native:f.native});
// Exact nonzero source direction offset; cursor itself is not advanced by direction.
test('source positive offset exact native start',{source:source(direction('down',8,'<offset>1</offset>')+sn('C')+sn('D')+direction('stop',8)+sr(2)),native:native(nc(60)+head()+nc(50)+tail()+nr('half',0))});
test('source continuation within same span',{source:source(direction('down',8)+sn('C')+direction('continue',null)+sn('D')+direction('stop',null)+sr(2)),native:native(head('8va','1/2')+nc(48)+nc(50)+tail('-1/2')+nr('half',0))});
test('chord heads share performed offset',{source:source(direction('down')+sn('C')+sn('E',1,'<chord/>')+direction('stop',null)+sr(3)),native:native(head()+nc(48,'quarter','','<Note><pitch>52</pitch></Note>')+tail()+nr())});
test('ties do not change offset domain',{source:source(direction('down')+sn('C',1,'<tie type="start"/>')+direction('stop',null)+sn('C',1,'<tie type="stop"/>')+sr(2)),native:native(head()+nc(48,'quarter','<Spanner type="Tie"/>')+tail()+nc(60,'quarter','<Spanner type="Tie"/>')+nr('half',0))});
test('adjacent opposite spans use half-open boundary',{source:source(direction('down')+sn('C')+direction('stop',null)+direction('up')+sn('D')+direction('stop',null)+sr(2)),native:native(head('8va')+nc(48)+tail()+head('8vb')+nc(74)+tail()+nr('half',0))});
test('reciprocal endpoint in different voice same staff',{source:f.source,native:score('<voice>'+head('8va','1/4','','<voices>1</voices>')+nc(48)+nr()+'</voice><voice>'+nr('quarter',0)+tail('-1/4','<voices>-1</voices>')+nr()+'</voice>')});
test('origin track offset precedes display staff move',{source:source(direction('down',8,'',1,1)+sn('C',1,'',2)+direction('stop',null,'',1,1)+sr(3),attr(2)),native:'<museScore version="4.00"><Score><Part><Staff id="7"/><Staff id="9"/></Part><Staff id="7"><Measure><voice>'+head()+nc(48,'quarter','<staffMove>1</staffMove>')+tail()+nr()+'</voice></Measure></Staff><Staff id="9"><Measure><voice/></Measure></Staff></Score></museScore>'});
const multiSource='<score-partwise><part-list><score-part id="A"/></part-list><part id="A"><measure>'+attr()+sr(3)+direction('down')+sn('C')+'</measure><measure>'+sn('D')+direction('stop',null)+sr(3)+'</measure></part></score-partwise>';
const multiNative='<museScore version="4.00"><Score><Part><Staff id="7"/></Part><Staff id="7"><Measure><voice>'+nr()+head('8va','-1/2','','<measures>1</measures>')+nc(48)+'</voice></Measure><Measure><voice>'+nc(50)+tail('1/2','<measures>-1</measures>')+nr()+'</voice></Measure></Staff></Score></museScore>';
test('cross-measure exact rational locations',{source:multiSource,native:multiNative});
for(const tag of ['appoggiatura','acciaccatura','grace4','grace16','grace32','grace8after','grace16after','grace32after'])test('stable grace offset '+tag,{source:source(direction('down')+sn('C')+sn('D',1,'<grace/>')+sn('E')+sr(2)+direction('stop',null)),native:native(head('8va','1/1')+nc(48)+nc(50,'eighth',`<${tag}/>`)+nc(52)+nr('half',0)+tail('-1/1'))});
test('grace before first principal inside opening span',{source:source(direction('down')+sn('D',1,'<grace/>')+sn('C')+direction('stop',null)+sr(3)),native:native(head()+nc(50,'eighth','<appoggiatura/>')+nc(48)+tail()+nr())});
// Conservative refusal controls: no guessed span, voice/owner alias or future fallback.
const reject=(name,sourceChange=x=>x,nativeChange=x=>x,base=fixture())=>test(name,{source:sourceChange(base.source),native:nativeChange(base.native)},false);
reject('native offset cannot substitute for missing source span',s=>s.replace(/<direction>[\s\S]*?<\/direction>/g,''));
reject('missing native timeline',x=>x,n=>n.replace(/<Spanner type="Ottava">[\s\S]*?<\/Spanner>/g,''));
reject('source contradictory direction',s=>s.replace('type="down"','type="up"'));
reject('source contradictory size',s=>s.replace('size="8"','size="15"'));
reject('source unknown size',s=>s.replace('size="8"','size="9"'));
reject('source unknown type',s=>s.replace('type="down"','type="future"'));
reject('source unknown mark attribute',s=>s.replace('type="down"','type="down" future="1"'));
reject('source unknown mark child',s=>s.replace('size="8" number="1"/>','size="8" number="1"><future/></octave-shift>'));
reject('source unmapped staff',s=>s.replace('<staff>1</staff>','<staff>9</staff>'));
reject('source unclosed span',s=>s.replace(/<direction><direction-type><octave-shift type="stop"[\s\S]*?<\/direction>/,''));
reject('source unmatched stop',s=>s.replace(/<direction><direction-type><octave-shift type="down"[\s\S]*?<\/direction>/,''));
reject('source continuation without active owner',s=>s.replace('type="down"','type="continue"'));
reject('source contradictory continuation size',s=>s.replace(sn(),direction('continue',15)+sn()));
reject('source zero span',s=>s.replace(sn(),''));
reject('source number outside domain',s=>s.replaceAll('number="1"','number="17"'));
reject('source number duplicate',s=>s.replace(sn(),direction('down')+sn()));
reject('source direction offset unmatched',s=>s.replace('<voice>1</voice>','<voice>1</voice><offset>1</offset>'));
reject('native named subtype unknown',x=>x,n=>n.replace('<subtype>8va</subtype>','<subtype>future</subtype>'));
reject('native numeric subtype unknown',x=>x,n=>n.replace('<subtype>8va</subtype>','<subtype>6</subtype>'));
reject('native duplicate subtype',x=>x,n=>n.replace('</subtype>','</subtype><subtype>8va</subtype>'));
reject('native property outside known format',x=>x,n=>n.replace('</Ottava>','<pitchShift>0</pitchShift></Ottava>'));
reject('native future format',x=>x,n=>n.replace('version="4.00"','version="4.01"'));
reject('native legacy numeric ambiguity',x=>x,n=>n.replace('version="4.00"','version="1.14"').replace('>8va<','>1<'));
reject('native unknown connector attribute',x=>x,n=>n.replace('type="Ottava"','type="Ottava" future="1"'));
reject('native unknown location field',x=>x,n=>n.replace('<fractions>1/4</fractions>','<fractions>1/4</fractions><future>0</future>'));
reject('native cross-staff endpoint',x=>x,n=>n.replace('<fractions>1/4</fractions>','<fractions>1/4</fractions><staves>1</staves>'));
reject('native out-of-range voice',x=>x,n=>n.replace('<fractions>1/4</fractions>','<fractions>1/4</fractions><voices>4</voices>'));
reject('native note-index endpoint unsupported',x=>x,n=>n.replace('<fractions>1/4</fractions>','<fractions>1/4</fractions><notes>1</notes>'));
reject('native endpoint outside score',x=>x,n=>n.replace('<fractions>1/4</fractions>','<fractions>2/1</fractions>'));
reject('native reverse span',x=>x,n=>n.replace('<fractions>1/4</fractions>','<fractions>-1/4</fractions>'));
reject('native zero span',x=>x,n=>n.replace('<fractions>1/4</fractions>','<fractions>0</fractions>'));
reject('native reciprocal endpoint mismatch',x=>x,n=>n.replace('<fractions>-1/4</fractions>','<fractions>-1/8</fractions>'));
reject('native missing reciprocal tail',x=>x,n=>n.replace(tail(),''));
reject('native duplicate reciprocal tail',x=>x,n=>n.replace(tail(),tail()+tail()));
reject('native duplicate head',x=>x,n=>n.replace(head(),head()+head()));
reject('native both next and prev at head',x=>x,n=>n.replace('</next>','</next><prev><location><fractions>0</fractions></location></prev>'));
reject('native unvisited head representation',x=>x,n=>n.replace('<Staff id="7"><Measure>','<Ottava><subtype>8va</subtype></Ottava><Staff id="7"><Measure>'));
test('raw128 cannot normalize to valid116',{source:fixture(1).source.replace('<step>C</step><octave>4</octave>','<step>G</step><alter>1</alter><octave>8</octave>'),native:fixture(1).native.replace('<pitch>72</pitch>','<pitch>128</pitch>')},false);
if(!rows.at(-1).error.includes('Native stored pitch outside MIDI range'))throw Error('Raw128 guard not reached');
test('rawMinus1 cannot normalize to valid11',{source:fixture(0).source.replace('<step>C</step><octave>4</octave>','<step>B</step><octave>-1</octave>'),native:fixture(0).native.replace('<pitch>48</pitch>','<pitch>-1</pitch>')},false);
if(!rows.at(-1).error.includes('Native stored pitch outside MIDI range'))throw Error('RawMinus1 guard not reached');
reject('native contradictory raw pitch remains rejected',x=>x,n=>n.replace('<pitch>48</pitch>','<pitch>49</pitch>'));
reject('native capo interaction not inferred',x=>x,n=>n.replace('<Score>','<Score><Capo>1</Capo>'));
reject('native drum interaction not inferred',x=>x,n=>n.replace('<Score>','<Score><Drumset/>'));
reject('native useDrumset interaction not inferred',x=>x,n=>n.replace('<Score>','<Score><useDrumset>1</useDrumset>'));
// Source and native both contain two coherent but overlapping intervals: still refuse.
test('overlap is not a union or alias',{source:source(direction('down',8,'',1)+direction('down',8,'',2)+sn()+direction('stop',null,'',1)+direction('stop',null,'',2)+sr(3)),native:native(head()+head('8va','1/4','<color r="1"/>')+nc(48)+tail()+tail()+nr())},false);
test('grace ambiguous at span end',{source:source(direction('down')+sn('C')+direction('stop',null)+sn('D',1,'<grace/>')+sn('E')+sr(2)),native:native(head()+nc(48)+tail()+nc(50,'eighth','<grace8after/>')+nc(64)+nr('half',0))},false);
test('grace role unsupported future',{source:source(direction('down')+sn('D',1,'<grace/>')+sn('C')+direction('stop',null)+sr(3)),native:native(head()+nc(50,'eighth','<grace64/>')+nc(48)+tail()+nr())},false);
test('grace duplicated contradictory tags',{source:source(direction('down')+sn('D',1,'<grace/>')+sn('C')+direction('stop',null)+sr(3)),native:native(head()+nc(50,'eighth','<appoggiatura/><grace8after/>')+nc(48)+tail()+nr())},false);
test('after grace principal unavailable',{source:source(direction('down')+sn('D',1,'<grace/>')+sn('C')+direction('stop',null)+sr(3)),native:native(head()+nc(50,'eighth','<grace8after/>')+nc(48)+tail()+nr())},false);
test('no-Ottava behavior retains absent/future version',{source:source(sn()+sr(3)),native:native(nc(60)+nr(),'9.99')});
test('source voice compression does not fabricate native voice identity',{source:fixture().source.replaceAll('<voice>1</voice>','<voice>2</voice>'),native:fixture().native});
test('part-local same number independent spans',{source:'<score-partwise><part-list><score-part id="A"/><score-part id="B"/></part-list><part id="A"><measure>'+attr()+direction('down')+sn()+direction('stop',null)+sr(3)+'</measure></part><part id="B"><measure>'+attr()+direction('up')+sn('D')+direction('stop',null)+sr(3)+'</measure></part></score-partwise>',native:'<museScore version="4.00"><Score><Part><Staff id="7"/></Part><Part><Staff id="9"/></Part><Staff id="7"><Measure><voice>'+head('8va')+nc(48)+tail()+nr()+'</voice></Measure></Staff><Staff id="9"><Measure><voice>'+head('8vb')+nc(74)+tail()+nr()+'</voice></Measure></Staff></Score></museScore>'});
test('instrument interval and Ottava are separate projections',{source:fixture().source.replace('</attributes>','</attributes><attributes><transpose><diatonic>-1</diatonic><chromatic>-2</chromatic></transpose></attributes>'),native:fixture().native.replace('</Part>','<Instrument><transposeChromatic>-2</transposeChromatic><transposeDiatonic>-1</transposeDiatonic></Instrument></Part>').replace('<pitch>48</pitch>','<pitch>46</pitch>')});
test('distinct reciprocal overlapping spans are refused',{source:source(direction('down',8,'',1)+sn('C')+direction('down',8,'',2)+sn('D')+direction('stop',null,'',1)+sn('E')+direction('stop',null,'',2)+sr(1)),native:native(head('8va','1/2')+nc(48)+head('8va','1/2')+nc(50)+tail('-1/2')+nc(52)+tail('-1/2')+nr('quarter',0))},false);
if(!rows.at(-1).error.includes('Overlapping'))throw Error('True overlap branch not reached');
test('source continuation cannot lie after its eventual stop',{source:source(direction('down')+sn('C',2)+direction('continue',null)+'<backup><duration>1</duration></backup>'+direction('stop',null)+sr(3)),native:native(head()+nc(48,'half')+'<location><fractions>-1/4</fractions></location>'+tail()+'<location><fractions>1/4</fractions></location>'+nr('half',0))},false);
const own='<identification><encoding><software>Music Sheets</software></encoding></identification>';
const unpitched='<note><unpitched><display-step>C</display-step><display-octave>4</display-octave></unpitched><duration>1</duration><voice>1</voice><type>quarter</type><staff>1</staff><notehead>x</notehead></note>';
const carrier=(index,count)=>({carriers:{count,owners:[{index,marker:{step:'C',octave:'4',position:60,tpc:14,voice:'1',staff:'1',type:'quarter',chord:false},accidentalCount:0}]}});
const nativeCarrier=nc(60).replace('</Note>','<tpc>14</tpc><head>cross</head></Note>');
test('display carrier with active Ottava is not silently sounded',{source:source(direction('down')+unpitched+direction('stop',null)+sr(3)).replace('<part-list>',own+'<part-list>'),native:native(head()+nativeCarrier+tail()+nr()),options:carrier(0,1)},false);
if(!rows.at(-1).error.includes('Unpitched display carrier needs'))throw Error('Carrier/Ottava refusal branch not reached');
test('display carrier outside span retains exact display proof',{source:source(direction('down')+sn()+direction('stop',null)+unpitched+sr(2)).replace('<part-list>',own+'<part-list>'),native:native(head()+nc(48)+tail()+nativeCarrier+nr('half',0)),options:carrier(1,2)});
test('nested tuplet exact Ottava boundary',{source:source(direction('down')+sn('C',4)+direction('stop',null)+sr(56),attr().replace('<divisions>1</divisions>','<divisions>15</divisions>')),native:native(head('8va','1/15')+'<Tuplet><normalNotes>2</normalNotes><actualNotes>3</actualNotes></Tuplet><Tuplet><normalNotes>4</normalNotes><actualNotes>5</actualNotes></Tuplet>'+nc(48,'eighth')+'<endTuplet/><endTuplet/>'+tail('-1/15')+'<Rest><durationType>quarter</durationType><duration>14/15</duration></Rest>')});
test('normalized paired tremolo retains metric clock with Ottava',{source:source(direction('down')+sn('C',1,'<notations><ornaments><tremolo type="start">2</tremolo></ornaments></notations>')+sn('D',1,'<notations><ornaments><tremolo type="stop">2</tremolo></ornaments></notations>')+direction('stop',null)+sr(2)),native:native(head('8va','1/2')+nc(48,'quarter','<Tremolo><subtype>c16</subtype></Tremolo>')+nc(50)+tail('-1/2')+nr('half',0))});
test('trill annotation does not expand the Ottava identity clock',{source:fixture().source.replace('</note>','<notations><ornaments><trill-mark/></ornaments></notations></note>'),native:fixture().native.replace('<durationType>quarter</durationType>','<Articulation><subtype>ornamentTrill</subtype></Articulation><durationType>quarter</durationType>')});

 globalThis.hairpinOttavaIdentityControls=rows.map(r=>({...r,success:true}));
})();
