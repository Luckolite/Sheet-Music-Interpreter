# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Guide263's bounded semantic records; stable IDs match ScoreSemanticWire, not enum order."""
import io
import math
import struct
from encodings.utf_16_le import encode as utf16_encode

KINDS = ('UNRESOLVED_DIRECTION', 'RITARDANDO', 'RALLENTANDO', 'RITENUTO',
         'ACCELERANDO', 'A_TEMPO', 'TEMPO_PRIMO', 'SAME_TEMPO', 'FERMATA',
         'BREATH', 'CAESURA', 'SFORZANDO', 'SFORZATO', 'SFORZANDO_PIANO',
         'PEDAL_DOWN', 'PEDAL_UP', 'ARPEGGIO', 'CRESCENDO', 'DIMINUENDO', 'METRIC_MODULATION', 'NATURAL_HARMONIC', 'FALL_OFF', 'PARENTHESIZED_NOTE_GROUP')
SCOPES = ('SCORE', 'PART', 'VOICE', 'NOTE', 'REST', 'UNRESOLVED')
STRENGTHS = ('UNSPECIFIED', 'POCO', 'MOLTO')
POLICIES = ('DEFAULT', 'SKIP', 'PLAY', 'UNKNOWN')
JUMPS = (2, 7, 8, 9, 10, 11)
MAX_RECORD_BYTES = 262144
DEFAULT_DETAILS = dict(quarterBeatOffset=0, eventId='', targetId='', codaTargetId='',
                       repeatGroupId='', totalPlays=2, passes=[], end=None,
                       afterJumpRepeats='DEFAULT', printedText='', evidence=[])


def integer(value, minimum, maximum):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError('Invalid semantic integer')
    return value


def number(value, minimum, maximum):
    if type(value) not in (int, float) or not math.isfinite(value) or not minimum <= value <= maximum:
        raise ValueError('Invalid semantic number')
    return value


def text(value, maximum):
    # Explicit codec import keeps frozen desktop packages complete too.
    if not isinstance(value, str) or len(utf16_encode(value)[0]) // 2 > maximum:
        raise ValueError('Invalid semantic text')
    value.encode('utf-8', errors='strict')
    return value


def anchor(value):
    if value is None:
        return None
    return dict(measureIndex=integer(value['measureIndex'], 0, 100000),
                quarterBeatOffset=number(value['quarterBeatOffset'], 0, 128))


def evidence(values, required=False):
    if not isinstance(values, list) or not (1 if required else 0) <= len(values) <= 64:
        raise ValueError('Invalid semantic evidence count')
    for e in values:
        if not text(e['sourceId'], 256).strip():
            raise ValueError('Empty semantic evidence identity')
        integer(e['pageIndex'], 0, 100000)
        number(e['visualX'], 0, 1)
        integer(e['staffCount'], 1, 2147483647)
        integer(e['staffIndex'], 0, e['staffCount'] - 1)
        text(e['printedText'], 4096)
    return values


def owned_start(value, measures):
    if value is not None and (value['measureIndex'] > measures
            or value['measureIndex'] == measures and value['quarterBeatOffset'] != 0):
        raise ValueError('Semantic start outside page')


def direction(row, measures):
    boundary = integer(row['measureBoundary'], 0, measures)
    kind = integer(row['kind'], 0, 13)
    supplied = row.get('details', {})
    if not isinstance(supplied, dict) or set(supplied) - set(DEFAULT_DETAILS):
        raise ValueError('Unknown navigation detail')
    d = dict(DEFAULT_DETAILS, **supplied)
    offset = number(d['quarterBeatOffset'], 0, 128)
    owned_start(dict(measureIndex=boundary, quarterBeatOffset=offset), measures)
    for key in ('eventId', 'targetId', 'codaTargetId', 'repeatGroupId'):
        text(d[key], 256)
    integer(d['totalPlays'], 1, 128)
    passes = d['passes']
    if not isinstance(passes, list) or len(passes) > 128:
        raise ValueError('Invalid navigation passes')
    for value in passes:
        integer(value, 1, 128)
    if len(set(passes)) != len(passes) or passes and kind not in (6, *JUMPS):
        raise ValueError('Invalid navigation pass selector')
    d['end'] = anchor(d['end'])
    if d['end'] is not None and (d['end']['measureIndex'], d['end']['quarterBeatOffset']) <= (boundary, offset):
        raise ValueError('Invalid navigation span')
    if kind == 6 and (d['end'] is None or not passes):
        raise ValueError('Ending needs endpoint and passes')
    if d['afterJumpRepeats'] not in POLICIES:
        raise ValueError('Unknown return policy')
    text(d['printedText'], 4096)
    evidence(d['evidence'])
    if d != DEFAULT_DETAILS and not d['eventId'].strip():
        raise ValueError('Rich navigation needs source identity')
    return dict(measureBoundary=boundary, kind=kind, details=d)


def expression(row, measures):
    if not text(row['eventId'], 256).strip() or row['kind'] not in KINDS or row['scope'] not in SCOPES:
        raise ValueError('Invalid expressive identity or kind')
    start, end = anchor(row['start']), anchor(row['end'])
    owned_start(start, measures)
    if end is not None and (start is None or (end['measureIndex'], end['quarterBeatOffset'])
                           < (start['measureIndex'], start['quarterBeatOffset'])):
        raise ValueError('Invalid expressive span')
    if row['scope'] != 'UNRESOLVED' and start is None:
        raise ValueError('Resolved expression needs anchor')
    integer(row['staffCount'], 1, 2147483647)
    integer(row['staffIndex'], 0, row['staffCount'] - 1)
    target = row['targetEventId']
    if target is not None and not text(target, 256).strip():
        raise ValueError('Empty expressive target')
    if row['kind'] == 'FERMATA' and row['scope'] != 'UNRESOLVED' and (end is None or target is None):
        raise ValueError('Resolved fermata needs target and release')
    if row['strength'] not in STRENGTHS:
        raise ValueError('Unknown expressive strength')
    text(row['qualifierText'], 4096)
    if row['kind'] == 'METRIC_MODULATION':
        values = row['qualifierText'].split(':')
        try:
            allowed = {base*factor for base in (.125,.25,.5,1,2,4) for factor in (1,1.5,1.75)}
            valid = len(values) == 3 and values[0] == 'metric-pulse-v1' and all(float(v) in allowed for v in values[1:])
        except ValueError:
            valid = False
        if not valid:
            raise ValueError('Metric modulation needs a validated pulse pair')
    if row['kind'] == 'NATURAL_HARMONIC' and (
            row['scope'] not in ('NOTE', 'UNRESOLVED') or target is None or end is not None
            or row['scope'] == 'UNRESOLVED' and start is not None
            or row['scope'] == 'NOTE' and start is None):
        raise ValueError('Natural harmonic needs one exact note owner')

    if row['kind'] == 'FALL_OFF':
        try:
            fields = target.split(':')
            nums = [int(fields[i]) for i in (1, 2, 3, 5)]
            floats = [struct.unpack('>f', bytes.fromhex(fields[i]))[0] for i in (4, 6)]
            valid = (len(fields) == 7 and fields[0] == 'fall-off-note-v1'
                     and all(-2147483648 <= n <= 2147483647 for n in nums)
                     and nums[0] >= 0 and 0 <= nums[1] < nums[2]
                     and all(len(fields[i]) == 8 and all(c in '0123456789abcdef' for c in fields[i]) for i in (4, 6))
                     and all(math.isfinite(f) and 0 <= f <= 1 for f in floats))
        except (ValueError, TypeError, AttributeError, IndexError, struct.error):
            valid = False
        if (not valid or row['scope'] not in ('NOTE', 'UNRESOLVED') or end is not None
                or row['scope'] == 'UNRESOLVED' and start is not None
                or row['scope'] == 'NOTE' and start is None
                or row['strength'] != 'UNSPECIFIED' or row['qualifierText'] != 'falloff-v1:curved'):
            raise ValueError('Falloff needs one exact note owner and printed shape')
    if row['kind'] == 'PARENTHESIZED_NOTE_GROUP':
        try:
            from .parenthesized_note_group import decode as decode_parentheses
        except ImportError:
            from parenthesized_note_group import decode as decode_parentheses
        pair = decode_parentheses(row['qualifierText'])
        if (pair is None or row['scope'] not in ('NOTE', 'UNRESOLVED') or end is not None
                or row['scope'] == 'UNRESOLVED' and start is not None
                or row['scope'] == 'NOTE' and (start is None or start['measureIndex'] != pair.members[0].measure)
                or row['strength'] != 'UNSPECIFIED' or target != pair.target
                or (row['staffIndex'], row['staffCount']) != (pair.members[0].staff, pair.members[0].count)
                or pair.members[0].measure >= measures):
            raise ValueError('Group needs exact ordered members and one printed pair')
    evidence(row['evidence'], required=True)
    return dict(row, start=start, end=end)


class Writer:
    def __init__(self):
        self.output = io.BytesIO()

    def put(self, fmt, *values):
        self.output.write(struct.pack('>' + fmt, *values))

    def text(self, value):
        raw = value.encode('utf-8'); self.put('i', len(raw)); self.output.write(raw)

    def anchor(self, value):
        self.put('?', value is not None)
        if value is not None:
            self.put('id', value['measureIndex'], value['quarterBeatOffset'])

    def evidence(self, values):
        self.put('i', len(values))
        for e in values:
            self.text(e['sourceId']); self.put('ifii', e['pageIndex'], e['visualX'], e['staffIndex'], e['staffCount'])
            self.text(e['printedText'])

    def frame(self, record):
        raw = record.output.getvalue()
        if len(raw) > MAX_RECORD_BYTES:
            raise ValueError('Semantic record too large')
        self.put('i', len(raw)); self.output.write(raw)


def encode(directions, expressions, measures, guide_version=277):
    if not isinstance(directions, list) or len(directions) > min(100000, (measures + 1) * 64):
        raise ValueError('Too many navigation records')
    if not isinstance(expressions, list) or len(expressions) > min(250000, (measures + 1) * 256):
        raise ValueError('Too many expressions')
    if guide_version < 277 and any(row['kind'] == 'METRIC_MODULATION' for row in expressions):
        raise ValueError('Metric modulation requires guide277')
    if guide_version < 294 and any(row['kind'] == 'NATURAL_HARMONIC' for row in expressions):
        raise ValueError('Natural harmonic requires guide294')
    if guide_version < 295 and any(row['kind'] == 'FALL_OFF' for row in expressions):
        raise ValueError('Falloff requires guide295')
    if guide_version < 296 and any(row['kind'] == 'PARENTHESIZED_NOTE_GROUP' for row in expressions):
        raise ValueError('Parenthesized group requires guide296')
    out = Writer(); out.put('i', len(directions))
    for row in directions:
        row = direction(row, measures); d = row['details']; r = Writer()
        r.put('iid', row['measureBoundary'], row['kind'], d['quarterBeatOffset'])
        for key in ('eventId', 'targetId', 'codaTargetId', 'repeatGroupId'):
            r.text(d[key])
        r.put('ii', d['totalPlays'], len(d['passes']))
        for value in d['passes']:
            r.put('i', value)
        r.anchor(d['end']); r.put('i', POLICIES.index(d['afterJumpRepeats']))
        r.text(d['printedText']); r.evidence(d['evidence']); out.frame(r)
    out.put('i', len(expressions)); ids = set()
    for row in expressions:
        row = expression(row, measures)
        if row['eventId'] in ids:
            raise ValueError('Duplicate expression identity')
        ids.add(row['eventId']); r = Writer(); r.text(row['eventId']); r.put('i', KINDS.index(row['kind']))
        r.anchor(row['start']); r.anchor(row['end']); r.put('iii', SCOPES.index(row['scope']), row['staffIndex'], row['staffCount'])
        r.put('?', row['targetEventId'] is not None)
        if row['targetEventId'] is not None:
            r.text(row['targetEventId'])
        r.put('i', STRENGTHS.index(row['strength'])); r.text(row['qualifierText']); r.evidence(row['evidence']); out.frame(r)
    return out.output.getvalue()


class Reader:
    def __init__(self, data):
        self.data, self.offset = memoryview(data), 0

    def take(self, fmt):
        size = struct.calcsize('>' + fmt)
        if self.offset + size > len(self.data):
            raise ValueError('Truncated semantic record')
        result = struct.unpack_from('>' + fmt, self.data, self.offset); self.offset += size
        return result[0] if len(result) == 1 else result

    def count(self, maximum):
        return integer(self.take('i'), 0, maximum)

    def raw(self, size):
        if self.offset + size > len(self.data):
            raise ValueError('Truncated semantic bytes')
        result = self.data[self.offset:self.offset + size]; self.offset += size
        return result

    def boolean(self):
        return bool(integer(self.take('B'), 0, 1))

    def text(self, maximum):
        return text(bytes(self.raw(self.count(maximum * 4))).decode('utf-8', errors='strict'), maximum)

    def anchor(self):
        return anchor(dict(measureIndex=self.take('i'), quarterBeatOffset=self.take('d'))) if self.boolean() else None

    def evidence(self):
        result = []
        for _ in range(self.count(64)):
            source = self.text(256); page, x, staff, count = self.take('ifii')
            result.append(dict(sourceId=source, pageIndex=page, visualX=x, staffIndex=staff,
                               staffCount=count, printedText=self.text(4096)))
        return result

    def enum(self, values):
        return values[integer(self.take('i'), 0, len(values) - 1)]

    def frame(self):
        return Reader(self.raw(self.count(MAX_RECORD_BYTES)))

    def finish(self):
        if self.offset != len(self.data):
            raise ValueError('Unexpected semantic bytes')


def decode_prefix(data, measures, guide_version=277):
    source = Reader(data); directions, expressions = [], []; ids = set()
    for _ in range(source.count(min(100000, (measures + 1) * 64))):
        r = source.frame(); boundary, kind, offset = r.take('iid')
        d = dict(quarterBeatOffset=offset)
        for key in ('eventId', 'targetId', 'codaTargetId', 'repeatGroupId'):
            d[key] = r.text(256)
        d['totalPlays'] = r.take('i'); d['passes'] = [r.take('i') for _ in range(r.count(128))]
        d['end'] = r.anchor(); d['afterJumpRepeats'] = r.enum(POLICIES)
        d['printedText'] = r.text(4096); d['evidence'] = r.evidence(); r.finish()
        directions.append(direction(dict(measureBoundary=boundary, kind=kind, details=d), measures))
    for _ in range(source.count(min(250000, (measures + 1) * 256))):
        r = source.frame(); row = dict(eventId=r.text(256), kind=r.enum(KINDS), start=r.anchor(), end=r.anchor())
        if guide_version < 277 and row['kind'] == 'METRIC_MODULATION':
            raise ValueError('Metric modulation is outside this guide format')
        if guide_version < 294 and row['kind'] == 'NATURAL_HARMONIC':
            raise ValueError('Natural harmonic is outside this guide format')
        if guide_version < 295 and row['kind'] == 'FALL_OFF':
            raise ValueError('Falloff is outside this guide format')
        if guide_version < 296 and row['kind'] == 'PARENTHESIZED_NOTE_GROUP':
            raise ValueError('Parenthesized group is outside this guide format')
        row['scope'] = r.enum(SCOPES); row['staffIndex'], row['staffCount'] = r.take('ii')
        row['targetEventId'] = r.text(256) if r.boolean() else None
        row['strength'] = r.enum(STRENGTHS); row['qualifierText'] = r.text(4096); row['evidence'] = r.evidence(); r.finish()
        row = expression(row, measures)
        if row['eventId'] in ids:
            raise ValueError('Duplicate expressive identity')
        ids.add(row['eventId']); expressions.append(row)
    return directions, expressions, source.offset


def decode(data, measures, guide_version=277):
    directions, expressions, consumed = decode_prefix(data, measures, guide_version)
    if consumed != len(data):
        raise ValueError('Unexpected semantic bytes')
    return directions, expressions
