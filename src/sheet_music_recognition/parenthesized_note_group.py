# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""One print-only pair with exact, ordered physical note members."""
import hashlib
import math
import re
import struct
import uuid
from collections import Counter
from typing import NamedTuple

SOURCE = 'printed-parenthesized-note-group'
TARGET = 'parenthesis-group-v1:'
PAIR = 'parenthesis-pair-v1:'
GRACE = 1 << 15
f32 = lambda v: struct.unpack('>f', struct.pack('>f', v))[0]


def bits(value):
    return struct.pack('>f', value).hex()


def unbits(value):
    if not isinstance(value, str) or re.fullmatch('[0-9a-f]{8}', value) is None:
        raise ValueError('Noncanonical float bits')
    return struct.unpack('>f', bytes.fromhex(value))[0]


def integer(value):
    if type(value) is not int or not -2147483648 <= value <= 2147483647:
        raise ValueError('Invalid member integer')
    return value


class Member(NamedTuple):
    measure: int
    staff: int
    count: int
    x_bits: str
    step: int
    y_bits: str

    def token(self):
        return f'{self.measure},{self.staff},{self.count},{self.x_bits},{self.step},{self.y_bits}'


class Pair(NamedTuple):
    members: tuple
    geometry: tuple

    @property
    def target(self):
        return TARGET + self.members[0].token()

    @property
    def qualifier(self):
        return PAIR + ':'.join(map(bits, self.geometry)) + ':' + '/'.join(m.token() for m in self.members)


def member(token):
    p = token.split(',')
    if len(p) != 6:
        raise ValueError('Invalid member')
    m, staff, count, step = [integer(int(p[i])) for i in (0, 1, 2, 4)]
    x, y = unbits(p[3]), unbits(p[5])
    if m < 0 or not 0 <= staff < count or not all(math.isfinite(v) and 0 <= v <= 1 for v in (x, y)):
        raise ValueError('Invalid physical member')
    result = Member(m, staff, count, p[3], step, p[5])
    if result.token() != token:
        raise ValueError('Noncanonical member')
    return result


def member_of(note):
    try:
        if note.get('kind', 'PITCHED') != 'PITCHED' or integer(note.get('articulations', 0)) & GRACE:
            return None
        m, staff, count, step = [integer(note[k]) for k in ('measureIndex', 'staffIndex', 'staffCount', 'staffStep')]
        x, y = note['positionInMeasure'], note['pageY']
        if any(type(v) not in (int, float) or not math.isfinite(v) for v in (x, y)):
            return None
        return member(f'{m},{staff},{count},{bits(x)},{step},{bits(y)}')
    except (KeyError, TypeError, ValueError, OverflowError, struct.error):
        return None


def decode(qualifier):
    try:
        if not isinstance(qualifier, str) or len(qualifier) > 4096 or not qualifier.startswith(PAIR):
            return None
        p = qualifier[len(PAIR):].split(':')
        if len(p) != 7:
            return None
        geometry = tuple(unbits(v) for v in p[:6])
        lx, ly, rx, ry, lh, rh = geometry
        if (not all(math.isfinite(v) for v in geometry) or not -2 <= lx <= -f32(.45)
                or not f32(.45) <= rx <= 2 or abs(ly) > f32(.65) or abs(ry) > f32(.65)
                or not f32(1.1) <= lh <= f32(2.6) or not f32(1.1) <= rh <= f32(2.6)):
            return None
        tokens = p[6].split('/')
        if not 2 <= len(tokens) <= 64:
            return None
        members = tuple(member(v) for v in tokens)
        first, previous = members[0], -1
        for m in members:
            x = unbits(m.x_bits)
            if m[:3] != first[:3] or x <= previous:
                return None
            previous = x
        result = Pair(members, geometry)
        return result if result.qualifier == qualifier else None
    except (ValueError, TypeError, OverflowError, struct.error):
        return None


def owners(mark, notes):
    if (mark.get('kind') != 'PARENTHESIZED_NOTE_GROUP' or mark.get('scope') not in ('NOTE', 'UNRESOLVED')
            or mark.get('end') is not None or mark.get('strength') != 'UNSPECIFIED'):
        return None
    pair = decode(mark.get('qualifierText'))
    if pair is None or mark.get('targetEventId') != pair.target:
        return None
    first = pair.members[0]
    if (type(mark.get('staffIndex')) is not int or type(mark.get('staffCount')) is not int
            or (mark['staffIndex'], mark['staffCount']) != (first.staff, first.count)):
        return None
    start = mark.get('start')
    if mark['scope'] == 'UNRESOLVED':
        if start is not None:
            return None
    elif (not isinstance(start, dict) or type(start.get('measureIndex')) is not int
          or start['measureIndex'] != first.measure or type(start.get('quarterBeatOffset')) not in (int, float)
          or not math.isfinite(start['quarterBeatOffset']) or start['quarterBeatOffset'] < 0):
        return None
    evidence = mark.get('evidence')
    if not isinstance(evidence, list) or not any(isinstance(e, dict) and e.get('sourceId') == SOURCE
            and (e.get('staffIndex'), e.get('staffCount')) == (first.staff, first.count) for e in evidence):
        return None
    identities = [member_of(n) for n in notes]
    found = []
    for wanted in pair.members:
        matches = [i for i, m in enumerate(identities) if m == wanted]
        if len(matches) != 1:
            return None
        found.append(matches[0])
    left, right = unbits(first.x_bits), unbits(pair.members[-1].x_bits)
    for i, note in enumerate(notes):
        position = note.get('positionInMeasure')
        if ((note.get('measureIndex'), note.get('staffIndex'), note.get('staffCount')) == first[:3]
                and type(position) in (int, float) and math.isfinite(position) and 0 <= position <= 1
                and left <= f32(position) <= right and identities[i] not in pair.members):
            return None
    return tuple(found)


def group_id(mark, pair):
    digest = hashlib.md5((mark['eventId'] + '|' + pair.target).encode('utf-8')).digest()
    return 'pg-' + str(uuid.UUID(bytes=digest, version=3))


def retained(page):
    """Validate every member against the final adapter clock before any endpoint escapes."""
    score, events, beats = page.get('score', {}), page.get('events', []), page.get('measureBeats', [])
    notes = score.get('notes', [])
    if len(events) != len(notes) or not beats or any(type(v) not in (int, float) or not math.isfinite(v) or v <= 0 for v in beats):
        return ()
    starts = [0]
    for beat in beats:
        starts.append(starts[-1] + beat)
    marks = score.get('expressiveEvents', [])
    proposed = [(m, owners(m, notes)) for m in marks if m.get('kind') == 'PARENTHESIZED_NOTE_GROUP']
    counts = Counter(i for _, members in proposed if members is not None for i in members)
    ticks = lambda v: math.floor(v * 10080 + .5)
    result = []
    for mark, members in proposed:
        if members is None or any(counts[i] != 1 for i in members):
            continue
        previous_end, valid = -1, True
        for i in members:
            note, event = notes[i], events[i]
            m = note['measureIndex']
            begin, duration = event.get('startBeat'), event.get('durationBeats')
            if (event.get('kind', 'PITCHED') != 'PITCHED' or event.get('durationFallback', False)
                    or event.get('grace', False) or type(event.get('sourceNoteIndex')) is not int or event['sourceNoteIndex'] != i
                    or any(event.get(k) != note[k] for k in ('measureIndex', 'staffIndex', 'staffCount'))
                    or m >= len(beats) or any(type(v) not in (int, float) or not math.isfinite(v) for v in (begin, duration))
                    or duration <= 0 or not ticks(starts[m]) <= ticks(begin) < ticks(starts[m + 1])
                    or ticks(begin) < previous_end or ticks(begin + duration) > ticks(starts[-1])):
                valid = False
                break
            previous_end = ticks(begin + duration)
        if not valid:
            continue
        if mark['scope'] == 'NOTE' and ticks(starts[notes[members[0]]['measureIndex']] + mark['start']['quarterBeatOffset']) != ticks(events[members[0]]['startBeat']):
            continue
        pair = decode(mark['qualifierText'])
        result.append((mark, pair, members))
    return tuple(sorted(result, key=lambda r: events[r[2][0]]['startBeat']))
