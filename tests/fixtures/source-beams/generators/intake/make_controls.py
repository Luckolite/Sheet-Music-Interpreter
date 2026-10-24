# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original caller-boundary rasters; no score, trained model, or gold input.

Fixture consistency is tested here. Actual intake acceptance remains a separate
test for the complete producer once its API is sealed.
"""
from pathlib import Path
from collections import deque
import hashlib,json
from PIL import Image,ImageDraw

O=Path(__file__).resolve().parent;W,H=96,112
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
CASES=['primary_connected','left_terminal_gutter','right_terminal_gutter','both_terminal_gutters',
    'forward_hook_connected','forward_hook_root_gutter','head_shaft_orphan',
    'unbeamed_nonshaft_head_rival','shared_shaft_chord','nearby_distinct_shafts']

def head(d,x,y):
    for yy in range(y-4,y+5):
        for xx in range(x-6,x+7):
            if (xx-x)**2/36+(yy-y)**2/16<=1:d.point((xx,yy),fill=0)

def local_path(image,x,y,shaft_x):
    box=(max(0,x-8),y-8,min(W-1,shaft_x+2),y+8)
    target=(shaft_x,y-2);seen={(x,y)};q=deque(seen)
    while q:
        p=q.popleft()
        if p==target:return True
        for dx in [-1,0,1]:
            for dy in [-1,0,1]:
                n=(p[0]+dx,p[1]+dy)
                if n not in seen and box[0]<=n[0]<=box[2]and box[1]<=n[1]<=box[3]and image.getpixel(n)<165:
                    seen.add(n);q.append(n)
    return False

results=[];thumbs=[]
for name in CASES:
    image=Image.new('L',(W,H),255);d=ImageDraw.Draw(image)
    for yy in [30,40,50,60,70]:d.line((4,yy,91,yy),fill=0)
    left=26 if name=='head_shaft_orphan' else 24
    notes=[dict(id=0,x=18,y=70,beamCount=1,onset=0),dict(id=1,x=66,y=70,beamCount=1,onset=.5)]
    head(d,18,70);head(d,66,70)
    d.line((left,20,left,68 if name=='head_shaft_orphan' else 70),fill=0)
    d.line((72,20,72,70),fill=0)
    d.rectangle((left,20,72,25),fill=0)
    gutters=[]
    if name in ['left_terminal_gutter','both_terminal_gutters']:gutters.append(left+1)
    if name in ['right_terminal_gutter','both_terminal_gutters']:gutters.append(71)
    for x in gutters:d.line((x,20,x,25),fill=255)
    expected=dict(mainBridgeEndpoints=[0,1],mainPrimaryBridge=name not in ['left_terminal_gutter','right_terminal_gutter','both_terminal_gutters','head_shaft_orphan','nearby_distinct_shafts'],secondaryForwardHook=False)
    if name.startswith('forward_hook'):
        notes[0]['beamCount']=2;d.rectangle((left,32,38,37),fill=0)
        expected['secondaryForwardHook']=name=='forward_hook_connected'
        if name=='forward_hook_root_gutter':d.line((left+1,32,left+1,37),fill=255)
    obligations=[]
    if name=='unbeamed_nonshaft_head_rival':
        head(d,left,50);notes.append(dict(id=2,x=left,y=50,beamCount=0,onset=.25))
        expected['mainPrimaryBridge']=None
        obligations=['Do not ignore this valid head merely because it has no beamCount or independently captured shaft.',
            'A node may own the shaft only if the rival is excluded by evidence or shares the same authenticated shaft/onset; otherwise retain unknown.']
    if name=='shared_shaft_chord':
        head(d,18,84);d.line((24,20,24,84),fill=0)
        notes.append(dict(id=2,x=18,y=84,beamCount=1,onset=0))
        expected['leftColumnMembers']=[0,2]
    if name=='nearby_distinct_shafts':
        head(d,20,84);d.line((26,20,26,84),fill=0)
        notes.append(dict(id=2,x=20,y=84,beamCount=1,onset=.25))
        # Remove the new shaft's top horizontal contact so it remains separate.
        d.line((25,20,25,25),fill=255)
        expected['leftColumnMembersMustNotMerge']=[0,2]
        obligations=['Observed x/cap closeness does not prove shared physical shaft.',
            'A rival capture ambiguity may reject rather than invent a chord; inspect every head before filtering beam-positive candidates.']
    root_left=all(image.getpixel((left+1,y))<165 for y in range(20,26))
    root_right=all(image.getpixel((71,y))<165 for y in range(20,26))
    if gutters:assert not(root_left and root_right)
    if name=='primary_connected':assert root_left and root_right and local_path(image,18,70,left)
    if name=='head_shaft_orphan':assert not local_path(image,18,70,left)
    if name=='shared_shaft_chord':assert local_path(image,18,70,24)and local_path(image,18,84,24)
    if name=='forward_hook_root_gutter':assert all(image.getpixel((left+1,y))==255 for y in range(32,38))
    target=O/name;target.mkdir(exist_ok=True);image.save(target/'source.png');(target/'gray.bin').write_bytes(image.tobytes())
    truth=dict(name=name,width=W,height=H,gap=10,staffRows=[30,40,50,60,70],notes=notes,
        observedShafts=[dict(left=left,right=left,endY=20,direction=-1),dict(left=72,right=72,endY=20,direction=-1)],
        expected=expected,callerObligations=obligations,
        truthBoundary='Declared original drawing topology, not inferred intake output. Same-page source/onset facts explicit; not a full musical measure.',
        sourceSHA256=sha(target/'source.png'),graySHA256=sha(target/'gray.bin'))
    (target/'truth.json').write_text(json.dumps(truth,indent=2)+'\n')
    results.append(dict(name=name,truthSHA256=sha(target/'truth.json')));thumbs.append((name,image))
sheet=Image.new('RGB',(5*192,2*256),'white');s=ImageDraw.Draw(sheet)
for i,(name,image)in enumerate(thumbs):
    x=(i%5)*192;y=(i//5)*256;sheet.paste(image.resize((192,224)),(x,y+30));s.text((x+2,y+2),name[:28],fill='black')
sheet.save(O/'contact-sheet.png')
receipt=dict(status='ORIGINAL_INTAKE_FIXTURE_CONSISTENCY_PASS',cases=len(results),results=results,
    generatorSHA256=sha(Path(__file__)),license='Apache-2.0',
    checks='Literal one-column gutters, near-head8connected corridor, true shared-shaft chord, and hook root gap checked from raw pixels.',
    productionIntakeTested=False,boundary='Procedural inputs only. Rival case records conditional unknown contract; no invented output requirement for ambiguous source. No model/sourcegold input or owner file edits.')
(O/'fixture-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps(dict(status=receipt['status'],cases=len(results),receiptSHA256=sha(O/'fixture-receipt.json'))))
