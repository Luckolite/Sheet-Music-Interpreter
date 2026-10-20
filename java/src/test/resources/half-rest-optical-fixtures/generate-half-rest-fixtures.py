# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Reproduce independently authored half-rest optical fixtures; no classifier calls."""
from pathlib import Path
import argparse, json, hashlib, math

def render_acquired(D):
    D.mkdir(parents=True,exist_ok=False)
    roles=[]
    for g in (14,16,20):
     for ph in (0,.5):roles.append({'name':f'plate_g{g}_phase{ph:g}','gap':g,'phase':ph,'kind':'positive','expectedDirect':1,'expectedFull':1})
    for k in ('stem','branch','lower','oval'):roles.append({'name':'adverse_'+k,'gap':16,'phase':0,'kind':k,'expectedDirect':0,'expectedFull':0})
    roles.append({'name':'interrupted_left_support_retains_ordinary_rest','gap':16,'phase':0,'kind':'supportgap','expectedDirect':0,'expectedFull':1})
    out=[];w=480;h=220;ss=4
    for r in roles:
     g=r['gap'];ph=r['phase'];top=60+ph;middle=top+2*g;L=180;RR=L+round(1.15*g);plateTop=middle-.5*g
     high=bytearray(w*h*16);active=set()
     def box(l,t,rr,b,value=1):
      x0=max(0,math.ceil(l*4-.5));x1=min(w*4,math.ceil(rr*4-.5));y0=max(0,math.ceil(t*4-.5));y1=min(h*4,math.ceil(b*4-.5))
      for yy in range(y0,y1):high[yy*w*4+x0:yy*w*4+x1]=bytes([value])*(x1-x0);active.add(yy//4)
     for k in range(5):box(20,top+k*g-1,460,top+k*g+1)
     if r['kind']=='supportgap':box(20,middle-1,L,middle+1,0)
     if r['kind']=='oval':
      for yy in range(math.floor(plateTop*4),math.ceil(middle*4)):
       for xx in range(L*4,RR*4):
        x=(xx+.5)/4;y=(yy+.5)/4
        if ((x-(L+RR)/2)/((RR-L)/2))**2+((y-(plateTop+middle)/2)/((middle-plateTop)/2))**2<1:high[yy*w*4+xx]=1;active.add(yy//4)
     else:box(L,plateTop,RR,middle)
     # Independent distant long beam affects global row masks; never intersects plate or its side probes.
     box(320,middle-.25*g,450,middle-1)
     if r['kind']=='stem':box(RR-3,plateTop-6,RR,middle+10)
     if r['kind']=='branch':box(RR-1,plateTop+2,RR+9,plateTop+5)
     if r['kind']=='lower':box(L+4,middle+3,L+8,middle+7)
     initial=[255]*(w*h)
     for y in active:
      for x in range(w):
       covered=sum(high[(y*4+dy)*w*4+x*4+dx]for dy in range(4)for dx in range(4));initial[y*w+x]=(255*(16-covered)+8)//16
     hor=[0]*(w*h)
     for y in range(h):
      for x in range(w):hor[y*w+x]=sum(k*(initial[y*w+x+d]if 0<=x+d<w else 255)for d,k in [(-1,1),(0,2),(1,1)])
     a=bytearray(w*h);kernel=[1,6,15,20,15,6,1]
     for y in range(h):
      for x in range(w):a[y*w+x]=(sum(k*(hor[(y+d)*w+x]if 0<=y+d<h else 255*4)for d,k in zip(range(-3,4),kernel))+128)//256
     f=D/(r['name']+'.gray');f.write_bytes(a)
     # Descriptive original-narrow projection acquisition, not recognition or fixture oracle change.
     bandTop=round(top+g*.25);bandBottom=round(top+4*g+g*.3);mask=[];counts=[0]*w
     for y in range(bandTop,bandBottom+1):
      raw=[x for x in range(w)if a[y*w+x]<170];longest=run=0
      for x in range(w):run=run+1 if a[y*w+x]<170 else 0;longest=max(longest,run)
      # Inputs here are .0/.5 positive, nearest-frame row ties use Java floor(v+.5).
      nearest=top+math.floor((y-top)/g+.5)*g;ru=abs(y-nearest)<=g*.2 and(len(raw)>w*.25 or longest>=g*5);mask.append(ru)
      if not ru:
       for x in raw:counts[x]+=1
     runs=[];start=None
     for x in range(w+1):
      if x<w and counts[x]>=2:
       if start is None:start=x
      elif start is not None:runs.append([start,x-1]);start=None
     selected=[p for p in runs if p[0]<=(L+RR)*.5<=p[1]]
     bounds=selected[0]if len(selected)==1 else None
     preconditions=bool(bounds and g*.7<=bounds[1]-bounds[0]+1<=g*1.6)
     assert bounds is not None,(r['name'],bounds)
     if r['expectedDirect']==1:assert preconditions,(r['name'],bounds)
     out.append({**r,'w':w,'h':h,'staff':[top,top+4*g,g,0,1],'physicalPlate':[L,plateTop,RR,middle],'rawProposal':bounds,'proposalWidth':bounds[1]-bounds[0]+1,'preconditions':preconditions,'negativeWitnessScope':'IN_DOMAIN' if preconditions else 'WIDTH_LIMIT_ONLY','sha256':hashlib.sha256(a).hexdigest()})
    (D/'geometry-manifest.json').write_text(json.dumps({'format':'unsigned 8-bit grayscale, row-major','width':480,'height':220,'roles':out},indent=2)+'\n',encoding='utf-8')

def render_early(D):
    D.mkdir(parents=True,exist_ok=False)
    roles=[{'name':f'early_remote_g{g}_phase{ph:g}','gap':g,'phase':ph,'kind':'positive','expectedDirect':1,'expectedFull':1}for g,ph in [(14,0),(16,0),(20,0),(16,.5)]]
    for k in ('stem','branch','lower','oval'):roles.append({'name':'early_adverse_'+k,'gap':16,'phase':0,'kind':k,'expectedDirect':0,'expectedFull':0})
    out=[];w=480;h=220;ss=4
    for r in roles:
     g=r['gap'];ph=r['phase'];top=60+ph;middle=top+2*g;L=180;RR=L+round(1.15*g);plateTop=middle-.5*g
     high=bytearray(w*h*16);active=set()
     def box(l,t,rr,b,value=1):
      x0=max(0,math.ceil(l*4-.5));x1=min(w*4,math.ceil(rr*4-.5));y0=max(0,math.ceil(t*4-.5));y1=min(h*4,math.ceil(b*4-.5))
      for yy in range(y0,y1):high[yy*w*4+x0:yy*w*4+x1]=bytes([value])*(x1-x0);active.add(yy//4)
     for k in range(5):box(20,top+k*g-1,460,top+k*g+1)
     if r['kind']=='supportgap':box(20,middle-1,L,middle+1,0)
     if r['kind']=='oval':
      for yy in range(math.floor(plateTop*4),math.ceil(middle*4)):
       for xx in range(L*4,RR*4):
        x=(xx+.5)/4;y=(yy+.5)/4
        if ((x-(L+RR)/2)/((RR-L)/2))**2+((y-(plateTop+middle)/2)/((middle-plateTop)/2))**2<1:high[yy*w*4+xx]=1;active.add(yy//4)
     else:box(L,plateTop,RR,middle)
     # Independent distant long beam affects global row masks; never intersects plate or its side probes.
     box(320,middle-.5*g,450,middle-2)
     if r['kind']=='stem':box(RR-3,plateTop-6,RR,middle+10)
     if r['kind']=='branch':box(RR-1,plateTop+2,RR+9,plateTop+5)
     if r['kind']=='lower':box(L+4,middle+3,L+8,middle+7)
     initial=[255]*(w*h)
     for y in active:
      for x in range(w):
       covered=sum(high[(y*4+dy)*w*4+x*4+dx]for dy in range(4)for dx in range(4));initial[y*w+x]=(255*(16-covered)+8)//16
     hor=[0]*(w*h)
     for y in range(h):
      for x in range(w):hor[y*w+x]=sum(k*(initial[y*w+x+d]if 0<=x+d<w else 255)for d,k in [(-1,1),(0,2),(1,1)])
     a=bytearray(w*h);kernel=[1,6,15,20,15,6,1]
     for y in range(h):
      for x in range(w):a[y*w+x]=(sum(k*(hor[(y+d)*w+x]if 0<=y+d<h else 255*4)for d,k in zip(range(-3,4),kernel))+128)//256
     f=D/(r['name']+'.gray');f.write_bytes(a)
     # Descriptive original-narrow projection acquisition, not recognition or fixture oracle change.
     bandTop=round(top+g*.25);bandBottom=round(top+4*g+g*.3);mask=[];counts=[0]*w
     for y in range(bandTop,bandBottom+1):
      raw=[x for x in range(w)if a[y*w+x]<170];longest=run=0
      for x in range(w):run=run+1 if a[y*w+x]<170 else 0;longest=max(longest,run)
      # Inputs here are .0/.5 positive, nearest-frame row ties use Java floor(v+.5).
      nearest=top+math.floor((y-top)/g+.5)*g;ru=abs(y-nearest)<=g*.2 and(len(raw)>w*.25 or longest>=g*5);mask.append(ru)
      if not ru:
       for x in raw:counts[x]+=1
     runs=[];start=None
     for x in range(w+1):
      if x<w and counts[x]>=2:
       if start is None:start=x
      elif start is not None:runs.append([start,x-1]);start=None
     selected=[p for p in runs if p[0]<=(L+RR)*.5<=p[1]]
     bounds=selected[0]if len(selected)==1 else None
     preconditions=bool(bounds and g*.7<=bounds[1]-bounds[0]+1<=g*1.6)
     assert bounds is not None,(r['name'],bounds)
     if r['expectedDirect']==1:assert preconditions,(r['name'],bounds)
     out.append({**r,'remotePhysicalBox':[320,middle-.5*g,450,middle-2],'rowAudit':[{'y':yy,'darkCount':sum(v<170 for v in a[yy*w:(yy+1)*w]),'localCentreTone':a[yy*w+int((L+RR)*.5)]}for yy in range(math.floor(middle)-3,math.ceil(middle)+3)],'w':w,'h':h,'staff':[top,top+4*g,g,0,1],'physicalPlate':[L,plateTop,RR,middle],'rawProposal':bounds,'proposalWidth':bounds[1]-bounds[0]+1,'preconditions':preconditions,'negativeWitnessScope':'IN_DOMAIN' if preconditions else 'WIDTH_LIMIT_ONLY','sha256':hashlib.sha256(a).hexdigest()})
    (D/'geometry-manifest.json').write_text(json.dumps({'format':'unsigned 8-bit grayscale, row-major','width':480,'height':220,'roles':out},indent=2)+'\n',encoding='utf-8')

if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);a=p.parse_args()
    a.output.mkdir(parents=True,exist_ok=False)
    render_acquired(a.output/'half-rest-acquired-blur')
    render_early(a.output/'half-rest-early-remote-blur')
