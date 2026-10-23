from . import straight_slide, slide_numbering
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""MusicXML 4.0 concert-pitch export of the decoded performance (not source engraving)."""
from collections import defaultdict
from functools import lru_cache
from math import gcd
from fractions import Fraction
from decimal import Decimal
import math
from pathlib import Path
import xml.etree.ElementTree as ET
from .typed_events import event_kind, validate_unpitched, notation_order, display_position
from .rest_kinds import rest_kind, full_measure_span
from . import bow_export, clef_export, articulation_export, ornament_export, grace_export, spelling_export, tremolo_export
from . import guitar_export

DIVISIONS = 10080  # Exact common binary values and triplet/quintuplet/septuplet subdivisions.


def element(parent, tag, value=None, **attributes):
    node = ET.SubElement(parent, tag, {key: str(value) for key, value in attributes.items()})
    if value is not None:
        node.text = str(value)
    return node


def ticks(value):
    if not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
        raise ValueError("MusicXML needs finite, nonnegative event times")
    return round(value * DIVISIONS)


def note_type(node, duration, event=None):
    actual = event.get('tupletActualNotes', 1) if event else 1
    normal = event.get('tupletNormalNotes', 1) if event else 1
    if (type(actual) is not int or type(normal) is not int or actual not in (1,2,3,4,5,6,7)
            or not 1 <= normal <= 16 or actual == 1 and normal != 1):
        raise ValueError('Unsupported explicit tuplet ratio')
    if actual != 1:
        for denominator, name in ((1,'whole'), (2,'half'), (4,'quarter'), (8,'eighth'),
                                  (16,'16th'), (32,'32nd'), (64,'64th')):
            base = DIVISIONS * 4 // denominator
            for dots, scale in ((0,1), (1,1.5), (2,1.75)):
                if abs(duration - base*scale*normal/actual) <= 1:
                    element(node, 'type', name)
                    for _ in range(dots):
                        element(node, 'dot')
                    modification = element(node, 'time-modification')
                    element(modification, 'actual-notes', actual)
                    element(modification, 'normal-notes', normal)
                    return
        return  # An explicit ratio must not silently become ordinary or a different tuplet.
    for denominator, name in ((1, 'whole'), (2, 'half'), (4, 'quarter'), (8, 'eighth'),
                              (16, '16th'), (32, '32nd'), (64, '64th')):
        base = DIVISIONS * 4 // denominator
        for dots, scale in ((0, 1), (1, 1.5), (2, 1.75)):
            if abs(duration - base * scale) <= 1:
                element(node, 'type', name)
                for _ in range(dots):
                    element(node, 'dot')
                return
        for actual, normal in ((3, 2), (5, 4), (7, 4)):
            if abs(duration - base * normal / actual) <= 1:
                element(node, 'type', name)
                modification = element(node, 'time-modification')
                element(modification, 'actual-notes', actual)
                element(modification, 'normal-notes', normal)
                return


@lru_cache(maxsize=256)
def exact_written_parts(duration, values):
    """Fewest exact glyphs from a proved duration vocabulary; no clock rounding."""
    if not values or duration <= 0:
        return None
    quantum = 0
    for value in values:
        quantum = gcd(quantum, value)
    if duration % quantum:
        return None
    target = duration // quantum
    units = tuple(value // quantum for value in values)
    unreachable = target + 1
    count, choice = [unreachable] * (target + 1), [0] * (target + 1)
    count[0] = 0
    for amount in range(1, target + 1):
        for unit in units:
            if unit <= amount and count[amount-unit] + 1 < count[amount]:
                count[amount], choice[amount] = count[amount-unit] + 1, unit
    if not choice[target]:
        return None
    parts = []
    while target:
        parts.append(choice[target] * quantum)
        target -= choice[target]
    return tuple(sorted(parts, reverse=True))


def duration_parts(duration, events=()):
    """Split exact composite spans while retaining each head's explicit ratio."""
    heads = tuple(events) or (None,)
    probes = []
    for event in heads:
        probe = ET.Element('note')
        note_type(probe, duration, event)
        probes.append(probe)
    if all(probe.find('type') is not None for probe in probes):
        return (duration,)
    written_values = {Fraction(DIVISIONS * 4, denominator) * Fraction(numerator, 4)
                      for denominator in (1, 2, 4, 8, 16, 32, 64)
                      for numerator in (4, 6, 7)}
    if any(event and event.get('tupletActualNotes', 1) != 1 for event in heads):
        common = None
        for event in heads:
            actual = event.get('tupletActualNotes', 1) if event else 1
            normal = event.get('tupletNormalNotes', 1) if event else 1
            values = {int(value * normal / actual) for value in written_values
                      if (value * normal / actual).denominator == 1}
            common = values if common is None else common & values
        parts = exact_written_parts(duration, tuple(sorted(common, reverse=True)))
        return parts if parts is not None else (duration,)
    # Keep the existing inferred-clock boundary: only ordinary 64th-grid spans split.
    if duration % (DIVISIONS // 16):
        return (duration,)
    values = sorted((int(value) for value in written_values if value.denominator == 1), reverse=True)
    parts, remaining = [], duration
    while remaining:
        value = next(v for v in values if v <= remaining)
        parts.append(value)
        remaining -= value
    return tuple(parts)


def metric_pulse(value):
    for i, name in enumerate(('whole', 'half', 'quarter', 'eighth', '16th', '32nd')):
        for dots, scale in enumerate((1, 1.5, 1.75)):
            if value == math.ldexp(4, -i)*scale:
                return name, dots
    raise ValueError('Unsupported MusicXML metric pulse')


def tempo_mark(tempo):
    """Printed pulse and rate; sound tempo remains quarter notes per minute."""
    bpm, pulse = tempo['bpm'], tempo.get('beatUnit', 1)
    if (type(bpm) not in (int, float) or not math.isfinite(bpm) or not 15 <= bpm <= 1600
            or type(pulse) not in (int, float) or not math.isfinite(pulse) or not 0 < pulse <= 4):
        raise ValueError('Invalid printed tempo')
    names = ('whole', 'half', 'quarter', 'eighth', '16th', '32nd', '64th',
             '128th', '256th', '512th', '1024th')
    for i, name in enumerate(names):
        for dots, scale in enumerate((1, 1.5, 1.75)):
            unit = math.ldexp(4, -i) * scale
            if abs(unit - pulse) < 1e-9:
                return name, dots, bpm / unit
    # Match ScoreTempoMark: unsupported pulses use an equivalent quarter mark.
    return 'quarter', 0, bpm


def expression_direction(measure, mark, offset):
    kind = mark['kind']
    caption = mark.get('_neutralRestCaption', False)
    direction = element(measure, 'direction', placement='below' if caption or kind in ('PEDAL_DOWN', 'PEDAL_UP') else 'above')
    type_node = element(direction, 'direction-type')
    if mark.get('_absoluteDynamic'):
        direction.set('placement', 'below')
        element(element(type_node, 'dynamics'), mark['_absoluteDynamic'])
    elif kind in ('PEDAL_DOWN', 'PEDAL_UP'):
        literal = (mark.get('targetEventId') or '').startswith('expression-column:') and any(
            e.get('sourceId') == 'printed-expression-word' for e in mark.get('evidence', []))
        if literal:
            printed = next((e['printedText'] for e in mark.get('evidence', []) if e.get('printedText')), None)
            if not printed:
                measure.remove(direction)
                return
            element(type_node, 'words', printed)
        else:
            element(type_node, 'pedal', type='start' if kind == 'PEDAL_DOWN' else 'stop', line='yes', sign='no')
    elif kind == 'METRIC_MODULATION':
        fields = mark.get('qualifierText', '').split(':')
        if len(fields) != 3 or fields[0] != 'metric-pulse-v1':
            raise ValueError('Metric relationship has no explicit pulse pair')
        metronome = element(type_node, 'metronome')
        for encoded in fields[1:]:
            name, dots = metric_pulse(float(encoded))
            element(metronome, 'beat-unit', name)
            for _ in range(dots):
                element(metronome, 'beat-unit-dot')
    elif kind in ('SFORZANDO', 'SFORZATO', 'SFORZANDO_PIANO'):
        name = {'SFORZANDO': 'sf', 'SFORZATO': 'sfz', 'SFORZANDO_PIANO': 'sfp'}[kind]
        element(element(type_node, 'dynamics'), name)
    else:
        fallback = {'RITARDANDO': 'rit.', 'RALLENTANDO': 'rall.', 'RITENUTO': 'ritenuto',
                    'ACCELERANDO': 'accel.', 'A_TEMPO': 'a tempo', 'TEMPO_PRIMO': 'tempo primo',
                    'SAME_TEMPO': "l'istesso tempo", 'FERMATA': 'fermata', 'BREATH': 'breath', 'CAESURA': 'caesura'}
        printed = next((e['printedText'] for e in mark.get('evidence', []) if e.get('printedText')), fallback.get(kind))
        if printed is None:
            measure.remove(direction)
            return
        element(type_node, 'words', printed)
    element(direction, 'offset', offset)


def emit_note(measure, duration, voice, event=None, chord=False, stop=False, start=False, flats=False,
              gliss_start=False, gliss_stop=False, slide_start=False, slide_stop=False, fragment_end=None, rest_symbols=(), full_measure=False,
              articulation_flags=0, attack_fragment=True, grace=None, bow_direction=None):
    node = element(measure, 'note')
    if grace is not None:
        element(node, 'grace', **{('steal-time-previous' if grace['afterGrace'] else 'steal-time-following'): grace['stealPercent']})
    if chord:
        element(node, 'chord')
    if event is None:
        element(node, 'rest', **({'measure': 'yes'} if full_measure else {}))
    elif event_kind(event) == 'UNPITCHED':
        identity = element(node, 'unpitched')
        element(identity, 'display-step', event['displayStep'])
        element(identity, 'display-octave', event['displayOctave'])
    else:
        midi = event['midi']
        names = (('C', 0), ('D', -1), ('D', 0), ('E', -1), ('E', 0), ('F', 0),
                 ('G', -1), ('G', 0), ('A', -1), ('A', 0), ('B', -1), ('B', 0)) if flats else (
                 ('C', 0), ('C', 1), ('D', 0), ('D', 1), ('E', 0), ('F', 0),
                 ('F', 1), ('G', 0), ('G', 1), ('A', 0), ('A', 1), ('B', 0))
        step, alter = names[midi % 12]
        step, alter, octave = event.get('retainedSpelling') or (step, alter, midi // 12 - 1)
        pitch = element(node, 'pitch')
        element(pitch, 'step', step)
        if alter:
            element(pitch, 'alter', alter)
        element(pitch, 'octave', octave)
    if grace is None:
        element(node, 'duration', duration)
    for active, kind in ((stop, 'stop'), (start, 'start')):
        if active:
            element(node, 'tie', type=kind)
    if event and event.get('durationFallback'):
        element(node, 'footnote', 'Estimated duration from interpretation')
    element(node, 'voice', voice)
    if full_measure:
        element(node, 'type', 'whole')
    else:
        paired = event.get('retainedTremolo') if event else None
        if paired and (grace is not None or stop or start or duration != event['end'] - event['start']):
            raise ValueError('Paired tremolo requires an unsplit original endpoint')
        note_type(node, duration * 2 if paired else duration, event)
    if grace is not None and node.find('type') is None:
        raise ValueError('Grace note has no exact written type')
    if event is not None and event_kind(event) == 'UNPITCHED':
        element(node, 'notehead', 'x')
    effect = event.get('guitarEffect') if event and attack_fragment else None
    ornament_flags = event.get('retainedOrnamentFlags', 0) if event and attack_fragment else 0
    symbols = [mark for mark in (event.get('releaseSymbols', []) if event else rest_symbols)
               if mark['release'] == fragment_end]
    paired = event.get('retainedTremolo') if event else None
    arpeggio = bool(event and event.get('retainedArpeggio') and attack_fragment and grace is None)
    if stop or start or effect or symbols or gliss_start or gliss_stop or slide_start or slide_stop or articulation_flags or ornament_flags or paired or arpeggio or bow_direction is not None:
        notation = element(node, 'notations')
        if arpeggio:
            element(notation, 'arpeggiate')
        if paired and not chord:
            element(element(notation, 'ornaments'), 'tremolo', paired['strokes'], type=paired['type'])
        for active, kind in ((stop, 'stop'), (start, 'start')):
            if active:
                element(notation, 'tied', type=kind)
        for active, kind in ((gliss_stop, 'stop'), (gliss_start, 'start')):
            if active:
                element(notation, 'glissando', type=kind,
                        number=event['gliss_'+kind], **{'line-type':'wavy'})
        for active, kind in ((slide_stop, 'stop'), (slide_start, 'start')):
            if active:
                element(notation, 'slide', type=kind, number=event['slide_'+kind], **{'line-type':'solid'})
        if effect:
            guitar_export.write(notation, effect)
        if articulation_flags:
            articulation_export.write(notation, articulation_flags)
        if bow_direction is not None:
            bow_export.write(notation, bow_direction)
        if ornament_flags:
            ornament_export.write(notation, ornament_flags)
        for symbol in symbols:
            kind = symbol['kind']
            if kind == 'FERMATA':
                element(notation, 'fermata', type='inverted' if symbol.get('inverted') else 'upright')
            else:
                articulations = notation.find('articulations')
                if articulations is None:
                    articulations = element(notation, 'articulations')
                element(articulations, 'caesura' if kind == 'CAESURA' else 'breath-mark',
                        None if kind == 'CAESURA' else symbol.get('value', 'comma'))
    return node


def emit_rest_gap(measure, start, end, voice, symbols, literal_edges=()):
    boundaries = sorted({start, end}
                        | {value for s in symbols for value in (s['start'], s['release']) if start < value < end}
                        | {value for value in literal_edges if start < value < end})
    for a, b in zip(boundaries, boundaries[1:]):
        cursor = a
        for duration in duration_parts(b-a):
            cursor += duration
            emit_note(measure, duration, voice, fragment_end=cursor, rest_symbols=symbols)


def write_musicxml(document, path, *, meter=(4, 4), key_fifths=0, bpm=None):
    """Write uncompressed .musicxml; each decoded staff becomes a concert-pitch part.

    Derived voices, gap rests and enharmonic spellings are reconstructed. Original
    Engraving and guitar string/fret placement are reconstructed only where supported.
    Expressive symbols preserve written evidence; preview holds never lengthen notation.
    """
    from .boundary_ties import resolve_boundary_ties
    for page in document['pages']:
        for event in page['events']:
            event_kind(event)
            validate_unpitched(event)
        for row in page.get('score', {}).get('rests', []):
            if rest_kind(row) == 'FULL_MEASURE':
                full_measure_span(page, row)
    document = resolve_boundary_ties(document)
    from .printed_tempo_spans import resolve_pages, preserve_end, SOURCE, GRADUAL
    document = dict(document, pages=resolve_pages(document['pages']))
    from . import slur_export
    retained_slurs = slur_export.retained_spans(document['pages'], include_candidates=True)
    retained_slur_ties = set()
    requested_slur_owners = {owner for _, a, b in retained_slurs for owner in (a, b)}
    slur_attacks = {}
    meter = tuple(document.get('initialMeter', meter))
    key_fifths = document.get('initialKeyFifths', key_fifths)
    bpm = document.get('initialBpm', 120) if bpm is None else bpm
    if len(meter) != 2 or not all(isinstance(n, int) and 1 <= n <= 32 for n in meter) or meter[1] & (meter[1]-1):
        raise ValueError('Invalid MusicXML initial meter')
    if not isinstance(key_fifths, int) or not -7 <= key_fifths <= 7 or not math.isfinite(bpm) or not 15 <= bpm <= 400:
        raise ValueError('Invalid MusicXML key or tempo')
    bars, events, expressions, full_rests, literal_rests = [], [], [], [], []
    printed_rest_groups = []
    octave_owned = {}
    offset = 0
    current_meter, current_key = meter, key_fifths
    for page_index, page in enumerate(document['pages']):
        score = page['score']
        local_start = 0
        first_bar = len(bars)
        from . import multi_rest_wire
        for group in multi_rest_wire.checked_score(score):
            printed_rest_groups.append(dict(group, firstBar=first_bar+group['firstMeasureIndex']))
        for index, beats in enumerate(page['measureBeats']):
            length = ticks(beats)
            if length <= 0:
                raise ValueError('MusicXML measure duration must be positive')
            for change in score.get('keyChanges', []):
                if change['measureIndex'] == index:
                    current_key = change['fifths']
            for change in score.get('meterChanges', []):
                if change['measureIndex'] == index:
                    current_meter = (change['numerator'], change['denominator'])
            bar = dict(start=offset+local_start, length=length, meter=current_meter, key=current_key,
                       tempos=[c for c in score.get('tempoChanges', []) if c['measureIndex'] == index])
            bars.append(bar)
            local_start += length
        for row in score.get('rests', []):
            if rest_kind(row) == 'FULL_MEASURE':
                bar_index = first_bar+row['measureIndex']
                bar = bars[bar_index]
                full_rests.append(dict(row, barIndex=bar_index, sourcePageIndex=page_index,
                                       start=bar['start'], end=bar['start']+bar['length'],
                                       restIdentity=len(full_rests), releaseSymbols=[]))
        # Literal rest columns retain their written subdivisions in proved silence slots.
        from .performance import _rest_column_span
        for row in score.get('rests', []):
            if rest_kind(row) != 'LITERAL' or not all(key in row for key in
                    ('measureIndex', 'staffIndex', 'staffCount', 'positionInMeasure', 'durationBeats')):
                continue
            m = row['measureIndex']
            if not isinstance(m, int) or not 0 <= m < len(page['measureBeats']):
                continue
            span = _rest_column_span(page, m, row['staffIndex'], row['staffCount'],
                                     row['positionInMeasure'], 0)
            if span is None:
                continue
            bar = bars[first_bar+m]
            start = bar['start']+ticks(span[0]['quarterBeatOffset'])
            end = bar['start']+ticks(span[1]['quarterBeatOffset'])
            if bar['start'] <= start < end <= bar['start']+bar['length']:
                literal_rests.append(dict(row, start=start, end=end))
        first_event = len(events)
        written = grace_export.plan(page, ticks)
        paired_tremolos = tremolo_export.retained(page)
        for note_index, n in enumerate(page['events']):
            if event_kind(n) == 'PITCHED' and (not isinstance(n['midi'], int) or not 0 <= n['midi'] <= 127):
                raise ValueError('MusicXML note pitch must be a MIDI integer in 0..127')
            start, duration = ticks(n['startBeat']), ticks(n['durationBeats'])
            placement = written.get(note_index)
            if placement is not None:
                start, duration = placement['start'], placement['duration']
            is_grace = placement is not None and placement['grace']
            if duration <= 0 or (start if is_grace else start + duration) > local_start + 2:
                raise ValueError('MusicXML note duration lies outside the page timeline')
            measure_index = n.get('measureIndex')
            spelling_key = (bars[first_bar+measure_index]['key']
                            if type(measure_index) is int and 0 <= measure_index < len(page['measureBeats'])
                            else None)
            events.append(dict(n, start=offset+start, end=offset+(start if is_grace else min(local_start,start+duration)), tie_stop=False, tie_start=False,
                               sourceIdentity=len(events), retainedClef=clef_export.retained(page, note_index),
                               retainedArticulations=articulation_export.retained(page, note_index),
                               retainedBow=bow_export.retained(page, note_index),
                               retainedSpelling=spelling_export.retained(page, note_index, spelling_key),
                               retainedOrnamentFlags=ornament_export.retained(page, note_index),
                               retainedTremolo=paired_tremolos.get(note_index), retainedArpeggio=False))
            if is_grace:
                events[-1].update(grace=True, graceOrdinal=placement['ordinal'], afterGrace=placement['after'],
                                  stealPercent=placement['steal'], graceWrittenDuration=duration,
                                  principalSourceIdentity=first_event+placement['principal'])
        from . import technique_export
        expressions.extend(technique_export.plan(page, events[first_event:], bars[first_bar:], f'page:{page_index}'))
        from . import dynamic_export
        expressions.extend(dynamic_export.plan(page, events[first_event:], bars[first_bar:], f'page:{page_index}'))
        for source, target, _ in straight_slide.bindings(page):
            # This global endpoint identity is an internal token, not a MusicXML number.
            token = first_event + target
            events[first_event+source]['slide_start'] = token
            events[first_event+target]['slide_stop'] = token
        from .performance import _column_members, _rest_owned, _rest_span, _rest_identity, _resolve_pedal, _anchor
        def absolute(anchor):
            if anchor is None:
                return None
            m, q = anchor['measureIndex'], anchor['quarterBeatOffset']
            if not isinstance(m, int) or not 0 <= m <= len(page['measureBeats']):
                raise ValueError('Expression anchor outside MusicXML page')
            if not isinstance(q, (int, float)) or not math.isfinite(q) or q < 0 or q > (page['measureBeats'][m] if m < len(page['measureBeats']) else 0):
                raise ValueError('Expression anchor outside MusicXML measure')
            return offset+ticks(sum(page['measureBeats'][:m])+q)
        from .arpeggio_notation import plan as arpeggio_plan
        from .neutral_rest_caption import is_caption, start as caption_start
        for supplied in score.get('expressiveEvents', []):
            if supplied.get('kind') == 'ARPEGGIO':
                for source_index in arpeggio_plan(supplied, page):
                    events[first_event+source_index]['retainedArpeggio'] = True
                continue  # A source-owned note symbol, never a generic words direction.
            mark = _resolve_pedal(dict(supplied), page)
            mark.pop('_neutralRestCaption', None)
            if is_caption(mark):
                mark['start'], mark['end'] = caption_start(mark, page), None
                mark['scope'] = 'PART' if mark['start'] is not None else 'UNRESOLVED'
                mark['_neutralRestCaption'] = mark['start'] is not None
            elif _rest_owned(mark):
                span = _rest_span(mark, page)
                mark['start'], mark['end'] = span if span else (None, None)
                mark['scope'] = 'REST' if span else 'UNRESOLVED'
                if span:
                    m, staff, count, position = _rest_identity(mark)
                    owned_full = [r for r in full_rests if r['sourcePageIndex'] == page_index
                                  and r['measureIndex'] == m and r['staffIndex'] == staff
                                  and r['staffCount'] == count and abs(r['positionInMeasure']-position) <= .018]
                    if len(owned_full) == 1:
                        mark['fullRestIdentity'] = owned_full[0]['restIdentity']
            mark['identity'] = f'page:{page_index}/'+mark['eventId']
            mark['startTick'], mark['endTick'] = absolute(mark.get('start')), absolute(mark.get('end'))
            members = _column_members(mark, page)
            mark['ownerIds'] = [first_event+i for i in members]
            owned = [events[first_event+i] for i in members]
            if owned and all(not n.get('durationFallback', False) and n['start'] == owned[0]['start'] and n['end'] == owned[0]['end'] for n in owned):
                mark['startTick'] = owned[0]['end'] if mark['kind'] in ('BREATH', 'CAESURA') else owned[0]['start']
                local_starts = [0]
                for duration in page['measureBeats']:
                    local_starts.append(local_starts[-1] + duration)
                resolved_start = _anchor((mark['startTick'] - offset) / DIVISIONS, local_starts)
                if mark['kind'] in GRADUAL:
                    mark['endTick'] = absolute(preserve_end(mark, resolved_start, page))
                if mark['kind'] == 'FERMATA':
                    mark['endTick'] = owned[0]['end']
            elif mark.get('scope') == 'UNRESOLVED':
                mark['startTick'] = None
            if mark['startTick'] is not None:
                expressions.append(mark)
        offset += local_start
    if not bars:
        raise ValueError('No interpreted measures to export')
    root = ET.Element('score-partwise', version='4.0')
    element(element(root, 'work'), 'work-title', document.get('inputName', 'Interpreted score'))
    identification = element(root, 'identification')
    element(element(identification, 'encoding'), 'software', 'Music Sheets Interpreter')
    misc = element(identification, 'miscellaneous')
    element(misc, 'miscellaneous-field', 'Concert-pitch reconstruction; recognition and estimated timing require review.', name='interpretation')
    part_list = element(root, 'part-list')
    def physical_staff(row):
        staff = row.get('staffIndex', 0)
        return staff, row.get('staffCount', staff+1)
    if full_rests:
        staffs = sorted({physical_staff(n) for n in events}
                        | {physical_staff(r) for r in full_rests}
                        | {physical_staff(m) for m in expressions if m.get('scope') == 'REST'})
    else:
        # Preserve the existing literal-only reconstruction and part identities.
        staffs = sorted({n.get('staffIndex', 0) for n in events}
                        | {m['staffIndex'] for m in expressions if m.get('scope') == 'REST'}) or [0]
    for identity in staffs:
        staff, count = identity if full_rests else (identity, None)
        same_staff = lambda row: physical_staff(row) == identity if full_rests else row.get('staffIndex', 0) == staff
        ambiguous_index = bool(full_rests) and sum(key[0] == staff for key in staffs) > 1
        part_id = f'P{staff+1}C{count}' if ambiguous_index else f'P{staff+1}'
        name = f'Staff {staff+1} of {count}' if ambiguous_index else f'Staff {staff+1}'
        element(element(part_list, 'score-part', id=part_id), 'part-name', name)
        part = element(root, 'part', id=part_id)
        notes = sorted((n for n in events if same_staff(n)), key=lambda n: (n['start'], notation_order(n)))
        clef_changes = clef_export.plan(notes)
        emitted_clefs = set()
        silent = [r for r in full_rests if same_staff(r)]
        literal_edges = {tick for row in literal_rests if same_staff(row)
                         for tick in (row['start'], row['end'])}
        for index, n in enumerate(notes):
            if n.get('grace', False):
                continue
            gliss = n.get('glissando')
            if gliss is None:
                continue
            if (not isinstance(gliss,dict) or gliss.get('style') != 'white_keys'
                    or type(gliss.get('targetMidi')) is not int or not 0 <= gliss['targetMidi'] <= 127):
                raise ValueError('Unsupported glissando notation')
            targets = [target for target in notes if not target.get('grace', False) and abs(target['start']-n['end']) <= 2]
            if len(targets) == 1 and event_kind(targets[0]) == 'PITCHED' and targets[0]['midi'] == gliss['targetMidi']:
                n['gliss_start'] = index % 16 + 1
                targets[0]['gliss_stop'] = n['gliss_start']
        marks = sorted((m for m in expressions if m.get('scope') == 'SCORE' or same_staff(m)),
                       key=lambda m: (m['startTick'], 0 if m['kind'] == 'PEDAL_UP' else 1))
        dash_numbers = {}
        occupied = []
        for mark in marks:
            if mark['kind'] not in GRADUAL or mark.get('endTick') is None or mark['endTick'] <= mark['startTick'] or not any(e['sourceId'] == SOURCE for e in mark.get('evidence', [])):
                continue
            occupied = [(end, number) for end, number in occupied if end > mark['startTick']]
            number = next((n for n in range(1, 17) if n not in {number for _, number in occupied}), None)
            if number is None:
                raise ValueError('Too many overlapping printed tempo extensions')
            dash_numbers[mark['identity']] = number
            occupied.append((mark['endTick'], number))
        rendered, rest_symbols = set(), []
        for mark in marks:
            if mark['kind'] not in ('FERMATA', 'BREATH', 'CAESURA'):
                continue
            release = mark['endTick'] if mark['kind'] == 'FERMATA' else mark['startTick']
            if release is None:
                continue
            symbol = dict(kind=mark['kind'], start=mark['startTick'], release=release,
                          value='tick' if 'tick' in mark.get('qualifierText', '') else 'comma',
                          inverted=mark.get('qualifierText') == 'fermata inverted')
            if mark.get('scope') == 'REST' and mark['kind'] == 'FERMATA':
                owned = [r for r in silent if r['restIdentity'] == mark.get('fullRestIdentity')]
                if owned:
                    owned[0]['releaseSymbols'].append(symbol)
                    rendered.add(mark['identity'])
                elif release > mark['startTick'] and not any(n['start'] < release and n['end'] > mark['startTick'] for n in notes):
                    rest_symbols.append(symbol)
                    rendered.add(mark['identity'])
                continue
            eligible = [n for n in notes if not n.get('grace', False) and n['end'] == release and not n.get('durationFallback', False)
                        and (not mark['ownerIds'] or n['sourceIdentity'] in mark['ownerIds'])]
            if eligible:
                selected = (min if symbol['inverted'] else max)(eligible, key=(
                    (lambda n: display_position(n)) if any(event_kind(n) == 'UNPITCHED' for n in eligible)
                    else (lambda n: n['midi'])))
                selected.setdefault('releaseSymbols', []).append(symbol)
                rendered.add(mark['identity'])
        previous = {}
        for n in notes:
            if n.get('grace', False) or event_kind(n) == 'UNPITCHED':
                continue
            prior = previous.get(n['midi'])
            if n.get('tiedFromPrevious') and prior and abs(prior['end']-n['start']) <= 2:
                prior['tie_start'] = True
                n['tie_stop'] = True
                retained_slur_ties.add((prior['sourceIdentity'], n['sourceIdentity']))
            previous[n['midi']] = n
        groups = defaultdict(list)
        for n in notes:
            if not n.get('grace', False):
                groups[n['start'], n['end'], 0 if event_kind(n) == 'UNPITCHED' else n.get('octaveShift', 0)].append(n)
        voice_ends = []
        for (start, end, octave_shift), chord in sorted(groups.items()):
            voice = next((i for i, at in enumerate(voice_ends) if at <= start), len(voice_ends))
            if voice == len(voice_ends):
                voice_ends.append(end)
            else:
                voice_ends[voice] = end
            for n in chord:
                n['voice'] = voice + 1
        by_source = {n['sourceIdentity']: n for n in notes}
        grace_groups = defaultdict(list)
        for n in notes:
            if n.get('grace', False):
                owner = by_source.get(n['principalSourceIdentity'])
                if owner is None or owner.get('grace', False):
                    raise ValueError('Grace principal is outside exported part')
                n['voice'] = owner['voice']
                grace_groups[n['principalSourceIdentity'], n['afterGrace']].append(n)
        def emit_graces(measure, chord, after, anchor, flats):
            pending = [g for n in chord if anchor == (n['end'] if after else n['start'])
                       for g in grace_groups.get((n['sourceIdentity'], after), ())]
            for g in sorted(pending, key=lambda g: (g['graceOrdinal'], g['sourceIdentity'])):
                emitted = emit_note(measure, g['graceWrittenDuration'], g['voice'], g, flats=flats,
                                    articulation_flags=g['retainedArticulations'], grace=g, bow_direction=g.get('retainedBow'))
                octave_owned[emitted] = 0 if event_kind(g) == 'UNPITCHED' else g.get('octaveShift', 0)
                if g['sourceIdentity'] in requested_slur_owners:
                    slur_attacks[g['sourceIdentity']] = emitted
        for index, bar in enumerate(bars):
            measure = element(part, 'measure', number=index+1)
            attributes = element(measure, 'attributes')
            element(attributes, 'divisions', DIVISIONS)
            for group in printed_rest_groups:
                if group['firstBar'] == index and same_staff(group):
                    element(element(attributes, 'measure-style'), 'multiple-rest', group['measureCount'])
            element(element(attributes, 'key'), 'fifths', bar['key'])
            time = element(attributes, 'time')
            numerator, denominator = bar['meter']
            if ticks(numerator*4/denominator) != bar['length']:
                # Retain actual timeline for pickups or unusual inferred bars.
                measure.set('implicit', 'yes')
            element(time, 'beats', numerator)
            element(time, 'beat-type', denominator)
            if bar['start'] in clef_changes:
                clef_export.write(attributes, clef_changes[bar['start']])
                emitted_clefs.add(bar['start'])
            tempos = list(bar['tempos'])
            if index == 0 and not any(t['positionInMeasure'] == 0 for t in tempos):
                tempos.insert(0, dict(bpm=bpm, positionInMeasure=0))
            for tempo in tempos:
                unit, dots, marked_bpm = tempo_mark(tempo)
                direction = element(measure, 'direction', placement='above')
                metronome = element(element(direction, 'direction-type'), 'metronome')
                element(metronome, 'beat-unit', unit)
                for _ in range(dots):
                    element(metronome, 'beat-unit-dot')
                element(metronome, 'per-minute', format(Decimal(str(marked_bpm)).normalize(), 'f'))
                element(direction, 'offset', round(bar['length']*tempo['positionInMeasure']))
                element(direction, 'sound', tempo=str(tempo['bpm']))
            a, b = bar['start'], bar['start']+bar['length']
            for mark in marks:
                if mark.get('_absoluteDynamic'):
                    owns = mark['_absoluteDynamicBarStart'] == a and a <= mark['startTick'] <= b
                elif mark.get('_playingInstruction'):
                    # A terminal after-grace still belongs to its original written bar.
                    owns = mark['_playingInstructionBarStart'] == a and a <= mark['startTick'] <= b
                else:
                    owns = (a <= mark['startTick'] < b
                            or index == len(bars)-1 and mark['kind'] == 'PEDAL_UP' and mark['startTick'] == b)
                if mark['identity'] not in rendered and owns:
                    expression_direction(measure, mark, mark['startTick']-a)
                    if mark['identity'] in dash_numbers:
                        dash = element(measure, 'direction', placement='above')
                        element(element(dash, 'direction-type'), 'dashes', type='start', number=str(dash_numbers[mark['identity']]))
                        element(dash, 'offset', mark['startTick']-a)
                if mark['identity'] in dash_numbers and (a <= mark['endTick'] < b or index == len(bars)-1 and mark['endTick'] == b):
                    dash = element(measure, 'direction', placement='above')
                    element(element(dash, 'direction-type'), 'dashes', type='stop', number=str(dash_numbers[mark['identity']]))
                    element(dash, 'offset', mark['endTick']-a)
            present = [n for n in notes if not n.get('grace', False) and n['start'] < b and n['end'] > a]
            bar_rests = [r for r in silent if r['barIndex'] == index]
            voices = sorted({n['voice'] for n in present}) or ([] if bar_rests else [1])
            rest_voices = {max(1000, len(voice_ends))+i+1: row for i, row in enumerate(bar_rests)}
            voices.extend(rest_voices)
            for vi, voice in enumerate(voices):
                if vi:
                    element(element(measure, 'backup'), 'duration', bar['length'])
                if voice in rest_voices:
                    row = rest_voices[voice]
                    emit_note(measure, bar['length'], voice, fragment_end=b,
                              rest_symbols=row['releaseSymbols'], full_measure=True)
                    continue
                cursor = a
                segments = defaultdict(list)
                for n in present:
                    if n['voice'] == voice:
                        segments[max(a,n['start']),min(b,n['end'])].append(n)
                for (start, end), chord in sorted(segments.items()):
                    if start > cursor:
                        emit_rest_gap(measure, cursor, start, voice, rest_symbols, literal_edges)
                    fragment_start = start
                    for duration in duration_parts(end-start, chord):
                        fragment_end = fragment_start + duration
                        if any(fragment_start == n['start'] for n in chord) and start in clef_changes and start not in emitted_clefs:
                            clef_export.write(element(measure, 'attributes'), clef_changes[start])
                            emitted_clefs.add(start)
                        emit_graces(measure, chord, False, fragment_start, bar['key']<0)
                        articulation_flags = articulation_export.chord_marks(chord, fragment_start)
                        bow_directions = bow_export.chord_marks(chord, fragment_start)
                        for ci, n in enumerate(chord):
                            emitted = emit_note(measure, duration, voice, n, chord=ci>0,
                                      stop=fragment_start > n['start'] or n['tie_stop'],
                                      start=fragment_end < n['end'] or n['tie_start'], flats=bar['key']<0,
                                      gliss_start=fragment_end==n['end'] and 'gliss_start' in n,
                                      gliss_stop=fragment_start==n['start'] and 'gliss_stop' in n,
                                      slide_start=fragment_end==n['end'] and 'slide_start' in n,
                                      slide_stop=fragment_start==n['start'] and 'slide_stop' in n,
                                      fragment_end=fragment_end, articulation_flags=articulation_flags[ci],
                                      attack_fragment=fragment_start==n['start'], bow_direction=bow_directions[ci])
                            octave_owned[emitted] = 0 if event_kind(n) == 'UNPITCHED' else n.get('octaveShift', 0)
                            if fragment_start == n['start'] and n['sourceIdentity'] in requested_slur_owners:
                                slur_attacks[n['sourceIdentity']] = emitted
                        emit_graces(measure, chord, True, fragment_end, bar['key']<0)
                        fragment_start = fragment_end
                    cursor = end
                if cursor < b:
                    emit_rest_gap(measure, cursor, b, voice, rest_symbols, literal_edges)
    from .octave_shift_export import apply as octave_apply
    octave_apply(root, octave_owned)
    first_score = document['pages'][0].get('score', {})
    known_meter = 'initialMeter' in document or any(
        c.get('measureIndex') == 0 for c in first_score.get('meterChanges', []))
    known_key = 'initialKeyFifths' in document or any(
        c.get('measureIndex') == 0 for c in first_score.get('keyChanges', []))
    part_nodes = {note: part for part in root.findall('part') for note in part.iter('note')}
    exported_slur_parts = {owner: part_nodes.get(note) for owner, note in slur_attacks.items()}
    retained_slurs = slur_export.classify(retained_slurs, document['pages'], events,
                                        retained_slur_ties, known_meter and known_key,
                                        exported_slur_parts)
    slur_export.apply(root, retained_slurs, slur_attacks, events)
    slide_numbering.assign(root)
    ET.indent(root)
    ET.ElementTree(root).write(Path(path), encoding='utf-8', xml_declaration=True)
