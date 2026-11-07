# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Absolute dynamics at explicitly owned, exact source note columns."""
import math
from .technique_export import position


def plan(page, rendered, bars, page_identity):
    notes = page.get('score', {}).get('notes', [])
    events = page.get('events', [])
    if len(notes) != len(events) or len(rendered) != len(notes):
        return []
    result, seen = [], set()
    for index, change in enumerate(page.get('score', {}).get('dynamicChanges', [])):
        m, staff, count = (change.get(k) for k in ('measureIndex', 'staffIndex', 'staffCount'))
        p, db = position(change.get('positionInMeasure')), change.get('decibels')
        shared = change.get('sharedStaffs', False)
        timing = change.get('sharedTiming', shared)
        if (type(change.get('direction')) is not int or change['direction'] != 0
                or type(m) is not int or not 0 <= m < len(bars)
                or type(staff) is not int or type(count) is not int or not 0 <= staff < count <= 8
                or p is None or type(db) not in (int, float) or not math.isfinite(db) or not -24 <= db <= 9
                or type(shared) is not bool or type(timing) is not bool):
            continue
        timing |= shared
        owners, malformed = [], False
        for i, note in enumerate(notes):
            if note.get('measureIndex') != m:
                continue
            if timing:
                if note.get('staffCount') != count:
                    continue
            elif note.get('staffIndex') != staff:
                continue
            if position(note.get('positionInMeasure')) != p:
                continue
            if (any(type(note.get(k)) is not int for k in ('measureIndex', 'staffIndex', 'staffCount'))
                    or note['staffCount'] != count or not 0 <= note['staffIndex'] < count):
                malformed = True
                break
            owners.append(i)
        if malformed or not owners:
            continue
        start, valid = None, True
        for i in owners:
            source, event, row = notes[i], events[i], rendered[i]
            at = row['start']
            if (type(event.get('sourceNoteIndex')) is not int or event['sourceNoteIndex'] != i
                    or event.get('durationFallback') is not False
                    or any(type(event.get(k)) is not int or event[k] != source.get(k)
                           for k in ('measureIndex', 'staffIndex', 'staffCount'))
                    or type(at) is not int or not bars[m]['start'] <= at <= bars[m]['start'] + bars[m]['length']
                    or at == bars[m]['start'] + bars[m]['length'] and not row.get('grace', False)
                    or start is not None and start != at):
                valid = False
                break
            start = at
        if not valid:
            continue
        name = ('ppp' if db <= -18 else 'pp' if db <= -12 else 'p' if db <= -8 else
                'mp' if db <= -4 else 'mf' if db <= 0 else 'f' if db <= 4 else 'ff' if db <= 7 else 'fff')
        targets = sorted({n['staffIndex'] for n in notes
                          if type(n.get('staffIndex')) is int and 0 <= n['staffIndex'] < count
                          and type(n.get('staffCount')) is int and n['staffCount'] == count
                          and n.get('measureIndex') == m}) if shared else [staff]
        for target in targets:
            key = (m, target, count, name, start)
            if key in seen:
                continue
            seen.add(key)
            result.append(dict(identity=f'{page_identity}/absolute-dynamic:{index}:{target}',
                kind='ABSOLUTE_DYNAMIC', scope='PART', staffIndex=target, staffCount=count,
                startTick=start, endTick=None, ownerIds=[rendered[i]['sourceIdentity'] for i in owners if notes[i]['staffIndex']==target], evidence=[],
                _dynamicGroup=f'{page_identity}/absolute-dynamic:{index}',
                _absoluteDynamic=name, _absoluteDynamicBarStart=bars[m]['start']))
    return result
