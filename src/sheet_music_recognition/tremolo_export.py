# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Written paired tremolos retain source ownership separately from performed pulses."""
import math
from .tremolo_wire import clock_policy, performance_pairs


def _anchor(value):
    if not isinstance(value, dict) or any(type(value.get(k)) is not int or value[k] < 0 for k in ('pageIndex', 'noteIndex')):
        raise ValueError('Invalid tremolo source anchor')
    return value['pageIndex'], value['noteIndex']


def retained(page):
    score = page.get('score', {})
    source = score.get('tremoloSource') or {}
    pairs = performance_pairs(source)
    if not pairs:
        return {}
    notes, events, origins = score.get('notes', []), page['events'], source.get('noteOrigins', [])
    if len(notes) != len(events) or len(origins) != len(notes):
        raise ValueError('Tremolo export requires complete source event ownership')
    lookup = {}
    for index, origin in enumerate(origins):
        if origin is None:
            continue
        anchor = _anchor(origin)
        if anchor in lookup:
            raise ValueError('Duplicate tremolo source identity')
        lookup[anchor] = index
    result, ids = {}, set()
    for pair in pairs:
        independent = clock_policy(pair) == 'INDEPENDENT_ENDPOINT_DURATIONS'
        identifier, strokes = pair.get('id'), pair.get('strokes')
        if not isinstance(identifier, str) or not identifier or identifier in ids or type(strokes) is not int or not 1 <= strokes <= 8:
            raise ValueError('Invalid tremolo pair identity or strokes')
        ids.add(identifier)
        sides = []
        for side in ('first', 'second'):
            anchors = pair.get(side, [])
            if not anchors:
                raise ValueError('Missing tremolo chord endpoint')
            members = []
            for anchor in anchors:
                owner = lookup.get(_anchor(anchor))
                if owner is None or owner in result:
                    raise ValueError('Missing or ambiguous tremolo endpoint')
                event, note = events[owner], notes[owner]
                if (type(event.get('sourceNoteIndex', owner)) is not int or event.get('sourceNoteIndex', owner) != owner
                        or any(event.get(k) != note.get(k) for k in ('measureIndex', 'staffIndex', 'staffCount'))
                        or note.get('kind', 'PITCHED') != 'PITCHED'
                        or event.get('kind', 'PITCHED') != 'PITCHED'
                        or event.get('tiedFromPrevious')
                        or note.get('tiedFromPrevious') or note.get('boundaryTies', 0)
                        or note.get('articulations', 0) & ((1 << 15) | (7 << 16))):
                    raise ValueError('Unproved tremolo source endpoint')
                start, duration = event.get('startBeat'), event.get('durationBeats')
                if any(type(v) not in (int, float) or not math.isfinite(v) or v < 0 for v in (start, duration)) or duration <= 0:
                    raise ValueError('Invalid tremolo event clock')
                beams, dots = note.get('beamCount', 0), note.get('augmentationDots', 0)
                actual, normal = note.get('tupletDivisor', 1), note.get('tupletNormalNotes', 1)
                if any(type(v) is not int for v in (beams, dots, actual, normal)) or not 0 <= beams <= 8 or not 0 <= dots <= 3 or min(actual, normal) < 1:
                    raise ValueError('Invalid tremolo printed duration')
                base = 2**-beams if beams else note.get('unbeamedDurationBeats', 0)
                if type(base) not in (int, float) or not math.isfinite(base) or base <= 0:
                    raise ValueError('Missing tremolo printed base duration')
                if event.get('tupletActualNotes', 1) != actual or event.get('tupletNormalNotes', 1) != normal:
                    raise ValueError('Tremolo event ratio disagrees with source')
                written = base * (2 - 2**-dots) * normal / actual
                if not math.isfinite(written) or abs(duration * (1 if independent else 2) - written) > 1e-7:
                    raise ValueError('Tremolo clock must be half its written type')
                result[owner] = dict(type='start' if side == 'first' else 'stop', strokes=strokes, pair=identifier)
                if independent:
                    if actual != 1 or normal != 1:
                        raise ValueError('Independent tremolo tuplet needs a proved outer tuplet group')
                    if beams != 0 or abs(base - 1) > 1e-7:
                        raise ValueError('Independent tremolo source appearance needs a supported quarter-head representation')
                    result[owner].update(printedFilled=True, printedBase=base, printedStem=note.get('stemDirection', 0), canonicalClockPolicy='INDEPENDENT_ENDPOINT_DURATIONS')

                members.append(owner)
            sides.append(members)
        a, b = (events[side[0]] for side in sides)
        lane = tuple(a.get(k) for k in ('measureIndex', 'staffIndex', 'staffCount'))
        if abs(a['startBeat'] + a['durationBeats'] - b['startBeat']) > 1e-7 or (not independent and abs(a['durationBeats'] - b['durationBeats']) > 1e-7):
            raise ValueError('Tremolo endpoints must share one adjacent duration span')
        for side, reference in zip(sides, (a, b)):
            for owner in side:
                event = events[owner]
                if (tuple(event.get(k) for k in ('measureIndex', 'staffIndex', 'staffCount')) != lane
                        or abs(event['startBeat'] - reference['startBeat']) > 1e-7
                        or abs(event['durationBeats'] - reference['durationBeats']) > 1e-7):
                    raise ValueError('Tremolo chord endpoints disagree')
    return result
