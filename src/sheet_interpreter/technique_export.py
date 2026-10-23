# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Bind persistent instruction text to its first explicitly owned exported note column."""
import math
import struct

LABELS=('arco','pizz.','cantabile','sostenuto','marcato','ord.')

def position(value):
    if type(value) not in (int,float) or not math.isfinite(value) or not 0<=value<=1:
        return None
    return struct.unpack('>f',struct.pack('>f',value))[0]

def plan(page,rendered,bars,page_identity):
    notes=page.get('score',{}).get('notes',[])
    events=page.get('events',[])
    if len(notes)!=len(events) or len(rendered)!=len(notes):return []
    result=[];seen=set()
    for index,change in enumerate(page.get('score',{}).get('techniqueChanges',[])):
        kind=change.get('technique');m=change.get('measureIndex');staff=change.get('staffIndex');count=change.get('staffCount')
        p=position(change.get('positionInMeasure'))
        if (type(kind) is not int or not 0<=kind<len(LABELS) or type(m)is not int or not 0<=m<len(bars)
                or type(staff)is not int or type(count)is not int or not 0<=staff<count<=8 or p is None):continue
        first=None;owners=[];affected=[];malformed=False
        for i,note in enumerate(notes):
            if note.get('measureIndex')!=m or note.get('staffIndex')!=staff:continue
            q=position(note.get('positionInMeasure'))
            if q is None or any(type(note.get(k))is not int for k in ('measureIndex','staffIndex','staffCount')):
                malformed=True;break
            # Match the Java float activation law. This tolerance selects a column, never time.
            following=struct.unpack('>f',struct.pack('>f',q+struct.unpack('>f',struct.pack('>f',.001))[0]))[0]
            if p>following:continue
            affected.append(i)
            if first is None or q<first:first=q;owners=[i]
            elif q==first:owners.append(i)
        if malformed or not owners:continue
        start=None;earliest=None;valid=True
        for i in affected:
            source,event,row=notes[i],events[i],rendered[i]
            if (source.get('staffCount')!=count or type(event.get('sourceNoteIndex'))is not int
                    or event['sourceNoteIndex']!=i or event.get('durationFallback')is not False
                    or any(type(event.get(k))is not int or event[k]!=source.get(k)
                           for k in ('measureIndex','staffIndex','staffCount'))):
                valid=False;break
            at=row['start']
            if (type(at)is not int or not bars[m]['start']<=at<=bars[m]['start']+bars[m]['length']
                    or at==bars[m]['start']+bars[m]['length']and not row.get('grace',False)):
                valid=False;break
            earliest=at if earliest is None else min(earliest,at)
            if i in owners:
                if start is not None and start!=at:valid=False;break
                start=at
        if not valid or start!=earliest:continue
        key=(m,staff,count,kind,start)
        if key in seen:continue
        seen.add(key)
        result.append(dict(identity=f'{page_identity}/playing-instruction:{index}',
            kind='UNRESOLVED_DIRECTION',scope='PART',staffIndex=staff,staffCount=count,
            startTick=start,endTick=None,ownerIds=[rendered[i]['sourceIdentity']for i in owners],
            evidence=[dict(sourceId='retained-playing-instruction',printedText=LABELS[kind])],
            _playingInstruction=True, _playingInstructionBarStart=bars[m]['start']))
    return result
