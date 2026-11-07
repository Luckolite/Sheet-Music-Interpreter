# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Exact source-column ownership for optically proved instrument/Play cue captions."""
import math
import struct
SOURCE = 'printed-cue-instruction'
INSTRUMENTS = {'guitar', 'piano', 'violin', 'viola', 'cello', 'flute', 'oboe', 'clarinet',
               'bassoon', 'trumpet', 'trombone', 'horn', 'bass', 'harp', 'organ', 'saxophone'}

def owns(mark):
    evidence = mark.get('evidence', [])
    if (mark.get('kind') != 'UNRESOLVED_DIRECTION' or mark.get('qualifierText', '')
            or len(evidence) != 1 or not (mark.get('targetEventId') or '').startswith('expression-column:')):
        return False
    row = evidence[0]
    text = row.get('printedText', '')
    token = text.strip().lower().rstrip('.:') if type(text) is str else ''
    return (row.get('sourceId') == SOURCE and token in INSTRUMENTS | {'play'}
            and all(row.get(k) == mark.get(k) for k in ('staffIndex', 'staffCount')))

def members(mark, page):
    if not owns(mark):
        return []
    try:
        m, staff, count, bits = map(int, mark['targetEventId'][len('expression-column:'):].split(':'))
        pos = struct.unpack('>f', struct.pack('>I', bits & 0xffffffff))[0]
    except (ValueError, struct.error):
        return []
    if (m < 0 or not 0 <= staff < count <= 8 or not math.isfinite(pos) or not 0 <= pos <= 1
            or staff != mark.get('staffIndex') or count != mark.get('staffCount')
            or any(not -2147483648 <= v <= 2147483647 for v in (m, staff, count, bits))):
        return []
    notes = page.get('score', {}).get('notes', [])
    events = page.get('events', [])
    if len(notes) != len(events):
        return []
    result = []
    for i, note in enumerate(notes):
        p = note.get('positionInMeasure')
        if (type(p) not in (int, float) or not math.isfinite(p) or not 0 <= p <= 1
                or note.get('measureIndex') != m or note.get('staffIndex') != staff):
            continue
        if struct.unpack('>f', struct.pack('>f', p))[0] != pos:
            continue
        event = events[i]
        if (note.get('staffCount') != count or note.get('kind', 'PITCHED') != 'PITCHED'
                or note.get('articulations', 0) & 32768
                or type(event.get('sourceNoteIndex', i)) is not int
                or event.get('sourceNoteIndex', i) != i
                or any(type(note.get(k)) is not int or event.get(k) != note[k]
                       for k in ('measureIndex', 'staffIndex', 'staffCount'))):
            return []
        result.append(i)
    return result
