# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Compile actual pickup ingress call slices with current pure Java helpers.

Uses only Python's standard library and a supplied JDK/core classpath. This is a
caller-wiring regression, not an Android runtime, OCR, source-score or decoder test.
No caller condition is mirrored in Python or the authored Java fixture.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def call_slice(path):
    text = path.read_bytes().decode('utf-8')
    matches = list(re.finditer(r'\bfinalInterpretation\s*\.\s*withOpeningPickup\s*\(',text))
    if len(matches) != 1:
        raise ValueError(f'Expected exactly one actual pickup ingress: {path}, found{len(matches)}')
    start = matches[0].start(); opening = matches[0].end()-1
    depth = 0; quote = None; escaped = False; comment = None; i = opening
    while i < len(text):
        ch=text[i]; pair=text[i:i+2]
        if comment == 'line':
            if ch == '\n': comment=None
        elif comment == 'block':
            if pair == '*/': comment=None;i+=1
        elif quote:
            if escaped: escaped=False
            elif ch == '\\': escaped=True
            elif ch == quote: quote=None
        elif pair == '//': comment='line';i+=1
        elif pair == '/*': comment='block';i+=1
        elif ch in ['"',"'"]: quote=ch
        elif ch == '(': depth+=1
        elif ch == ')':
            depth-=1
            if depth == 0:
                expression=text[start:i+1]
                return expression, text.count('\n',0,start)+1
        i+=1
    raise ValueError(f'Unterminated actual pickup call: {path}')

def run(command,cwd):
    return subprocess.run([str(x) for x in command],cwd=cwd,text=True,
            stdout=subprocess.PIPE,stderr=subprocess.PIPE,encoding='utf-8',errors='replace')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--app',type=Path)
    parser.add_argument('--public',type=Path,required=True)
    parser.add_argument('--classpath',required=True)
    parser.add_argument('--javac',default='javac')
    parser.add_argument('--java',default='java')
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--fixture',type=Path)
    args=parser.parse_args()
    args.classpath=os.pathsep.join(str(Path(item).resolve()) for item in args.classpath.split(os.pathsep))
    args.public=args.public.resolve();args.out=args.out.resolve()
    if args.app is not None: args.app=args.app.resolve()
    if args.fixture is None:
        args.fixture=args.public/'java/src/test/resources/pickup-ingress/PickupIngressOriginalFixture.java'
    args.fixture=args.fixture.resolve()
    package='com.musicsheets.app.pdf' if args.app else 'io.github.luckolite.interpreter'
    core=(args.app/'app/src/main/java/com/musicsheets/app/pdf' if args.app else
            args.public/'java/src/main/java/io/github/luckolite/interpreter')
    paths={}
    if args.app:
        paths.update(android=core/'OmrMeasureAnalyzer.java',
                native=args.app/'tools/native-worker/NativePageGuide.java')
    paths['public']=args.public/'java/src/main/java/io/github/luckolite/interpreter/SheetInterpreter.java'
    args.out.mkdir(parents=True,exist_ok=False)
    source=args.out/'sources';source.mkdir();classes=args.out/'classes';classes.mkdir()
    slices={};methods=[]
    for entry,path in paths.items():
        expression,line=call_slice(path)
        destination=source/(entry+'-exact-expression.txt');destination.write_bytes((expression+'\n').encode('utf-8'))
        slices[entry]={'source':str(path),'sourceSHA256':sha(path),'line':line,
                'expression':expression,'expressionSHA256':hashlib.sha256(expression.encode()).hexdigest()}
        method={'android':'android','native':'nativeEntry','public':'publicEntry'}[entry]
        methods.append('static ScorePageInterpretation '+method+'(ScorePageInterpretation finalInterpretation, int firstMeasureNumber, List<MeasureNumberReconciler.NumberToken> numberTokens, List<MeasureNumberReconciler.NumberToken> measureNumbers, List<MeasureNumberReconciler.NumberToken> numbers) {\nreturn '+expression+';\n}')
    if not args.app:
        # The unchanged authored fixture statically references all entry names.
        # Missing entries fail closed; they are never run or used as fake conditions.
        for method in ['android','nativeEntry']:
            methods.append('static ScorePageInterpretation '+method+'(ScorePageInterpretation score, int first, List<MeasureNumberReconciler.NumberToken> a, List<MeasureNumberReconciler.NumberToken> b, List<MeasureNumberReconciler.NumberToken> c) { throw new UnsupportedOperationException("Entry unavailable in public-only mode"); }')
    adapter=source/'PickupIngressActualSlices.java'
    adapter.write_bytes(('package '+package+';\nimport java.util.*;\nfinal class PickupIngressActualSlices {\n'+'\n'.join(methods)+'\n}\n').encode('utf-8'))
    fixture_text=args.fixture.read_bytes().decode('utf-8')
    declarations=list(re.finditer(r'(?m)^package ([A-Za-z0-9_.]+);',fixture_text))
    if len(declarations)!=1:raise ValueError('Fixture must have exactly one package declaration')
    original_package=declarations[0].group(1)
    if original_package not in ['com.musicsheets.app.pdf','io.github.luckolite.interpreter']:
        raise ValueError('Unexpected fixture package')
    declaration=declarations[0]
    fixture_text=fixture_text[:declaration.start(1)]+package+fixture_text[declaration.end(1):]
    fixture=source/args.fixture.name;fixture.write_bytes(fixture_text.encode('utf-8'))
    helpers=['OpeningPickupContent.java','MeasureNumberReconciler.java','ScorePageInterpretation.java',
            'ScoreNoteEvent.java','ScoreRestEvent.java','ScoreMeterChange.java','MeasureRegion.java']
    pins={str(Path(__file__)):sha(Path(__file__)),str(args.fixture):sha(args.fixture),str(adapter):sha(adapter)}
    helper_sources=[]
    for name in helpers:
        original=core/name;target=source/name;target.write_bytes(original.read_bytes());pins[str(original)]=sha(original);helper_sources.append(target)
    for item in args.classpath.split(os.pathsep):
        path=Path(item)
        if path.is_file():pins[str(path)]=sha(path)
    receipt={'scope':'ORIGINAL_ACTUAL_CALLER_SOURCE_SLICES_NO_ANDROID_RUNTIME_OR_SCORE_ASSETS',
            'conditionCopiedVerbatim':True,'entrySlices':slices,'pins':pins,'fixtureCountPerEntry':15,
            'mode':'app-native-public' if args.app else 'public-only',
            'corePackage':package,'fixtureOnlyRewrite':{'originalPackage':original_package,'selectedPackage':package},
            'canonicalWrites':False,'inferenceRuns':0,'results':{}}
    compiled=run([args.javac,'-cp',args.classpath,'-d',classes,adapter,fixture,*helper_sources],args.out)
    (args.out/'compile.stdout').write_text(compiled.stdout,encoding='utf-8');(args.out/'compile.stderr').write_text(compiled.stderr,encoding='utf-8')
    if compiled.returncode:
        receipt.update(status='COMPILE_ERROR_NOT_BEHAVIOR_RED',compileExit=compiled.returncode)
        (args.out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
        print(compiled.stderr);return 2
    for entry in paths:
        tested=run([args.java,'-Xmx128m','-cp',str(classes)+os.pathsep+args.classpath,package+'.PickupIngressOriginalFixture',entry],args.out)
        (args.out/(entry+'.stdout')).write_text(tested.stdout,encoding='utf-8');(args.out/(entry+'.stderr')).write_text(tested.stderr,encoding='utf-8')
        cases=[]
        for row in tested.stdout.splitlines():
            if row.startswith('CASE\t'):
                _,name,case,expected,actual,number,first,status=row.split('\t')
                cases.append({'case':case,'expected':expected=='true','actual':actual=='true','numberingProof':number=='true','derivedFirstMeasureNumber':int(first),'status':status})
        receipt['results'][entry]={'exitCode':tested.returncode,'cases':cases,'passed':sum(c['status']=='PASS'for c in cases),'behaviorRed':sum(c['status']=='BEHAVIOR_RED'for c in cases)}
        if len(cases)!=15 or (tested.returncode and 'BEHAVIOR_RED' not in tested.stderr):
            receipt['status']='RUNTIME_OR_FIXTURE_ERROR_NOT_BEHAVIOR_RED'
            (args.out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8');print(tested.stderr);return 2
    failed=any(r['behaviorRed']for r in receipt['results'].values())
    receipt['status']='ACTUAL_CALLER_BEHAVIOR_RED' if failed else 'ALL_ACTUAL_CALLER_CONTROLS_PASS'
    (args.out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':receipt['status'],'receipt':str(args.out/'receipt.json'),
        'receiptSHA256':sha(args.out/'receipt.json'),'results':{k:{n:v for n,v in r.items()if n!='cases'}for k,r in receipt['results'].items()}},indent=2))
    return 1 if failed else 0

if __name__=='__main__':sys.exit(main())
