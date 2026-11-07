# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Exact source-note ownership for printed fall-off notation."""
import math
import struct

PREFIX = 'fall-off-note-v1:'
SOURCE = 'printed-fall-off'
CURVED = 'falloff-v1:curved'


def target(note):
    """Canonical raw note identity; never a nearest note or chord-wide attachment."""
    try:
        m, staff, count, step = [note[k] for k in
                                ('measureIndex', 'staffIndex', 'staffCount', 'staffStep')]
        x, y = note['positionInMeasure'], note['pageY']
        if (any(type(v) is not int for v in (m, staff, count, step)) or m < 0
                or not 0 <= staff < count or note.get('kind', 'PITCHED') != 'PITCHED'
                or any(type(v) not in (int, float) or not math.isfinite(v) or not 0 <= v <= 1
                       for v in (x, y))):
            return None
        bits = lambda v: struct.pack('>f', v).hex()
        return f'{PREFIX}{m}:{staff}:{count}:{bits(x)}:{step}:{bits(y)}'
    except (KeyError, TypeError, ValueError, OverflowError, struct.error):
        return None


def retained(page):
    notes, events = page.get('score', {}).get('notes', []), page.get('events', [])
    if len(notes) != len(events):
        return frozenset()
    owners = {}
    for i, note in enumerate(notes):
        key = target(note)
        if key is not None:
            owners[key] = i if key not in owners else None
    result = set()
    marks = page.get('score', {}).get('expressiveEvents', [])
    owner_counts = {}
    for mark in marks:
        i = owner_index(mark, notes)
        if i is not None:
            owner_counts[i] = owner_counts.get(i, 0) + 1
    for mark in marks:
        if (mark.get('qualifierText') != CURVED or mark.get('strength') != 'UNSPECIFIED'
            or mark.get('kind') != 'FALL_OFF' or mark.get('scope') not in ('NOTE', 'UNRESOLVED')
                or mark.get('end') is not None or not isinstance(mark.get('targetEventId'), str)):
            continue
        i = owners.get(mark['targetEventId'])
        if i is None or owner_counts.get(i) != 1:
            continue
        note, event, anchor = notes[i], events[i], mark.get('start')
        if (any(mark.get(k) != note[k] for k in ('staffIndex', 'staffCount'))
                or event.get('kind', 'PITCHED') != 'PITCHED'
                or type(event.get('sourceNoteIndex')) is not int or event['sourceNoteIndex'] != i
                or any(event.get(k) != note[k] for k in ('measureIndex', 'staffIndex', 'staffCount'))
                or event.get('durationFallback', False)
                or (mark.get('scope') == 'NOTE' and (not isinstance(anchor, dict)
                    or type(anchor.get('measureIndex')) is not int
                    or anchor['measureIndex'] != note['measureIndex']))
                or (mark.get('scope') == 'UNRESOLVED' and anchor is not None)
                or not any(e.get('sourceId') == SOURCE
                           and all(e.get(k) == note[k] for k in ('staffIndex', 'staffCount'))
                           for e in mark.get('evidence', []))):
            continue
        beats, m = page.get('measureBeats', []), note['measureIndex']
        start, duration = event.get('startBeat'), event.get('durationBeats')
        if (m >= len(beats) or any(type(v) not in (int, float) or not math.isfinite(v) or v <= 0 for v in beats[:m+1])
                or any(type(v) not in (int, float) or not math.isfinite(v) for v in (start, duration))
                or duration <= 0):
            continue
        begin = sum(beats[:m])
        if not begin <= start < begin + beats[m]:
            continue
        if mark.get('scope') == 'UNRESOLVED':
            result.add(i)
            continue
        offset = anchor['quarterBeatOffset']
        if not 0 <= offset < beats[m]:
            continue
        # The mark must name the exact final-attack tick used by the Java resolver.
        if round(start * 10080) != round((begin + offset) * 10080):
            continue
        result.add(i)
    return frozenset(result)

def owner_index(mark, notes):
    """Mirror Java physical-owner validation before final-clock validation."""
    if (mark.get('qualifierText') != CURVED or mark.get('strength') != 'UNSPECIFIED'
            or mark.get('kind') != 'FALL_OFF'
            or mark.get('scope') not in ('NOTE', 'UNRESOLVED')
            or mark.get('end') is not None
            or not isinstance(mark.get('targetEventId'), str)):
        return None
    anchor = mark.get('start')
    if mark['scope'] == 'NOTE':
        if (not isinstance(anchor, dict) or type(anchor.get('measureIndex')) is not int
                or type(anchor.get('quarterBeatOffset')) not in (int, float)):
            return None
        try:
            if not math.isfinite(anchor['quarterBeatOffset']):
                return None
        except (OverflowError, TypeError, ValueError):
            return None
    elif anchor is not None:
        return None
    evidence = mark.get('evidence', [])
    if not isinstance(evidence, list) or not any(
            isinstance(e, dict) and e.get('sourceId') == SOURCE
            and e.get('staffIndex') == mark.get('staffIndex')
            and e.get('staffCount') == mark.get('staffCount') for e in evidence):
        return None
    matches = [i for i, note in enumerate(notes) if target(note) == mark['targetEventId']]
    if len(matches) != 1:
        return None
    i = matches[0]
    note = notes[i]
    if (note.get('staffIndex') != mark.get('staffIndex')
            or note.get('staffCount') != mark.get('staffCount')
            or (anchor is not None and anchor['measureIndex'] != note.get('measureIndex'))):
        return None
    return i





