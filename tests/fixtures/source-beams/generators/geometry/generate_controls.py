# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original procedural beam geometry controls; no score, font, or traced artwork.

This builds fixture inputs and drawing-command truth, NOT a detector acceptance.
Run with Pillow. Coordinates/identities are intentionally local to each fixture.
"""
from pathlib import Path
import hashlib, json
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent
BASE = [
    dict(name='plain_three_levels', runs=[(1,0,3),(2,0,3),(3,0,3)]),
    dict(name='sloping_staff_crossing', slope=.24, y=102, runs=[(1,0,3),(2,0,3)]),
    dict(name='opposite_slope_crossing', slope=-.24, y=138, runs=[(1,0,3),(2,0,3)]),
    dict(name='wide_observed_spacing', spacing=18, runs=[(1,0,3),(2,0,3),(3,0,3)]),
    dict(name='narrow_observed_spacing', spacing=10, thickness=4, runs=[(1,0,3),(2,0,3),(3,0,3)]),
    dict(name='primary_break_thin_staff_bridge', y=112, runs=[(1,0,1),(1,2,3)]),
    dict(name='two_pixel_break_thin_staff_bridge', y=112, cuts=[(1,111,112)], runs=[(1,0,3)]),
    dict(name='secondary_break_continuous_primary', runs=[(1,0,3),(2,0,1),(2,2,3)]),
    dict(name='tertiary_break_continuous_upper_levels', runs=[(1,0,3),(2,0,3),(3,0,1),(3,2,3)]),
    dict(name='secondary_forward_and_backward_hooks', runs=[(1,0,3)], hooks=[(2,0,'forward'),(2,3,'backward')]),
    dict(name='inner_hooks_never_full_connections', runs=[(1,0,3)], hooks=[(2,1,'forward'),(2,2,'backward')], hook_length=10),
    dict(name='beam_over_explicit_rest', runs=[(1,0,3),(2,0,1)], rest=True),
    dict(name='chord_owned_single_shaft', runs=[(1,0,3),(2,0,3)], chord=True),
    dict(name='long_shafts_shallow_quantized_slope', slope=.04, y=54, head_y=198, runs=[(1,0,3),(2,0,3)]),
]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write_json(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')

def build(spec, down, scale):
    # Every mirror uses the same raster reflection, preserving original topology.
    w,h=280,256
    img=Image.new('L',(w,h),255); d=ImageDraw.Draw(img)
    mask=Image.new('L',(w,h),0); md=ImageDraw.Draw(mask)
    xs=[40,88,136,184]; gap=16; y=spec.get('y',78)
    slope=spec.get('slope',.0); spacing=spec.get('spacing',14)
    thick=spec.get('thickness',6); head_y=spec.get('head_y',190)
    beam_y=lambda x,level: round(y+slope*(x-xs[0])+(level-1)*spacing)
    staff_bands=[dict(top=sy-1,bottom=sy) for sy in range(96,161,gap)]
    for band in staff_bands:d.rectangle((12,band['top'],w-13,band['bottom']),fill=0)
    columns=[]
    for i,x in enumerate(xs):
        hy=head_y+(i%2)*8
        d.rectangle((x-1,beam_y(x,1),x,hy),fill=0)
        d.ellipse((x-11,hy-4,x+1,hy+4),fill=0)
        heads=[dict(id=f'head-{i}',x=x-5,y=hy)]
        if spec.get('chord'):
            d.ellipse((x-11,hy-20,x+1,hy-12),fill=0)
            heads.append(dict(id=f'head-{i}-chord',x=x-5,y=hy-16))
        columns.append(dict(id=i,heads=heads,stem=dict(left=x-1,right=x,cap=beam_y(x,1))))
    # Draw vertical slices so quantized, constant-thickness ribbons are explicit.
    for level,a,b in spec['runs']:
        for x in range(xs[a]-1,xs[b]+1):
            if any(l==level and lo<=x<=hi for l,lo,hi in spec.get('cuts',[])):continue
            top=beam_y(x,level)
            d.line((x,top,x,top+thick-1),fill=0)
            md.line((x,top,x,top+thick-1),fill=255)
    hook_meta=[]
    for level,col,direction in spec.get('hooks',[]):
        sign=1 if direction=='forward' else -1
        end=xs[col]+sign*spec.get('hook_length',16)
        for x in range(min(end,xs[col]),max(end,xs[col])+1):
            top=beam_y(x,level)
            d.line((x,top,x,top+thick-1),fill=0)
            md.line((x,top,x,top+thick-1),fill=255)
        hook_meta.append(dict(column=col,level=level,direction=direction))
    if spec.get('rest'):
        # Original abstract rest mark inside a gap: not a source head/beam owner.
        d.line([(112,172),(118,179),(111,185),(118,192)],fill=0,width=2)
    edges=[]; rejected=[]
    for level in range(1,4):
        for i in range(3):
            drawn=any(l==level and a<=i and i+1<=b for l,a,b in spec['runs'])
            cut=any(l==level and xs[i]<hi and lo<xs[i+1] for l,lo,hi in spec.get('cuts',[]))
            (edges if drawn and not cut else rejected).append([i,i+1,level])
    # Verify fixture support independently from the topology list. A full beam
    # has a thick mask stripe at every interior x; cuts must contain a true gap.
    for a,b,l in edges:
        assert all(mask.getpixel((x,beam_y(x,l)+thick//2))==255 for x in range(xs[a]+2,xs[b]-1))
    for l,lo,hi in spec.get('cuts',[]):
        assert all(mask.getpixel((x,beam_y(x,l)+thick//2))==0 for x in range(lo,hi+1))
    if down:
        img=img.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        mask=mask.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        for column in columns:
            for head in column['heads']:head['y']=h-1-head['y']
            column['stem']['cap']=h-1-column['stem']['cap']
        staff_bands=[dict(top=h-1-band['bottom'],bottom=h-1-band['top']) for band in reversed(staff_bands)]
    img=img.resize((w*scale,h*scale),Image.Resampling.NEAREST)
    mask=mask.resize(img.size,Image.Resampling.NEAREST)
    for column in columns:
        for head in column['heads']:
            head['x']=(head['x']+.5)*scale-.5
            head['y']=(head['y']+.5)*scale-.5
        stem=column['stem'];stem['left']*=scale
        stem['right']=(stem['right']+1)*scale-1
        stem['cap']=(stem['cap']+1)*scale-1 if down else stem['cap']*scale
    for band in staff_bands:
        band['top']*=scale;band['bottom']=(band['bottom']+1)*scale-1
    name=spec['name']+('-down' if down else '-up')+f'-{scale}x'
    dest=OUT/name;dest.mkdir(exist_ok=True)
    img.save(dest/'source.png'); mask.save(dest/'drawn-beams-only.png')
    (dest/'gray.bin').write_bytes(img.tobytes())
    truth=dict(name=name,width=img.width,height=img.height,gap=gap*scale,
        direction=1 if down else -1,staffBands=staff_bands,columns=columns,expectedConnections=edges,
        forbiddenConnections=rejected,expectedHooks=hook_meta,
        restIsNotHead=bool(spec.get('rest')),chordColumnsShareShaft=bool(spec.get('chord')),
        definition=spec,files={n:sha(dest/n) for n in ['source.png','gray.bin','drawn-beams-only.png']})
    write_json(dest/'truth.json',truth)
    return dict(name=name,truthSHA256=sha(dest/'truth.json'),edges=len(edges),hooks=len(hook_meta)),img

def main():
    results=[]; thumbs=[]
    for spec in BASE:
        for down in [False,True]:
            for scale in [1,2]:
                result,img=build(spec,down,scale);results.append(result)
                if not down and scale==1:thumbs.append((spec['name'],img))
    sheet=Image.new('RGB',(1120,7*290),'white');draw=ImageDraw.Draw(sheet)
    for i,(name,img) in enumerate(thumbs):
        x=(i%4)*280;y=(i//4)*290
        sheet.paste(img,(x,y+28));draw.text((x+4,y+5),name[:36],fill='black')
    sheet=sheet.crop((0,0,1120,4*290));sheet.save(OUT/'contact-sheet.png')
    receipt=dict(status='ORIGINAL_PROCEDURAL_FIXTURE_CONSISTENCY_PASS',
        provenance='New drawing commands only. No commercial score/image/font/gold input read.',
        license='Apache-2.0',fixtures=len(results),baseCases=len(BASE),
        mirroredDirections=2,integerRasterScales=[1,2],
        expectedConnections=sum(r['edges'] for r in results),
        expectedHooks=sum(r['hooks'] for r in results),results=results,
        generatorSHA256=sha(Path(__file__)),detectorTested=False,productionSourceChanges=False)
    write_json(OUT/'fixture-receipt.json',receipt)
    print(json.dumps({k:v for k,v in receipt.items() if k!='results'}))

if __name__=='__main__':main()
