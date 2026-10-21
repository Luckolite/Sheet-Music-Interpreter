# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Attack-local bow directions retained on the existing techniqueChanges records."""
import xml.etree.ElementTree as ET
UP_BOW, DOWN_BOW = 6, 7

def retained(page, index):
    notes=page.get('score', {}).get('notes', [])
    events=page.get('events', [])
    if len(notes)!=len(events) or not 0<=index<len(notes): return None
    event, note=events[index], notes[index]
    if (type(event.get('sourceNoteIndex', index)) is not int or event.get('sourceNoteIndex', index)!=index or any(event.get(k)!=note.get(k)
            for k in ('measureIndex','staffIndex','staffCount'))): return None
    value=None
    for row in page.get('score', {}).get('techniqueChanges', []):
        kind=row.get('technique')
        if type(kind) is not int or kind not in (UP_BOW, DOWN_BOW): continue
        if any(row.get(k)!=note.get(k) for k in ('measureIndex','positionInMeasure','staffIndex','staffCount')):continue
        if value is not None and value!=kind:return None
        value=kind
    return value

def chord_marks(chord, start):
    seen=set(); result=[]
    for row in chord:
        value=row.get('retainedBow') if start==row['start'] else None
        result.append(None if value in seen else value)
        if value is not None:seen.add(value)
    return result

def write(notation, value):
    if value not in (UP_BOW, DOWN_BOW):raise ValueError('Invalid retained bow direction')
    technical=notation.find('technical')
    if technical is None:technical=ET.SubElement(notation,'technical')
    ET.SubElement(technical,'up-bow' if value==UP_BOW else 'down-bow')
