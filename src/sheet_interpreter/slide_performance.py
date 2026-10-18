# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Bind continuous-slide performance to original endpoints and actual route visits.

The source document stays written. Only a private performed copy is annotated
and coalesced; page-local raw indices are never used after clock projection.
"""
import copy
import math
from .straight_slide import bindings
from .tremolo_performance import source_spans


def _same(a, b):
    return abs(a-b) <= 8*max(math.ulp(float(a)), math.ulp(float(b)))


def prepare_slides(document):
    internal = ('_slideOrigins', '_slideRelation', '_slideContext', '_continuousSlide', '_slideGate')
    explicit = any('slide' in e for p in document['pages'] for e in p['events'])
    if not explicit and not any(any(k in e for k in internal)
                                for p in document['pages'] for e in p['events']):
        return document
    result = copy.deepcopy(document)
    # Caller data cannot supply trusted projection/ownership annotations.
    for page in result['pages']:
        for note in page['events']:
            for key in internal:
                note.pop(key, None)
    if not explicit:
        return result
    verified = [bindings(page) for page in result['pages']]
    for page_number, (page, pairs) in enumerate(zip(result['pages'], verified)):
        paired = source_spans(page) if pairs else {}
        for source, target, _ in pairs:
            for index in (source, target):
                if (page['events'][index].get('tremoloBeats', 0) != 0
                        or page['score']['notes'][index].get('articulations', 0) & (7 << 16)
                        or index in paired):
                    raise ValueError('Continuous slide conflicts with original endpoint tremolo')
            source_id = f'p{page_number}:n{source}'
            target_id = f'p{page_number}:n{target}'
            for index, identity in ((source, source_id), (target, target_id)):
                note = page['events'][index]
                origins = note.setdefault('_slideOrigins', [])
                if identity not in origins:
                    origins.append(identity)
            marks = page['score']['notes'][target]['articulations']
            page['events'][target]['_slideGate'] = (.28 if marks & 16 else
                                                    .75 if marks & 4 and marks & 8 else
                                                    .5 if marks & 4 else 1.)
            relation = page['events'][target]['slide']
            page['events'][target]['_slideRelation'] = dict(
                sourceId=source_id, targetId=target_id,
                sourceMidi=relation['sourceMidi'])
    return result


def _context(note, projected):
    value = note.get('_slideContext')
    if value is None:
        if projected:
            raise ValueError('Projected slide endpoint lacks attack and visit context')
        return dict(visitId='linear', attack=not note.get('tiedFromPrevious', False),
                    clippedEntry=False, clippedRelease=False)
    if (not isinstance(value, dict) or not isinstance(value.get('visitId'), str)
            or not value['visitId']
            or any(type(value.get(k)) is not bool
                   for k in ('attack', 'clippedEntry', 'clippedRelease'))):
        raise ValueError('Invalid projected slide attack and visit context')
    if 'held' in value and type(value['held']) is not bool:
        raise ValueError('Invalid performed slide hold ownership')
    return value


def _pitch(note):
    midi = note.get('midi')
    if (note.get('kind', 'PITCHED') != 'PITCHED' or type(midi) is not int
            or not 0 <= midi <= 127):
        raise ValueError('Continuous slide endpoint has no resolved pitched MIDI')
    return midi


def _compatible(a, b):
    return all(a.get(key) == b.get(key) for key in (
        'midi', 'staffCount', 'staffIndex', 'guitarEffect', 'glissando',
        'performanceAttack', 'tremoloBeats', '_slideGate'))


def resolve_slides(document):
    annotated = any(e.get('_slideOrigins') for p in document['pages'] for e in p['events'])
    if not annotated:
        if any('slide' in e for p in document['pages'] for e in p['events']):
            raise ValueError('Continuous slides must be prepared before clock projection')
        return document
    result = copy.deepcopy(document)
    projected = 'navigationOccurrences' in result or 'expressivePerformance' in result
    rows, offset = [], 0.0
    for page_number, page in enumerate(result['pages']):
        for index, note in enumerate(page['events']):
            if not note.get('_slideOrigins'):
                continue
            origins = note['_slideOrigins']
            if (not isinstance(origins, list) or not origins
                    or any(not isinstance(x, str) or not x for x in origins)
                    or len(set(origins)) != len(origins)):
                raise ValueError('Invalid original slide endpoint identities')
            start, duration = note.get('startBeat'), note.get('durationBeats')
            if (any(type(v) not in (int, float) or not math.isfinite(v)
                    for v in (start, duration)) or start < 0 or duration <= 0):
                raise ValueError('Continuous slide endpoint has an invalid performed clock')
            context = _context(note, projected)
            rows.append(dict(page=page_number, index=index, note=note,
                             start=offset+start, end=offset+start+duration,
                             context=context))
        extent = page['totalBeats']
        if type(extent) not in (int, float) or not math.isfinite(extent) or extent < 0:
            raise ValueError('Invalid performed slide page extent')
        offset += extent
    by_origin = {}
    targets = {}
    for row in rows:
        note, visit = row['note'], row['context']['visitId']
        for origin in note['_slideOrigins']:
            by_origin.setdefault((visit, origin), []).append(row)
        relation = note.get('_slideRelation')
        if relation is not None:
            if (not isinstance(relation, dict)
                    or set(relation) != {'sourceId', 'targetId', 'sourceMidi'}
                    or any(not isinstance(relation[k], str) or not relation[k]
                           for k in ('sourceId', 'targetId'))
                    or relation['targetId'] not in note['_slideOrigins']):
                raise ValueError('Invalid projected continuous slide relation')
            targets.setdefault((visit, relation['targetId']), []).append(row)
    remove = set()
    claims = {}
    pending = []
    for (visit, target_id), fragments in targets.items():
        fragments.sort(key=lambda r: (r['start'], r['end']))
        first = fragments[0]
        context = first['context']
        # A jump into a sustain is not a new connected-slide attack.
        if not context['attack'] or context['clippedEntry']:
            continue
        relation = first['note']['_slideRelation']
        target_midi = _pitch(first['note'])
        end = first['end']
        for later in fragments[1:]:
            if (later['context']['attack'] or not _same(end, later['start'])
                    or later['note']['_slideRelation'] != relation
                    or not _compatible(first['note'], later['note'])):
                raise ValueError('Ambiguous or changing continuous slide target continuation')
            end = later['end']
        source_rows = sorted(by_origin.get((visit, relation['sourceId']), []),
                             key=lambda r: (r['start'], r['end']))
        candidates = []
        attack_seen, previous_end = False, None
        for source in source_rows:
            if previous_end is None or not _same(previous_end, source['start']):
                attack_seen = False
            if source['context']['attack'] and not source['context']['clippedEntry']:
                attack_seen = True
            previous_end = source['end']
            if (attack_seen and not source['context']['clippedRelease']
                    and _same(source['end'], first['start'])):
                candidates.append(source)
        if not candidates:
            continue  # The written predecessor was skipped or clipped by this route.
        if len(candidates) != 1:
            raise ValueError('Ambiguous performed continuous slide source')
        source = candidates[0]
        if any(source['note'].get(k) != first['note'].get(k)
               for k in ('staffCount', 'staffIndex')):
            raise ValueError('Continuous slide changed performed part')
        source_midi = _pitch(source['note'])
        if any(first['note'].get(k) for k in ('glissando', 'guitarEffect', 'tremoloBeats')):
            raise ValueError('Conflicting continuous slide performance')
        claim = (visit, relation['sourceId'], source['end'])
        claims[claim] = claims.get(claim, 0)+1
        pending.append((claim, first, fragments, end, source_midi, target_midi))
    for claim, first, fragments, end, source_midi, target_midi in pending:
        if claims[claim] != 1:
            raise ValueError('Multiple performed slide targets claim one source')
        first['note']['durationBeats'] = end-first['start']
        first['note']['_continuousSlide'] = dict(sourceMidi=source_midi)
        gate = first['note'].get('_slideGate', 1.)
        if type(gate) not in (int, float) or gate not in (.28, .5, .75, 1.):
            raise ValueError('Invalid original slide articulation gate')
        if any(fragment['context'].get('held', False) for fragment in fragments):
            gate = 1.
        if gate != 1:
            first['note']['_continuousSlide']['gate'] = gate

        for later in fragments[1:]:
            remove.add((later['page'], later['index']))
    for page_number, page in enumerate(result['pages']):
        retained = []
        for index, note in enumerate(page['events']):
            note.pop('slide', None)
            note.pop('_slideRelation', None)
            if (page_number, index) not in remove:
                retained.append(note)
        page['events'] = retained
    return result
