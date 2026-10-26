// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Source-only validation of portable browser suite using the read-only DOM facade.
const fs=require('fs'),path=require('path'),vm=require('vm'),{parse}=require('./readonly_dom.cjs');
const W=path.join(__dirname,'..'),ctx=vm.createContext({DOMParser:class{parseFromString(x){return parse(x);}},XMLSerializer:class{serializeToString(x){return JSON.stringify(x);}}});
for(const file of ['hairpin-source.js','hairpin-identity.js','tests/hairpin-ottava-identity.controls.js'])vm.runInContext(fs.readFileSync(path.join(W,file),'utf8'),ctx);
if(ctx.hairpinOttavaIdentityControls.length!==99||!ctx.hairpinOttavaIdentityControls.every(x=>x.success))throw Error('Portable browser control census');
console.log(JSON.stringify({scope:'SOURCE_ONLY_BROWSER_SUITE_READONLY_DOM_NOT_BROWSER_NATIVE',cases:99,allPass:true}));
