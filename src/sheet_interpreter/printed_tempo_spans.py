# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Retain optically proved gradual-tempo spans through document resolution."""
import copy
import math
import re
import struct

SOURCE = 'printed-tempo-dash-span'
END = 'printed-tempo-dash-end-v1:'
OPEN = 'printed-tempo-dash-page-edge-v1:'
GRADUAL = {'ACCELERANDO', 'RITARDANDO', 'RALLENTANDO'}
TEMPO = GRADUAL | {'RITENUTO', 'A_TEMPO', 'TEMPO_PRIMO', 'METRIC_MODULATION'}


def _hint(event, prefix):
    values = [e['sourceId'][len(prefix):] for e in event['evidence']
              if e['sourceId'].startswith(prefix)]
    if not values or any(not re.fullmatch(r'[1-9][0-9]{0,3}', v) for v in values):
        return None
    return int(values[0]) if len(set(values)) == 1 else None


def has_open(event):
    return any(e['sourceId'].startswith(OPEN) for e in event['evidence'])


def _measure(event):
    target = event.get('targetEventId') or ''
    if not target.startswith(('expression-column:', 'expression-rest:')) or not any(
            e['sourceId'] in ('printed-expression-word', 'printed-expression-symbol')
            for e in event['evidence']):
        return None
    try:
        m, staff, count, bits = map(int, target.split(':')[1:])
        position = struct.unpack('>f', struct.pack('>i', bits))[0]
        if m >= 0 and staff == event['staffIndex'] and count == event['staffCount'] and 0 <= position <= 1:
            return m
    except (ValueError, struct.error):
        pass
    return None


def preserve_end(event, start, page):
    """Preserve a proved endpoint only if resolving ownership did not move its start."""
    if event['kind'] not in GRADUAL or event.get('start') not in (None, start) or not any(
            e['sourceId'] == SOURCE for e in event['evidence']):
        return None
    count = len(page['measureBeats'])
    if event.get('end') is not None:
        end = event['end']
        m, beat = end['measureIndex'], end['quarterBeatOffset']
        if 0 <= m <= count and math.isfinite(beat) and beat >= 0 and (
                m < count and beat < page['measureBeats'][m] or m == count and beat == 0) and (
                m, beat) > (start['measureIndex'], start['quarterBeatOffset']):
            return copy.deepcopy(end)
        return None
    delta = _hint(event, END)
    if delta is None:
        return None
    target = start['measureIndex'] + delta
    marks = [t for t in page.get('score', {}).get('tempoChanges', [])
             if t['measureIndex'] == target and t['positionInMeasure'] == 0]
    if target < count and marks and len({t['bpm'] for t in marks}) == 1:
        return dict(measureIndex=target, quarterBeatOffset=0)
    return None


def _latest(marks, fallback):
    if not marks:
        return fallback
    last = marks[-1]
    values = {t['bpm'] for t in marks if (t['measureIndex'], t['positionInMeasure']) ==
              (last['measureIndex'], last['positionInMeasure'])}
    return last['bpm'] if len(values) == 1 else None


def resolve_pages(pages):
    """Resolve only within the supplied continuous movement or playback range."""
    result = copy.deepcopy(pages)
    carried = None
    for i, page in enumerate(result):
        if page is None or not page['measureBeats']:
            carried = None
            continue
        score = page.get('score', {})
        count = len(page['measureBeats'])
        marks = sorted(score.get('tempoChanges', []), key=lambda t: (t['measureIndex'], t['positionInMeasure']))
        next_page = result[i + 1] if i + 1 < len(result) else None
        adjacent = bool(next_page and next_page['measureBeats'])
        first = score.get('firstMeasureNumber', 0)
        following = next_page.get('score', {}).get('firstMeasureNumber', 0) if next_page else 0
        if first > 0 and following > 0 and first + count != following:
            adjacent = False
        openings = [t for t in next_page.get('score', {}).get('tempoChanges', [])
                    if t['measureIndex'] == 0 and t['positionInMeasure'] == 0] if adjacent else []
        events = score.get('expressiveEvents', [])
        candidates = [e for e in events if has_open(e)]
        if len(candidates) == 1 and openings and len({t['bpm'] for t in openings}) == 1:
            event = candidates[0]
            measure, delta = _measure(event), _hint(event, OPEN)
            current = carried
            if measure is not None and delta is not None and measure + delta == count:
                current = _latest([t for t in marks if (t['measureIndex'], t['positionInMeasure']) <= (measure, 0)], carried)
                intervening = any((t['measureIndex'], t['positionInMeasure']) > (measure, 0) for t in marks)
                intervening |= any(e is not event and e['kind'] in TEMPO and _measure(e) is not None
                                   and _measure(e) >= measure for e in events)
                faster = event['kind'] == 'ACCELERANDO' or event['kind'] == 'UNRESOLVED_DIRECTION' and any(
                    e['sourceId'] == 'printed-expression-word' and re.fullmatch(r'\s*(?:[mpf]{1,3}\s+)?faster\s*[-.!]*\s*', e['printedText'], re.I)
                    for e in event['evidence'])
                slower = event['kind'] in ('RITARDANDO', 'RALLENTANDO')
                target = openings[0]['bpm']
                if current is not None and math.isfinite(current) and not intervening and (
                        faster and target > current or slower and target < current):
                    evidence = [e for e in event['evidence'] if not e['sourceId'].startswith(OPEN)]
                    evidence.extend(dict(event['evidence'][0], sourceId=s) for s in (
                        SOURCE, END + str(delta), 'printed-tempo-dash-adjacent-page-v1'))
                    event.update(kind='ACCELERANDO' if faster else event['kind'], evidence=evidence,
                                 end=dict(measureIndex=count, quarterBeatOffset=0) if event.get('start') else None,
                                 scope='SCORE' if event.get('start') else 'UNRESOLVED')
        carried = _latest(marks, carried)
        if not adjacent:
            carried = None
    return result
