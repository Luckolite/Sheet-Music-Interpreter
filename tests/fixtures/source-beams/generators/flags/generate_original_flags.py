# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original procedural flag controls. No score, font, model or manual gold input."""
from pathlib import Path
import json,hashlib,math
from PIL import Image,ImageDraw
O=Path(__file__).resolve().parent
BASE=[('single',1),('double',2),('staff_crossing',1),('straight_beamlet',0),('sloping_beamlet',0),('floating_flag',0),('carrier_root_bridge',0),('staff_only',0),('outward_slur',0),('tuplet_contact',0),('foreign_left_flag',0),('full_beam_to_rival',0),('cue_single',1),('cue_floating_flag',0)]
def bezier(points,n=40):
 a,b,c,d=points
 return [((1-t)**3*a[0]+3*(1-t)**2*t*b[0]+3*(1-t)*t*t*c[0]+t**3*d[0],(1-t)**3*a[1]+3*(1-t)**2*t*b[1]+3*(1-t)*t*t*c[1]+t**3*d[1])for t in[i/n for i in range(n+1)]]
def flag(d,x,y,g=1):
 outer=bezier([(0,0),(19,8),(20,24),(8,39)])
 inner=bezier([(8,39),(17,22),(9,14),(0,10)])
 d.polygon([(round(x+px*g),round(y+py*g))for px,py in outer+inner],fill=0)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 rows=[];thumbs=[]
 for name,expected in BASE:
  for down in[False,True]:
   for scale in[1,2]:
    w=h=160;sx=60;cap=30;glyph=.7 if name.startswith('cue_')else 1.;gap=16*glyph;headY=125
    im=Image.new('L',(w,h),255);d=ImageDraw.Draw(im);carrier=Image.new('L',(w,h),0);cd=ImageDraw.Draw(carrier)
    d.rectangle((sx-1,cap,sx,headY),fill=0);d.ellipse((sx-12,headY-4,sx+1,headY+4),fill=0)
    if name in['single','double','staff_crossing','cue_single']:flag(d,sx,cap,glyph)
    if name=='double':flag(d,sx,cap+14,1.)
    if name in['floating_flag','carrier_root_bridge','cue_floating_flag']:flag(d,sx+5,cap,glyph)
    if name=='straight_beamlet':d.rectangle((sx,cap,sx+22,cap+5),fill=0)
    if name=='sloping_beamlet':d.polygon([(sx,cap),(sx+24,cap+12),(sx+24,cap+18),(sx,cap+6)],fill=0)
    if name=='outward_slur':
     curve=bezier([(sx,cap),(sx+8,cap-22),(sx+33,cap-24),(sx+42,cap)])
     d.line([(round(x),round(y))for x,y in curve],fill=0,width=3)
    if name=='tuplet_contact':
     d.line([(sx,cap+16),(sx+8,cap+16),(sx+16,cap+10),(sx+22,cap+17),(sx+15,cap+23),(sx+22,cap+29),(sx+15,cap+36),(sx+7,cap+31)],fill=0,width=3)
    rivals=[]
    if name=='foreign_left_flag':
     rx=35;d.rectangle((rx-1,cap,rx,headY),fill=0);flag(d,rx,cap);rivals.append(dict(left=rx-1,right=rx,cap=cap,headX=rx-5,headY=headY))
    if name=='full_beam_to_rival':
     rx=112;d.rectangle((rx-1,cap,rx,headY),fill=0);d.ellipse((rx-12,headY-4,rx+1,headY+4),fill=0);d.rectangle((sx,cap,rx,cap+5),fill=0);rivals.append(dict(left=rx-1,right=rx,cap=cap,headX=rx-5,headY=headY))
    if name in['staff_crossing','staff_only','carrier_root_bridge']:
     bands=[(31,32),(47,48),(63,64),(79,80),(95,96)]
     for top,bottom in bands:d.rectangle((10,top,149,bottom),fill=0);cd.rectangle((10,top,149,bottom),fill=255)
    if down:
     im=im.transpose(Image.Transpose.FLIP_TOP_BOTTOM);carrier=carrier.transpose(Image.Transpose.FLIP_TOP_BOTTOM);cap=h-1-cap;headY=h-1-headY
     for r in rivals:r['cap']=h-1-r['cap'];r['headY']=h-1-r['headY']
    for r in rivals:
     r.update(left=r['left']*scale,right=(r['right']+1)*scale-1,cap=(r['cap']+1)*scale-1 if down else r['cap']*scale,headX=(r['headX']+.5)*scale-.5,headY=(r['headY']+.5)*scale-.5)
    im=im.resize((w*scale,h*scale),Image.Resampling.NEAREST);carrier=carrier.resize(im.size,Image.Resampling.NEAREST)
    dest=O/(name+('-down'if down else'-up')+f'-{scale}x');dest.mkdir(exist_ok=True);im.save(dest/'source.png');carrier.save(dest/'carrier.png');(dest/'gray.bin').write_bytes(im.tobytes());(dest/'carrier.bin').write_bytes(bytes(v>0 for v in carrier.tobytes()))
    truth=dict(name=dest.name,width=im.width,height=im.height,glyphGap=gap*scale,staffGap=16*scale,direction=1 if down else-1,stem=dict(left=(sx-1)*scale,right=(sx+1)*scale-1,cap=(cap+1)*scale-1 if down else cap*scale,start=(headY+.5)*scale-.5),head=dict(x=(sx-5+.5)*scale-.5,y=(headY+.5)*scale-.5),rivals=rivals,expectedFlagCount=expected,files={n:sha(dest/n)for n in['source.png','carrier.png','gray.bin','carrier.bin']},provenance='Original procedural filled cubic curves, rectangles and polylines. No score/font/model/gold input.');(dest/'truth.json').write_text(json.dumps(truth,indent=2)+'\n');rows.append(dict(name=dest.name,truthSHA256=sha(dest/'truth.json'),expectedFlagCount=expected))
    if not down and scale==1:thumbs.append((name,im))
 sheet=Image.new('RGB',(640,190*math.ceil(len(thumbs)/4)),'white');dd=ImageDraw.Draw(sheet)
 for i,(name,im)in enumerate(thumbs):x=(i%4)*160;y=(i//4)*190;sheet.paste(im,(x,y+25));dd.text((x+2,y+4),name[:24],fill='black')
 sheet.save(O/'original-contact-sheet.png');receipt=dict(status='ORIGINAL_FIXTURE_GENERATION_ONLY_DETECTOR_NOT_TESTED',license='Apache-2.0',fixtures=len(rows),bases=len(BASE),positiveFixtures=sum(r['expectedFlagCount']>0 for r in rows),negativeFixtures=sum(r['expectedFlagCount']==0 for r in rows),generatorSHA256=sha(Path(__file__)),rows=rows);(O/'fixture-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print({k:v for k,v in receipt.items()if k!='rows'})
if __name__=='__main__':main()


