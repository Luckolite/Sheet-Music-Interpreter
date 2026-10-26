// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
// Synthetic compatibility controls. No native/browser execution.
const fs=require('fs'),path=require('path'),vm=require('vm'),crypto=require('crypto');
const xml=require(process.env.MUSIC_SHEETS_XML_JS || 'xml-js');
const W=path.join(__dirname,'..'),assets=W;
const O=process.env.MUSIC_SHEETS_TEST_OUTPUT || path.join(require('os').tmpdir(),'sheet-engraving-'+process.pid);fs.mkdirSync(O,{recursive:true});
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
class Element {
 constructor(x){this.localName=x.name.split(':').pop();this.tagName=x.name;this.attributeValues=x.attributes||{};this.attributes=Object.entries(this.attributeValues).map(([name,value])=>({name,value}));this.children=(x.elements||[]).filter(n=>n.type==='element').map(n=>new Element(n));this._parts=x.elements||[];}
 getAttribute(k){return Object.hasOwn(this.attributeValues,k)?this.attributeValues[k]:null;}
 get textContent(){return this._parts.map(p=>p.type==='text'?p.text:p.type==='cdata'?p.cdata:p.type==='element'?new Element(p).textContent:'').join('');}
 getElementsByTagName(k){return this.children.flatMap(c=>[...(c.tagName===k?[c]:[]),...c.getElementsByTagName(k)]);}
 querySelectorAll(q){const parts=q.split('>').map(s=>s.trim());if(parts.length===2)return this.getElementsByTagName(parts[0]).flatMap(p=>p.children.filter(c=>c.localName===parts[1]));throw Error('Unsupported audit selector '+q);}
}
function parse(text){const x=xml.xml2js(text,{compact:false});const roots=x.elements.filter(n=>n.type==='element');if(roots.length!==1)throw Error('Expected one audit XML root');const root=new Element(roots[0]);return {documentElement:root,getElementsByTagName:k=>[...(root.tagName===k?[root]:[]),...root.getElementsByTagName(k)],querySelectorAll:q=>root.querySelectorAll(q)};}
const identity=fs.readFileSync(path.join(W,'hairpin-identity.js'),'utf8');
const needle='    let noteCount=0;',overlay='    globalThis.identityObserved={mapping,sourceEvents:Object.fromEntries(sourceEvents),nativeEvents:Object.fromEntries(nativeEvents),plan};\n'+needle;
if(identity.split(needle).length!==2)throw Error('Observer anchor changed');const observed=identity.replace(needle,overlay);if(observed.replace(overlay,needle)!==identity)throw Error('Observer inverse');
const ctx=vm.createContext({console});vm.runInContext(fs.readFileSync(path.join(assets,'hairpin-source.js'),'utf8'),ctx);vm.runInContext(observed,ctx);
const attr=(div=1,staves=1)=>`<attributes><divisions>${div}</divisions><time><beats>4</beats><beat-type>4</beat-type></time><staves>${staves}</staves></attributes>`;
const note=(step='C',dur=1,extra='',staff=1,voice=1)=>`<note>${extra}<pitch><step>${step}</step><octave>4</octave></pitch><duration>${dur}</duration><voice>${voice}</voice><staff>${staff}</staff></note>`;
const rest=d=>`<note><rest/><duration>${d}</duration></note>`,backup=d=>`<backup><duration>${d}</duration></backup>`;
const chord=(pitch=60,type='quarter',extra='',notes='')=>`<Chord>${extra}<durationType>${type}</durationType><Note><pitch>${pitch}</pitch></Note>${notes}</Chord>`;
const nr=(type='quarter',extra='')=>`<Rest><durationType>${type}</durationType>${extra}</Rest>`;
const source=(body,a=attr())=>`<score-partwise><part-list><score-part id="A"/></part-list><part id="A"><measure>${a}${body}</measure></part></score-partwise>`;
const native=(body)=>`<museScore><Score><Part><Staff id="1"/></Part><Staff id="1"><Measure><voice>${body}</voice></Measure></Staff></Score></museScore>`;
const results=[];
function test(name,s,n,expected,kind='CONSTRUCTED_DOM_CONTRACT_NOT_NATIVE_RUNTIME'){
 let value,error='',events;ctx.identityObserved=null;try{value=ctx.HairpinIdentity.identify(parse(s),parse(n));}catch(x){error=String(x.message);}events=ctx.identityObserved;
 const pass=expected==='PASS'?!error:!!error&&(expected==='REJECT'||error.includes(expected));if(!pass)throw Error(name+': '+error);
 results.push({name,kind,expected,passed:pass,result:value||null,error,events,sourceXML:s,nativeXML:n});
}
test('ordinary metric note',source(note()+rest(3)),native(chord()+nr('half','<dots>1</dots>')),'PASS');
test('exact seventh explicit fraction',source(note('C',4)+rest(24),attr(7)),native(chord(60,'quarter','<duration>1/7</duration>')+nr('quarter','<duration>6/7</duration>')),'PASS');
test('nested tuplet',source(note('C',4)+rest(56),attr(15)),native('<Tuplet><normalNotes>2</normalNotes><actualNotes>3</actualNotes></Tuplet><Tuplet><normalNotes>4</normalNotes><actualNotes>5</actualNotes></Tuplet>'+chord(60,'eighth')+'<endTuplet/><endTuplet/>'+nr('whole','<duration>14/15</duration>')),'PASS');
test('dotted note',source(note('C',3)+rest(1)),native(chord(60,'half','<dots>1</dots>')+nr()),'PASS');
test('grace before principal zero duration',source(note('D',1,'<grace/>')+note('C',4)),native(chord(62,'eighth','<acciaccatura/>')+chord(60,'whole')),'PASS');
test('after grace at principal end',source(note('C',4)+note('D',1,'<grace steal-time-previous="10"/>')),native(chord(60,'whole')+chord(62,'eighth','<grace8after/>')),'PASS');
test('grace cannot masquerade as metric note',source(note('D',1,'<grace/>')+note('C',4)),native(chord(62,'eighth')+chord(60,'whole')),'REJECT');
const polyNative=(a,b)=>`<museScore><Score><Part><Staff id="1"/></Part><Staff id="1"><Measure><voice>${a}</voice><voice>${b}</voice></Measure></Staff></Score></museScore>`;
test('polyphonic backup',source(note('C',4)+backup(4)+note('D',4,'',1,2)),polyNative(chord(60,'whole'),chord(62,'whole')),'PASS');
test('pitched voice exchange is outside identity signature',source(note('C',4)+backup(4)+note('D',4,'',1,2)),polyNative(chord(62,'whole'),chord(60,'whole')),'PASS','PROJECTION_LIMIT_NOT_A_PROVED_HAIRPIN_DEFECT');
test('duplicate unison multiplicity preserved',source(note('C',4)+backup(4)+note('C',4,'',1,2)),polyNative(chord(60,'whole'),chord(60,'whole')),'PASS');
test('missing duplicate unison refused',source(note('C',4)+backup(4)+note('C',4,'',1,2)),native(chord(60,'whole')),'Source/native note identity differs');
test('pitched chord onset',source(note('C',4)+note('E',4,'<chord/>')),native(chord(60,'whole','','<Note><pitch>64</pitch></Note>')),'PASS');
test('wrong chord member refused',source(note('C',4)+note('E',4,'<chord/>')),native(chord(60,'whole','','<Note><pitch>65</pitch></Note>')),'Source/native note identity differs');
test('orphan chord refused',source(note('E',4,'<chord/>')),native(chord(64,'whole')),'Orphan chord');
test('forward and native location',source('<forward><duration>2</duration></forward>'+note('C',2)),native('<location><fractions>1/2</fractions></location>'+chord(60,'half')),'PASS');
test('single repeated tremolo keeps same metric clock',source(note('C',4,'<notations><ornaments><tremolo type="single">3</tremolo></ornaments></notations>')),native(chord(60,'whole','<Tremolo><subtype>r32</subtype></Tremolo>')),'PASS');
const pairedSource=source(note('C',1,'<type>half</type><notations><ornaments><tremolo type="start">2</tremolo></ornaments></notations>')+note('D',1,'<type>half</type><notations><ornaments><tremolo type="stop">2</tremolo></ornaments></notations>')+rest(2));
test('paired annotation with already normalized actual durationType',pairedSource,native(chord(60,'quarter','<Tremolo><subtype>c16</subtype></Tremolo>')+chord(62)+nr('half')),'PASS','COUNTERCONTROL_BLANKET_HALF_FACTOR_WOULD_BREAK');
test('paired printed durationType differs from logical timeline',pairedSource,native(chord(60,'half','<Tremolo><subtype>c16</subtype></Tremolo>')+chord(62,'half')),'Source/native note identity differs','CONSTRUCTED_COUNTEREXAMPLE_NOT_PROOF_ACTUAL_BUNDLE_EMITS_THIS');
test('paired semantic tag alone does not alter walker',source(note('C',4)),native(chord(60,'whole','<Tremolo><subtype>c16</subtype></Tremolo>')),'PASS','PROJECTION_LIMIT_PAIR_VALIDITY_IS_NOT_THIS_IDENTIFY_CONTRACT');
test('trill annotation retains notated duration',source(note('C',4,'<notations><ornaments><trill-mark/></ornaments></notations>')),native(chord(60,'whole','<Articulation><subtype>ornamentTrill</subtype></Articulation>')),'PASS');
test('expanded trill attacks cannot silently replace source note',source(note('C',4,'<notations><ornaments><trill-mark/></ornaments></notations>')),native(chord(60,'half')+chord(62,'half')),'Source/native note identity differs');
const twoStaffSource=source(note('C',4,'',2),attr(1,2));
const twoStaffNative=`<museScore><Score><Part><Staff id="1"/><Staff id="2"/></Part><Staff id="1"><Measure><voice>${chord(60,'whole','<staffMove>1</staffMove>')}</voice></Measure></Staff><Staff id="2"><Measure><voice/></Measure></Staff></Score></museScore>`;
test('cross-staff move within exact part',twoStaffSource,twoStaffNative,'PASS');
test('cross-part staff move refused',source(note('C',4)),native(chord(60,'whole','<staffMove>1</staffMove>')),'Cross-part staff move');
test('instrument-change refused',source(note('C',4)),native('<InstrumentChange/>'+chord(60,'whole')),'Native instrument changes');
test('wrong native pitch refused',source(note('C',4)),native(chord(61,'whole')),'Source/native note identity differs');
test('pitched spelling tpc is outside this signature',source(note('C',4)),native(chord(60,'whole').replace('</Note>','<tpc>999</tpc></Note>')),'PASS','PROJECTION_LIMIT_NOT_FULL_NOTATION_IDENTITY');
test('tonal cross head does not become unpitched source',source(note('C',4,'<notehead>x</notehead>')),native(chord(60,'whole').replace('</Note>','<head>cross</head></Note>')),'PASS','PITCHED_NOTEHEAD_IS_APPEARANCE_NOT_PERCUSSION');
test('unpitched needs explicit carrier proof',source('<note><unpitched><display-step>C</display-step><display-octave>4</display-octave></unpitched><duration>4</duration><type>whole</type><notehead>x</notehead></note>'),native(chord(60,'whole').replace('</Note>','<head>cross</head></Note>')),'Unpitched source needs verified');
test('native unmatched tuplet refused',source(note('C',4)),native('<endTuplet/>'+chord(60,'whole')),'Unmatched native tuplet');
test('native unclosed tuplet refused',source(note('C',4)),native('<Tuplet><normalNotes>1</normalNotes><actualNotes>1</actualNotes></Tuplet>'+chord(60,'whole')),'Unclosed native tuplet');
test('native cursor underflow refused',source(note('C',4)),native('<location><fractions>-1/4</fractions></location>'+chord(60,'whole')),'Native cursor underflow');
const mmSource='<score-partwise><part-list><score-part id="A"/></part-list><part id="A"><measure>'+attr()+rest(4)+'</measure><measure>'+rest(4)+'</measure><measure>'+note('C',4)+'</measure></part></score-partwise>';
const mmNative='<museScore><Score><Part><Staff id="1"/></Part><Staff id="1"><Measure><voice>'+nr('measure')+'</voice></Measure><Measure len="2"><multiMeasureRest>2</multiMeasureRest><voice>'+nr('measure','<duration>2</duration>')+'</voice></Measure><Measure><voice>'+nr('measure')+'</voice></Measure><Measure><voice>'+chord(60,'whole')+'</voice></Measure></Staff></Score></museScore>';
test('multi-rest graphic does not own timeline',mmSource,mmNative,'PASS');
test('multi-rest graphic cannot cover note',mmSource,mmNative.replace('<Measure><voice>'+nr('measure')+'</voice></Measure>','<Measure><voice>'+chord(60,'whole')+'</voice></Measure>'),'Multi-rest graphic covers sounding notes');
const transpose='<attributes><transpose><diatonic>-1</diatonic><chromatic>-2</chromatic></transpose></attributes>';
const instrument='<Instrument><transposeChromatic>-2</transposeChromatic><transposeDiatonic>-1</transposeDiatonic></Instrument>';
test('written pitch plus exact instrument transposition',source(note('C',4),attr()+transpose),native(chord(58,'whole')).replace('</Part>',instrument+'</Part>'),'PASS');
test('wrong diatonic instrument interval refused',source(note('C',4),attr()+transpose),native(chord(58,'whole')).replace('</Part>',instrument.replace('>-1<','>-2<')+'</Part>'),'Source/native instrument transposition differs');
const result={scope:'READ_ONLY_UNCHANGED_JS_CONSTRUCTED_DOM_AUDIT_NOT_NATIVE_RUNTIME',readonlyDOMAPI:['children','localName','tagName','getAttribute','textContent','getElementsByTagName','querySelectorAll encoding>software'],xmlJS:'xml-js test-only dependency',identitySHA256:sha(identity),observedIdentitySHA256:sha(observed),exactObserverInverse:true,sourceSHA256:sha(fs.readFileSync(path.join(assets,'hairpin-source.js'))),cases:results.length,results};
fs.writeFileSync(path.join(O,'existing35-candidate-results.json'),JSON.stringify(result,null,2));
console.log(JSON.stringify({cases:results.length,allExpectedOutcomesPass:true,actualNativeExecuted:false,sha256:sha(fs.readFileSync(path.join(O,'existing35-candidate-results.json')))}));
