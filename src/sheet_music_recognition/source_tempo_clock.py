# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Numeric tempo clocks from an existing, uniquely owned inline source caption."""
import math
import struct
from .technique_export import position


def _f32(value):
    return struct.unpack('>f', struct.pack('>f', value))[0]


def _visual(region, p):
    left, right = position(region.get('left')), position(region.get('right'))
    if left is None or right is None or right <= left:
        return None
    return _f32(left + _f32(p * _f32(right-left)))


def onset(page, tempo):
    from .performance import _column_members
    score, events = page.get('score', {}), page.get('events', [])
    if tempo not in score.get('tempoChanges', []):
        return None
    notes, regions = score.get('notes', []), score.get('measures', [])
    m, p = tempo.get('measureIndex'), position(tempo.get('positionInMeasure'))
    beats = page.get('measureBeats', [])
    if (type(m) is not int or not 0 <= m < min(len(beats), len(regions)) or p is None
            or len(notes) != len(events) or type(beats[m]) not in (int, float)
            or not math.isfinite(beats[m]) or beats[m] <= 0):
        return None
    x = _visual(regions[m], p)
    if x is None:
        return None
    bits = struct.unpack('>I', struct.pack('>f', x))[0]
    tolerance = (struct.unpack('>f', struct.pack('>I', bits+1))[0]-x)*4
    marks = [c for c in score.get('tempoChanges', []) if c.get('measureIndex') == m
             and (q := position(c.get('positionInMeasure'))) is not None
             and abs(_visual(regions[m], q)-x) <= tolerance]
    if len(marks) != 1:
        return None
    result, bar_start = None, sum(beats[:m])
    for expression in score.get('expressiveEvents', []):
        if (expression.get('kind') != 'UNRESOLVED_DIRECTION'
                or expression.get('scope') not in ('SCORE', 'UNRESOLVED')):
            continue
        words = [e for e in expression.get('evidence', []) if e.get('sourceId') == 'printed-expression-word']
        if len(words) != 1:
            continue
        word = words[0]; visual = position(word.get('visualX'))
        if (word.get('staffIndex') != expression.get('staffIndex')
                or word.get('staffCount') != expression.get('staffCount')
                or visual is None or abs(visual-x) > tolerance or not word.get('printedText', '').strip()):
            continue
        owners = _column_members(expression, page)
        if not owners:
            continue
        at = None
        for i in owners:
            note, event = notes[i], events[i]
            start, duration = event.get('startBeat'), event.get('durationBeats')
            if (type(event.get('sourceNoteIndex')) is not int or event['sourceNoteIndex'] != i
                    or event.get('durationFallback') is not False or note.get('measureIndex') != m
                    or any(type(event.get(k)) is not int or event[k] != note.get(k)
                           for k in ('measureIndex', 'staffIndex', 'staffCount'))
                    or note.get('staffIndex') != expression.get('staffIndex')
                    or note.get('staffCount') != expression.get('staffCount')
                    or note.get('articulations', 0) & 32768
                    or type(start) not in (int, float) or type(duration) not in (int, float)
                    or not math.isfinite(start) or not math.isfinite(duration) or duration <= 0
                    or not bar_start <= start < bar_start + beats[m]
                    or at is not None and at != start-bar_start):
                return None
            at = start-bar_start
        anchor = expression.get('start')
        eligibility = at if anchor is None else anchor.get('quarterBeatOffset')
        if (anchor is not None and anchor.get('measureIndex') != m
                or type(eligibility) not in (int, float) or not math.isfinite(eligibility)
                or abs(eligibility-beats[m]*p) > 1/32):
            continue
        if result is not None:
            return None
        result = at
    return result


def rhythmic_changes(page):
    result = []
    for change in page.get('score', {}).get('tempoChanges', []):
        at = onset(page, change)
        result.append(change if at is None else dict(change,
            positionInMeasure=_f32(at/page['measureBeats'][change['measureIndex']])))
    return result
