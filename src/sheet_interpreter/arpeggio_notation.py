# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Complete source-owned arpeggio columns; notation only, without performed spread."""
import math
import re
import struct

PREFIX = 'arpeggio-column-v1:'
SOURCE = 'printed-arpeggio-wave'


def _int(text):
    if not re.fullmatch(r'[+-]?[0-9]+', text):
        raise ValueError('Not a Java integer')
    value = int(text)
    if not -2147483648 <= value <= 2147483647:
        raise ValueError('Integer outside Java range')
    return value


def _float32(value):
    return struct.unpack('>f', struct.pack('>f', value))[0]


def target_indices(expression, page):
    """Mirror ScoreArpeggioNotation.targetIndices, never a nearest-head heuristic."""
    if (expression.get('kind') != 'ARPEGGIO'
            or type(expression.get('staffIndex')) is not int
            or type(expression.get('staffCount')) is not int):
        return []
    target = expression.get('targetEventId') or ''
    if not isinstance(target, str) or not target.startswith(PREFIX):
        return []
    try:
        fields = target[len(PREFIX):].split(':')
        if len(fields) != 5:
            return []
        measure, staff, count = map(_int, fields[:3])
        # The producer serializes Float.toHexString, not expression-column float bits.
        if not re.fullmatch(r'[+-]?0[xX][0-9a-fA-F]+(?:\.[0-9a-fA-F]*)?[pP][+-]?[0-9]+', fields[3]):
            return []
        position = _float32(float.fromhex(fields[3]))
        measures = page.get('score', {}).get('measures', [])
        if (measure < 0 or measure >= len(measures) or not math.isfinite(position)
                or not 0 <= position <= 1 or staff != expression.get('staffIndex')
                or count != expression.get('staffCount')):
            return []
        steps = [_int(text) for text in fields[4].split(',')]
        if len(steps) < 2 or len(set(steps)) != len(steps):
            return []
        if not any(e.get('sourceId') == SOURCE and e.get('staffIndex') == staff
                   and e.get('staffCount') == count for e in expression.get('evidence', [])):
            return []
        found, result = set(), []
        for index, note in enumerate(page.get('score', {}).get('notes', [])):
            if (note.get('kind', 'PITCHED') == 'PITCHED' and not note.get('articulations', 0) & 32768
                    and note.get('measureIndex') == measure and note.get('staffIndex') == staff
                    and note.get('staffCount') == count
                    and abs(_float32(_float32(note['positionInMeasure']) - position)) <= _float32(.018)):
                step = note['staffStep']
                if type(step) is not int or step in found:
                    return []
                found.add(step)
                result.append(index)
        return result if found == set(steps) else []
    except (ValueError, TypeError, KeyError, OverflowError, struct.error):
        return []


def _computed_offset(offset, length, begin):
    """Mirror ScoreAnchor.computedOffset for decoder-computed musical time only."""
    if not math.isfinite(offset) or offset < 0:
        return offset
    rounding = 4 * math.ulp(max(length, abs(begin + length)))
    return length if offset > length and offset - length <= rounding else offset


def plan(expression, page, available=None):
    """Return all heads with complete available, nonfallback decoder-proven written time."""
    indices = target_indices(expression, page)
    notes, events = page.get('score', {}).get('notes', []), page.get('events', [])
    if not indices or len(notes) != len(events):
        return []
    allowed = set(range(len(events))) if available is None else set(available)
    if not allowed.issuperset(indices):
        return []
    measure = notes[indices[0]]['measureIndex']
    beats = page.get('measureBeats', [])
    if (not isinstance(beats, (list, tuple)) or measure >= len(beats)
            or any(type(b) not in (int, float) or not math.isfinite(b) or b <= 0
                   for b in beats[:measure+1])):
        return []
    begin, length = sum(beats[:measure]), beats[measure]
    onset = None
    for index in indices:
        event, note = events[index], notes[index]
        start, duration = event.get('startBeat'), event.get('durationBeats')
        if (event.get('kind', 'PITCHED') != 'PITCHED' or event.get('durationFallback', False)
                or event.get('grace', False)
                or type(event.get('sourceNoteIndex', index)) is not int
                or event.get('sourceNoteIndex', index) != index
                or event.get('measureIndex') != note['measureIndex']
                or event.get('staffIndex') != note['staffIndex']
                or event.get('staffCount') != note['staffCount']
                or any(type(v) not in (int, float) or not math.isfinite(v) for v in (start, duration))
                or duration <= 0 or start < begin
                or _computed_offset(start + duration - begin, length, begin) > length
                or onset is not None and abs(start-onset) > 1e-7):
            return []
        onset = start
    return indices
