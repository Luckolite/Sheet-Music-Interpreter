# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original junction challenges; no score, font, model or oracle input."""
from pathlib import Path
import json,math,numpy as np
from PIL import Image,ImageDraw
O=Path(__file__).resolve().parent;results=[];manifest=[];OUT=O/"junctions";OUT.mkdir(exist_ok=True)
for down in(False,True):
 for scale in(1,2):
  tag=('down'if down else'up')+f'-{scale}x'
  for case in('flat_beam_through_flag','sloped_beam_through_flag','numeral_extra_core_staff','slur_extra_core_staff'):
   base='single'if 'beam_through'in case else 'tuplet_contact'
   d=O/f'{base}-{tag}';t=json.loads((d/'truth.json').read_text());raw=np.frombuffer((d/'gray.bin').read_bytes(),np.uint8).copy().reshape(t['height'],t['width']);c=np.zeros_like(raw,dtype=bool);u=c.copy();s=t['stem'];n=dict(left=s['left'],right=s['right'],cap=s['cap'],start=round(s['start']),direction=t['direction']);hw=-t['direction'];g=t['glyphGap'];sx=n['right'];cap=n['cap'];heads=[(0,t['head']['x'],t['head']['y'],g)];rivals=[]
   im=Image.fromarray(raw);draw=ImageDraw.Draw(im)
   if 'beam_through'in case:
    rx=sx+round(g*2);delta=round(g*.55)if case.startswith('sloped')else 0;thick=max(3,round(g*.32));draw.polygon([(sx,cap),(rx,cap+hw*delta),(rx,cap+hw*(delta+thick-1)),(sx,cap+hw*(thick-1))],fill=0)
    rend=cap+hw*delta;draw.rectangle((rx-1,min(rend,n['start']),rx,max(rend,n['start'])),fill=0);rivals=[dict(id=1,left=rx-1,right=rx,cap=rend,start=n['start'],direction=n['direction'],gap=g)];heads.append((1,rx-g*.3,t['head']['y'],g))
   else:
    # Five literal rule bands remain carrier; an extra dark touching branch
    # THROUGH one band is certain residual foreground, never masked away.
    for j in range(5):
     yy=cap+hw*round(g*(.5+j));a,b=sorted((yy,yy+hw*max(1,scale)));draw.rectangle((10*scale,a,149*scale,b),fill=80);c[a:b+1,10*scale:149*scale+1]=True
    yy=cap+hw*round(g*1.3);x=sx+round(g*.7);draw.line([(x,yy-hw*round(g*.8)),(x+round(g*.4),yy),(x,yy+hw*round(g*.8))],fill=0,width=3*scale)
    # Residual curve ink at its crossing is EXTRA_CORE, not carrier-only.
    for y in range(max(0,yy-scale*2),min(raw.shape[0],yy+scale*2+1)):c[y,max(0,x):x+round(g*.5)+1]=False
    if case.startswith('slur'):
     draw.arc((sx-round(g*.2),cap-round(g),sx+round(g*1.8),cap+round(g)),0 if hw>0 else 180,180 if hw>0 else 360,fill=0,width=3*scale)
   raw=np.array(im)
   dest=OUT/f'{case}-{tag}';dest.mkdir(exist_ok=True);(dest/'gray.bin').write_bytes(raw.tobytes());(dest/'carrier.bin').write_bytes(c.astype(np.uint8).tobytes());im.save(dest/'source.png')
   text=';'.join(','.join(str(r[k])for k in['left','right','cap'])+','+str(heads[1][1])+','+str(heads[1][2])for r in rivals)
   manifest.append('\t'.join(map(str,[f'{case}-{tag}',(dest.relative_to(OUT)/'gray.bin').as_posix(),(dest.relative_to(OUT)/'carrier.bin').as_posix(),t['width'],t['height'],g,n['direction'],n['left'],n['right'],cap,n['start'],heads[0][1],heads[0][2],0,text])))
(OUT/'fixtures.tsv').write_text('\n'.join(manifest)+'\n')
