# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Strict original-head bindings for connected straight slides; clocks stay written."""
import math
import struct

def _f(value):
    return struct.unpack('!f', struct.pack('!f', value))[0]

def _part(n):
    return n.get('staffIndex'), n.get('staffCount'), n.get('clefBottomDiatonic')

def _previous(notes, target):
    t = notes[target]
    candidates = []
    for i, n in enumerate(notes):
        x = n.get('positionInMeasure')
        if _part(n) != _part(t) or type(x) not in (int, float) or not math.isfinite(x):
            continue
        if (n['measureIndex'] == t['measureIndex'] and _f(x) < _f(_f(t['positionInMeasure'])-_f(.001))
                or t['measureIndex'] > 0 and n['measureIndex'] == t['measureIndex']-1):
            candidates.append(i)
    if not candidates:
        return None
    latest = max(candidates, key=lambda i: (notes[i]['measureIndex'], _f(notes[i]['positionInMeasure'])))
    n = notes[latest]
    if any(i != latest and _part(c) == _part(t) and c['measureIndex'] == n['measureIndex']
           and abs(_f(_f(c['positionInMeasure'])-_f(n['positionInMeasure']))) < _f(.001)
           for i, c in enumerate(notes)):
        return None
    if n['measureIndex'] != t['measureIndex'] and (t.get('leadingRestBeats', 0) > 0 or n.get('followingRestBeats', 0) > 0):
        return None
    return latest

def bindings(page):
    """Return source/target/number triples; reject explicit stale or ambiguous relations."""
    events = page['events']
    explicit = [i for i, e in enumerate(events) if 'slide' in e]
    if not explicit:
        return []
    notes = page.get('score', {}).get('notes')
    def require(ok):
        if not ok:
            raise ValueError('Invalid continuous slide original-head relation')
    require(isinstance(notes, list) and len(notes) == len(events))
    for i, (n, e) in enumerate(zip(notes, events)):
        require(isinstance(n, dict) and isinstance(e, dict))
        require(all(type(n.get(k)) is int for k in ('measureIndex', 'staffIndex', 'staffCount', 'clefBottomDiatonic', 'articulations')))
        require(type(n.get('positionInMeasure')) in (int, float) and math.isfinite(n['positionInMeasure']) and 0 <= n['positionInMeasure'] <= 1)
        require(type(e.get('sourceNoteIndex', i)) is int and e.get('sourceNoteIndex', i) == i)
        require(all(type(e.get(k)) is int and e[k] == n.get(k) for k in ('measureIndex', 'staffIndex', 'staffCount')))
    result = []
    for target in explicit:
        e = events[target]; relation = e['slide']
        require(isinstance(relation, dict) and set(relation) == {'style', 'sourceNoteIndex', 'sourceMidi'} and relation['style'] == 'continuous')
        source = relation['sourceNoteIndex']
        require(type(source) is int and 0 <= source < len(notes) and source != target)
        require(type(relation['sourceMidi']) is int and 0 <= relation['sourceMidi'] <= 127)
        flags = notes[target].get('articulations', 0)
        require(flags & 224 == 192 and flags & 16384 and _previous(notes, target) == source)
        require(_part(notes[source]) == _part(notes[target]))
        for i in (source, target):
            n, event = notes[i], events[i]
            require(n.get('kind', 'PITCHED') == 'PITCHED' and event.get('kind', 'PITCHED') == 'PITCHED'
                    and not n.get('articulations', 0) & 32768 and n.get('clefBottomDiatonic', -1) != -1
                    and not event.get('clefInferred') and event.get('durationFallback') is False
                    and 'glissando' not in event and 'guitarEffect' not in event)
            require(type(event.get('midi')) is int and 0 <= event['midi'] <= 127)
            require(all(type(event.get(k)) in (int, float) and math.isfinite(event[k]) for k in ('startBeat', 'durationBeats')))
            require(event['startBeat'] >= 0 and event['durationBeats'] > 0)
        require(events[source]['midi'] == relation['sourceMidi'])
        end = events[source]['startBeat'] + events[source]['durationBeats']; start = e['startBeat']
        require(abs(end-start) <= 8*max(math.ulp(end), math.ulp(start)))
        result.append((source, target, target+1))
    require(len({s for s, _, _ in result}) == len(result))
    return result
