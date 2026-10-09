# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""MusicXML 4.0 concert-pitch export of the decoded performance (not source engraving)."""
from collections import defaultdict
import math
from pathlib import Path
import xml.etree.ElementTree as ET
from .typed_events import event_kind, validate_unpitched, notation_order, display_position
from .rest_kinds import rest_kind, full_measure_span

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
    if (type(actual) is not int or type(normal) is not int or actual not in (1,3,5,6,7)
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


def metric_pulse(value):
    for i, name in enumerate(('whole', 'half', 'quarter', 'eighth', '16th', '32nd')):
        for dots, scale in enumerate((1, 1.5, 1.75)):
            if value == math.ldexp(4, -i)*scale:
                return name, dots
    raise ValueError('Unsupported MusicXML metric pulse')


def expression_direction(measure, mark, offset):
    kind = mark['kind']
    direction = element(measure, 'direction', placement='below' if kind in ('PEDAL_DOWN', 'PEDAL_UP') else 'above')
    type_node = element(direction, 'direction-type')
    if kind in ('PEDAL_DOWN', 'PEDAL_UP'):
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
              gliss_start=False, gliss_stop=False, fragment_end=None, rest_symbols=(), full_measure=False):
    node = element(measure, 'note')
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
        pitch = element(node, 'pitch')
        element(pitch, 'step', step)
        if alter:
            element(pitch, 'alter', alter)
        element(pitch, 'octave', midi // 12 - 1)
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
        note_type(node, duration, event)
    if event is not None and event_kind(event) == 'UNPITCHED':
        element(node, 'notehead', 'x')
    effect = event.get('guitarEffect') if event else None
    symbols = [mark for mark in (event.get('releaseSymbols', []) if event else rest_symbols)
               if mark['release'] == fragment_end]
    if stop or start or effect or symbols or gliss_start or gliss_stop:
        notation = element(node, 'notations')
        for active, kind in ((stop, 'stop'), (start, 'start')):
            if active:
                element(notation, 'tied', type=kind)
        for active, kind in ((gliss_stop, 'stop'), (gliss_start, 'start')):
            if active:
                element(notation, 'glissando', type=kind,
                        number=event['gliss_'+kind], **{'line-type':'wavy'})
        if effect:
            # Preserve unsupported performance details explicitly without inventing endpoints/string numbers.
            description = effect.get('type', 'none')
            if effect.get('semitones'):
                description += f" ({effect['semitones']:+g} semitones)"
            if effect.get('vibrato'):
                description += ' vibrato'
            element(notation, 'other-notation', description, type='single')
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


def emit_rest_gap(measure, start, end, voice, symbols):
    boundaries = sorted({start, end} | {value for s in symbols for value in (s['start'], s['release']) if start < value < end})
    for a, b in zip(boundaries, boundaries[1:]):
        emit_note(measure, b-a, voice, fragment_end=b, rest_symbols=symbols)


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
    meter = tuple(document.get('initialMeter', meter))
    key_fifths = document.get('initialKeyFifths', key_fifths)
    bpm = document.get('initialBpm', 120) if bpm is None else bpm
    if len(meter) != 2 or not all(isinstance(n, int) and 1 <= n <= 32 for n in meter) or meter[1] & (meter[1]-1):
        raise ValueError('Invalid MusicXML initial meter')
    if not isinstance(key_fifths, int) or not -7 <= key_fifths <= 7 or not math.isfinite(bpm) or not 15 <= bpm <= 400:
        raise ValueError('Invalid MusicXML key or tempo')
    bars, events, expressions, full_rests = [], [], [], []
    offset = 0
    current_meter, current_key = meter, key_fifths
    for page_index, page in enumerate(document['pages']):
        score = page['score']
        local_start = 0
        first_bar = len(bars)
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
        first_event = len(events)
        for n in page['events']:
            if event_kind(n) == 'PITCHED' and (not isinstance(n['midi'], int) or not 0 <= n['midi'] <= 127):
                raise ValueError('MusicXML note pitch must be a MIDI integer in 0..127')
            start, duration = ticks(n['startBeat']), ticks(n['durationBeats'])
            if duration <= 0 or start + duration > local_start + 2:
                raise ValueError('MusicXML note duration lies outside the page timeline')
            events.append(dict(n, start=offset+start, end=offset+min(local_start,start+duration), tie_stop=False, tie_start=False,
                               sourceIdentity=len(events)))
        from .performance import _column_members, _rest_owned, _rest_span, _rest_identity, _resolve_pedal
        def absolute(anchor):
            if anchor is None:
                return None
            m, q = anchor['measureIndex'], anchor['quarterBeatOffset']
            if not isinstance(m, int) or not 0 <= m <= len(page['measureBeats']):
                raise ValueError('Expression anchor outside MusicXML page')
            if not isinstance(q, (int, float)) or not math.isfinite(q) or q < 0 or q > (page['measureBeats'][m] if m < len(page['measureBeats']) else 0):
                raise ValueError('Expression anchor outside MusicXML measure')
            return offset+ticks(sum(page['measureBeats'][:m])+q)
        for supplied in score.get('expressiveEvents', []):
            mark = _resolve_pedal(dict(supplied), page)
            if _rest_owned(mark):
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
        silent = [r for r in full_rests if same_staff(r)]
        for index, n in enumerate(notes):
            gliss = n.get('glissando')
            if gliss is None:
                continue
            if (not isinstance(gliss,dict) or gliss.get('style') != 'white_keys'
                    or type(gliss.get('targetMidi')) is not int or not 0 <= gliss['targetMidi'] <= 127):
                raise ValueError('Unsupported glissando notation')
            targets = [target for target in notes if abs(target['start']-n['end']) <= 2]
            if len(targets) == 1 and event_kind(targets[0]) == 'PITCHED' and targets[0]['midi'] == gliss['targetMidi']:
                n['gliss_start'] = index % 16 + 1
                targets[0]['gliss_stop'] = n['gliss_start']
        marks = sorted((m for m in expressions if m.get('scope') == 'SCORE' or same_staff(m)),
                       key=lambda m: (m['startTick'], 0 if m['kind'] == 'PEDAL_UP' else 1))
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
            eligible = [n for n in notes if n['end'] == release and not n.get('durationFallback', False)
                        and (not mark['ownerIds'] or n['sourceIdentity'] in mark['ownerIds'])]
            if eligible:
                selected = (min if symbol['inverted'] else max)(eligible, key=(
                    (lambda n: display_position(n)) if any(event_kind(n) == 'UNPITCHED' for n in eligible)
                    else (lambda n: n['midi'])))
                selected.setdefault('releaseSymbols', []).append(symbol)
                rendered.add(mark['identity'])
        previous = {}
        for n in notes:
            if event_kind(n) == 'UNPITCHED':
                continue
            prior = previous.get(n['midi'])
            if n.get('tiedFromPrevious') and prior and abs(prior['end']-n['start']) <= 2:
                prior['tie_start'] = True
                n['tie_stop'] = True
            previous[n['midi']] = n
        groups = defaultdict(list)
        for n in notes:
            groups[n['start'], n['end']].append(n)
        voice_ends = []
        for (start, end), chord in sorted(groups.items()):
            voice = next((i for i, at in enumerate(voice_ends) if at <= start), len(voice_ends))
            if voice == len(voice_ends):
                voice_ends.append(end)
            else:
                voice_ends[voice] = end
            for n in chord:
                n['voice'] = voice + 1
        for index, bar in enumerate(bars):
            measure = element(part, 'measure', number=index+1)
            attributes = element(measure, 'attributes')
            element(attributes, 'divisions', DIVISIONS)
            element(element(attributes, 'key'), 'fifths', bar['key'])
            time = element(attributes, 'time')
            numerator, denominator = bar['meter']
            if ticks(numerator*4/denominator) != bar['length']:
                # Retain actual timeline for pickups or unusual inferred bars.
                measure.set('implicit', 'yes')
            element(time, 'beats', numerator)
            element(time, 'beat-type', denominator)
            if index == 0:
                clef = element(attributes, 'clef')
                pitched = [n for n in notes if event_kind(n) == 'PITCHED']
                bass = (sum(n['midi'] for n in pitched)/len(pitched) < 60 if pitched
                        else bool(notes) and notes[0].get('clefBottomDiatonic') == 18)
                sign, line = ('F', 4) if bass else ('G', 2)
                bottom = notes[0].get('clefBottomDiatonic', 30) if notes and not pitched else None
                if bottom == 24:
                    sign, line = 'C', 3
                elif bottom == 22:
                    sign, line = 'C', 4
                element(clef, 'sign', sign)
                element(clef, 'line', line)
                if bottom == 37:
                    element(clef, 'clef-octave-change', 1)
            tempos = ([dict(bpm=bpm, positionInMeasure=0)] if index == 0 else []) + bar['tempos']
            for tempo in tempos:
                direction = element(measure, 'direction')
                metronome = element(element(direction, 'direction-type'), 'metronome')
                element(metronome, 'beat-unit', 'quarter')
                element(metronome, 'per-minute', tempo['bpm'])
                element(direction, 'offset', round(bar['length']*tempo['positionInMeasure']))
                element(direction, 'sound', tempo=str(tempo['bpm']))
            a, b = bar['start'], bar['start']+bar['length']
            for mark in marks:
                if mark['identity'] not in rendered and (a <= mark['startTick'] < b
                        or index == len(bars)-1 and mark['kind'] == 'PEDAL_UP' and mark['startTick'] == b):
                    expression_direction(measure, mark, mark['startTick']-a)
            present = [n for n in notes if n['start'] < b and n['end'] > a]
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
                        emit_rest_gap(measure, cursor, start, voice, rest_symbols)
                    for ci, n in enumerate(chord):
                        emit_note(measure, end-start, voice, n, chord=ci>0,
                                  stop=n['start']<a or n['tie_stop'], start=n['end']>b or n['tie_start'], flats=bar['key']<0,
                                  gliss_start=end==n['end'] and 'gliss_start' in n,
                                  gliss_stop=start==n['start'] and 'gliss_stop' in n, fragment_end=end)
                    cursor = end
                if cursor < b:
                    emit_rest_gap(measure, cursor, b, voice, rest_symbols)
    ET.indent(root)
    ET.ElementTree(root).write(Path(path), encoding='utf-8', xml_declaration=True)
