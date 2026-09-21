# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""MusicXML 4.0 concert-pitch export of the decoded performance (not source engraving)."""
from collections import defaultdict
import math
from pathlib import Path
import xml.etree.ElementTree as ET

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


def note_type(node, duration):
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


def emit_note(measure, duration, voice, event=None, chord=False, stop=False, start=False, flats=False):
    node = element(measure, 'note')
    if chord:
        element(node, 'chord')
    if event is None:
        element(node, 'rest')
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
    note_type(node, duration)
    effect = event.get('guitarEffect') if event else None
    if stop or start or effect:
        notation = element(node, 'notations')
        for active, kind in ((stop, 'stop'), (start, 'start')):
            if active:
                element(notation, 'tied', type=kind)
        if effect:
            # Preserve unsupported performance details explicitly without inventing endpoints/string numbers.
            description = effect.get('type', 'none')
            if effect.get('semitones'):
                description += f" ({effect['semitones']:+g} semitones)"
            if effect.get('vibrato'):
                description += ' vibrato'
            element(notation, 'other-notation', description, type='single')


def write_musicxml(document, path, *, meter=(4, 4), key_fifths=0, bpm=None):
    """Write uncompressed .musicxml; each decoded staff becomes a concert-pitch part.

    Derived voices, gap rests and enharmonic spellings are reconstructed. Original
    engraving, guitar string/fret placement and expressive playback are not reconstructed.
    """
    meter = tuple(document.get('initialMeter', meter))
    key_fifths = document.get('initialKeyFifths', key_fifths)
    bpm = document.get('initialBpm', 120) if bpm is None else bpm
    if len(meter) != 2 or not all(isinstance(n, int) and 1 <= n <= 32 for n in meter) or meter[1] & (meter[1]-1):
        raise ValueError('Invalid MusicXML initial meter')
    if not isinstance(key_fifths, int) or not -7 <= key_fifths <= 7 or not math.isfinite(bpm) or not 15 <= bpm <= 400:
        raise ValueError('Invalid MusicXML key or tempo')
    bars, events = [], []
    offset = 0
    current_meter, current_key = meter, key_fifths
    for page in document['pages']:
        score = page['score']
        local_start = 0
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
        for n in page['events']:
            if not isinstance(n['midi'], int) or not 0 <= n['midi'] <= 127:
                raise ValueError('MusicXML note pitch must be a MIDI integer in 0..127')
            start, duration = ticks(n['startBeat']), ticks(n['durationBeats'])
            if duration <= 0 or start + duration > local_start + 2:
                raise ValueError('MusicXML note duration lies outside the page timeline')
            events.append(dict(n, start=offset+start, end=offset+min(local_start,start+duration), tie_stop=False, tie_start=False))
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
    staffs = sorted({n.get('staffIndex', 0) for n in events}) or [0]
    for staff in staffs:
        part_id = f'P{staff+1}'
        element(element(part_list, 'score-part', id=part_id), 'part-name', f'Staff {staff+1}')
        part = element(root, 'part', id=part_id)
        notes = sorted((n for n in events if n.get('staffIndex', 0) == staff), key=lambda n: (n['start'], n['midi']))
        previous = {}
        for n in notes:
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
                bass = notes and sum(n['midi'] for n in notes)/len(notes) < 60
                element(clef, 'sign', 'F' if bass else 'G')
                element(clef, 'line', 4 if bass else 2)
            tempos = ([dict(bpm=bpm, positionInMeasure=0)] if index == 0 else []) + bar['tempos']
            for tempo in tempos:
                direction = element(measure, 'direction')
                metronome = element(element(direction, 'direction-type'), 'metronome')
                element(metronome, 'beat-unit', 'quarter')
                element(metronome, 'per-minute', tempo['bpm'])
                element(direction, 'offset', round(bar['length']*tempo['positionInMeasure']))
                element(direction, 'sound', tempo=str(tempo['bpm']))
            a, b = bar['start'], bar['start']+bar['length']
            present = [n for n in notes if n['start'] < b and n['end'] > a]
            voices = sorted({n['voice'] for n in present}) or [1]
            for vi, voice in enumerate(voices):
                if vi:
                    element(element(measure, 'backup'), 'duration', bar['length'])
                cursor = a
                segments = defaultdict(list)
                for n in present:
                    if n['voice'] == voice:
                        segments[max(a,n['start']),min(b,n['end'])].append(n)
                for (start, end), chord in sorted(segments.items()):
                    if start > cursor:
                        emit_note(measure, start-cursor, voice)
                    for ci, n in enumerate(chord):
                        emit_note(measure, end-start, voice, n, chord=ci>0,
                                  stop=n['start']<a or n['tie_stop'], start=n['end']>b or n['tie_start'], flats=bar['key']<0)
                    cursor = end
                if cursor < b:
                    emit_note(measure, b-cursor, voice)
    ET.indent(root)
    ET.ElementTree(root).write(Path(path), encoding='utf-8', xml_declaration=True)
