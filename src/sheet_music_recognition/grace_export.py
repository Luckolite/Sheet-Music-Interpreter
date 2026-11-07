# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Validate retained written grace ownership separately from playback budgets."""
from collections import defaultdict
import math
from .typed_events import event_kind
from .articulation_export import source_flags


def plan(page, ticks):
    notes, events = page.get('score', {}).get('notes', []), page['events']
    if not any(type(n.get('articulations', 0)) is int and n.get('articulations', 0) & (1 << 15) for n in notes):
        return {}
    if len(notes) != len(events):
        raise ValueError('Grace export needs one event per retained source head')
    for i in range(len(events)):
        source_flags(page, i)  # Validate signed flags before interpreting the grace bit.
    result, groups, budgets = {}, defaultdict(list), defaultdict(float)
    beat_starts = [0.0]
    for beats in page['measureBeats']:
        beat_starts.append(beat_starts[-1]+beats)
    page_end = ticks(beat_starts[-1])
    for i, (note, event) in enumerate(zip(notes, events)):
        fields = ('measureIndex', 'staffIndex', 'staffCount')
        if (type(event.get('sourceNoteIndex', i)) is not int or event.get('sourceNoteIndex', i) != i
                or any(k not in note or k not in event or note[k] != event[k] for k in fields)):
            raise ValueError('Grace written placement source identity disagrees')
        p = event.get('writtenPlacement')
        if not isinstance(p, dict):
            raise ValueError('Grace export needs retained written placement metadata')
        for key in ('onsetBeats', 'durationBeats', 'stealPercent'):
            if type(p.get(key)) not in (int, float) or not math.isfinite(p[key]) or p[key] < 0:
                raise ValueError('Invalid grace written clock')
        for key in ('principalIndex', 'ordinal'):
            if type(p.get(key)) is not int:
                raise ValueError('Invalid grace written owner')
        if type(p.get('afterGrace')) is not bool or p['durationBeats'] <= 0:
            raise ValueError('Invalid grace written placement')
        m = event['measureIndex']
        if type(m) is not int or not 0 <= m < len(page['measureBeats']):
            raise ValueError('Grace written measure outside page')
        grace = p['principalIndex'] >= 0
        if grace != bool(note.get('articulations', 0) & (1 << 15)):
            raise ValueError('Grace written placement contradicts source mark')
        if grace and event_kind(event) != 'PITCHED':
            raise ValueError('Unpitched grace needs explicit instrument semantics')
        onset, duration = ticks(p['onsetBeats']), ticks(p['durationBeats'])
        absolute = ticks(beat_starts[m]) + onset
        if ((not grace or not p['afterGrace']) and onset > ticks(page['measureBeats'][m])
                or absolute > page_end or not grace and absolute+duration > page_end+2):
            raise ValueError('Grace written clock outside measure')
        if not grace and (p['principalIndex'] != -1 or p['ordinal'] != -1 or p['afterGrace'] or p['stealPercent'] != 0):
            raise ValueError('Metrical note has contradictory grace ownership')
        if grace and (not 0 <= p['principalIndex'] < len(events) or p['principalIndex'] == i
                      or p['ordinal'] < 0 or not 0 < p['stealPercent'] <= 100):
            raise ValueError('Invalid grace written principal')
        result[i] = dict(start=absolute,
                         duration=duration, grace=grace, principal=p['principalIndex'],
                         ordinal=p['ordinal'], after=p['afterGrace'], steal=p['stealPercent'])
        if grace:
            groups[p['principalIndex'], p['afterGrace']].append(i)
            budgets[p['principalIndex']] += p['stealPercent']
    for (principal, after), members in groups.items():
        owner = result[principal]
        if owner['grace'] or event_kind(events[principal]) != 'PITCHED':
            raise ValueError('Grace principal must be a metrical pitched head')
        expected = owner['start'] + (owner['duration'] if after else 0)
        for i in members:
            if (any(events[i][k] != events[principal][k] for k in ('measureIndex', 'staffIndex', 'staffCount'))
                    or result[i]['start'] != expected):
                raise ValueError('Grace principal context or written anchor disagrees')
        if sorted(result[i]['ordinal'] for i in members) != list(range(len(members))):
            raise ValueError('Grace group ordinals are incomplete or ambiguous')
    for budget in budgets.values():
        if budget > 100+1e-7:
            raise ValueError('Grace group exceeds principal playback budget')
    return result


def order(event):
    if not event.get('grace', False):
        return event['start'], 0
    return event['start'], event['graceOrdinal'] - (2000000 if event['afterGrace'] else 1000000)
