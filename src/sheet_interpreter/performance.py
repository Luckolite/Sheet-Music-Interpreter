# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Expressive previews through the shared Java policy, preserving the written document."""
import copy
import json
import math
import struct
import subprocess
import tempfile
from pathlib import Path
from .runtime import java_executable
from .printed_tempo_spans import preserve_end, resolve_pages, has_open
from .semantic_wire import encode
from .typed_events import event_kind, validate_unpitched
from .rest_kinds import rest_kind, full_measure_span
from .tremolo_performance import source_spans, pulse_windows

SUPPORTED = {'RITARDANDO', 'RALLENTANDO', 'RITENUTO', 'ACCELERANDO', 'A_TEMPO',
             'TEMPO_PRIMO', 'SAME_TEMPO', 'FERMATA', 'BREATH', 'CAESURA',
             'SFORZANDO', 'SFORZATO', 'SFORZANDO_PIANO', 'METRIC_MODULATION',
             'PEDAL_DOWN', 'PEDAL_UP'}


def _bridge(data):
    if len(data) > 64*1024*1024:
        raise ValueError('Expressive performance request too large')
    jar = Path(__file__).with_name('interpreter.jar')
    with tempfile.TemporaryDirectory(prefix='sheet-expression-') as directory:
        request, output = Path(directory)/'request.expr', Path(directory)/'performance.json'
        request.write_bytes(data)
        result = subprocess.run([java_executable(), '-Xmx512m', '-cp', str(jar),
            'io.github.luckolite.interpreter.PerformanceBridge', str(request), str(output)],
            capture_output=True, text=True, encoding='utf-8', timeout=60)
        if result.returncode:
            raise ValueError('Expressive engine rejected decoded input: '+result.stderr.strip())
        return json.loads(output.read_text(encoding='utf-8'))


def _text(value):
    raw = value.encode('utf-8')
    if not raw or len(raw) > 4096:
        raise ValueError('Invalid expressive identity')
    return struct.pack('>i', len(raw))+raw


def _anchor(beat, starts):
    for index in range(len(starts)-1):
        if beat < starts[index+1]-1e-8:
            return dict(measureIndex=index, quarterBeatOffset=max(0, beat-starts[index]))
    if not math.isclose(beat, starts[-1], abs_tol=1e-7):
        raise ValueError('Expressive anchor outside page')
    return dict(measureIndex=len(starts)-1, quarterBeatOffset=0)


def _column_members(expression, page):
    from . import cue_caption
    if cue_caption.owns(expression):
        return cue_caption.members(expression, page)
    if expression.get('kind') == 'ARPEGGIO':
        from .arpeggio_notation import target_indices
        return target_indices(expression, page)
    target = expression.get('targetEventId') or ''
    sources = {'fermata-raw-ink'} if expression['kind'] == 'FERMATA' else {'printed-expression-word', 'printed-expression-symbol'}
    prefix = 'printed-attack:' if expression['kind'] == 'FERMATA' else 'expression-column:'
    if not target.startswith(prefix) or not any(e['sourceId'] in sources for e in expression['evidence']):
        return []
    try:
        m, staff, count, bits = map(int, target[len(prefix):].split(':'))
        position = struct.unpack('>f', struct.pack('>I', bits & 0xffffffff))[0]
    except (ValueError, struct.error):
        return []
    if not math.isfinite(position) or not 0 <= position <= 1:
        return []
    if expression['kind'] in ('PEDAL_DOWN', 'PEDAL_UP') and (
            staff != expression['staffIndex'] or count != expression['staffCount']
            or any(not -2147483648 <= value <= 2147483647 for value in (m, staff, count, bits))
            or m < 0):
        return []
    notes = page.get('score', {}).get('notes', [])
    # Main exports each decoded head in the same order. Refuse any missing correspondence.
    if len(notes) != len(page['events']):
        return []
    return [index for index, note in enumerate(notes) if note['measureIndex'] == m
            and note['staffIndex'] == staff and note['staffCount'] == count
            and abs(note['positionInMeasure']-position) <= .018]


def _rest_owned(expression):
    return expression['kind'] == 'FERMATA' and (expression.get('targetEventId') or '').startswith('printed-rest:') \
        and any(e['sourceId'] == 'fermata-rest-raw-ink' for e in expression['evidence'])


def _rest_identity(expression):
    if not _rest_owned(expression):
        return None
    try:
        m, staff, count, bits = map(int, expression['targetEventId'][len('printed-rest:'):].split(':'))
        position = struct.unpack('>f', struct.pack('>I', bits & 0xffffffff))[0]
    except (ValueError, struct.error):
        return None
    if not math.isfinite(position) or not 0 <= position <= 1 \
            or staff != expression['staffIndex'] or count != expression['staffCount']:
        return None
    return m, staff, count, position


def _rest_span(expression, page):
    """Owned full silent voice, or the same exact literal-slot proof as the Java detector."""
    identity = _rest_identity(expression)
    if identity is None:
        return None
    m, staff, count, position = identity
    beats = page.get('measureBeats')
    if not isinstance(beats, (list, tuple)) or not 0 <= m < len(beats):
        # The typed full-rest resolver diagnoses missing performed spans below.
        if not any(rest_kind(r) == 'FULL_MEASURE' and r.get('measureIndex') == m
                   and r.get('staffIndex') == staff and r.get('staffCount') == count
                   for r in page.get('score', {}).get('rests', [])):
            return None
    return _rest_column_span(page, m, staff, count, position, .018)


def _rest_column_span(page, m, staff, count, position, tolerance):
    belongs = lambda row: row['measureIndex'] == m and row['staffIndex'] == staff and row['staffCount'] == count
    rests = [r for r in page.get('score', {}).get('rests', []) if belongs(r)]
    for row in rests:
        if rest_kind(row) == 'FULL_MEASURE':
            full_measure_span(page, row)
    targets = [r for r in rests if abs(r['positionInMeasure']-position) <= tolerance]
    if len(targets) != 1:
        return None
    target = targets[0]
    if rest_kind(target) == 'FULL_MEASURE':
        beats = full_measure_span(page, target)
        return dict(measureIndex=m, quarterBeatOffset=0), dict(measureIndex=m, quarterBeatOffset=beats)
    raw = page.get('score', {}).get('notes', [])
    if len(raw) != len(page['events']):
        return None
    duration = target['durationBeats']; beats = page['measureBeats'][m]
    if not math.isfinite(duration) or not 0 < duration <= beats:
        return None
    columns = [(r, n) for r, n in zip(raw, page['events']) if belongs(r)]
    if any(abs(r['positionInMeasure']-position) <= tolerance or n.get('durationFallback', False) for r, n in columns):
        return None
    before = max((r['positionInMeasure'] for r, _ in columns if r['positionInMeasure'] < position), default=-1)
    after = min((r['positionInMeasure'] for r, _ in columns if r['positionInMeasure'] > position), default=2)
    bar_start = sum(page['measureBeats'][:m])
    start = max((n['startBeat']+n['durationBeats']-bar_start for r, n in columns if r['positionInMeasure'] == before), default=0)
    end = min((n['startBeat']-bar_start for r, n in columns if r['positionInMeasure'] == after), default=beats)
    slot = [r for r in rests if rest_kind(r) == 'LITERAL' and before < r['positionInMeasure'] < after]
    if any(not math.isfinite(r['durationBeats']) or r['durationBeats'] <= 0 for r in slot) \
            or not math.isclose(end-start, sum(r['durationBeats'] for r in slot), abs_tol=1e-7):
        return None
    if any(n['startBeat']-bar_start < end-1e-7 and n['startBeat']+n['durationBeats']-bar_start > start+1e-7 for _, n in columns):
        return None
    onset = start+sum(r['durationBeats'] for r in slot if r['positionInMeasure'] < position)
    return dict(measureIndex=m, quarterBeatOffset=onset), dict(measureIndex=m, quarterBeatOffset=onset+duration)


def _pedal_span(expression, page):
    """Resolve persisted hook columns using the final exported musical clock, never page spacing."""
    target = expression.get('targetEventId') or ''
    single = target.startswith('pedal-glyph-column-v1:')
    if expression['kind'] not in ('PEDAL_DOWN', 'PEDAL_UP') or not (single or target.startswith('pedal-hooks-v1:')):
        return None
    try:
        prefix = 'pedal-glyph-column-v1:' if single else 'pedal-hooks-v1:'
        fields = list(map(int, target[len(prefix):].split(':')))
        if len(fields) != (6 if single else 10) or any(not -2147483648 <= value <= 2147483647 for value in fields):
            return None
        staff, count = fields[-2:]
        if staff < 0 or count <= staff or staff != expression['staffIndex'] or count != expression['staffCount']:
            return None
        if not any(e['sourceId'] == ('printed-pedal-glyph' if single else 'printed-pedal-bracket') and e['staffIndex'] == staff
                   and e['staffCount'] == count for e in expression.get('evidence', [])):
            return None
        anchors = []
        raw = page.get('score', {}).get('notes', [])
        starts = [0.]
        for duration in page['measureBeats']:
            starts.append(starts[-1]+duration)
        for m, kind, bits, tolerance_bits in ((fields[:4], fields[:4]) if single else (fields[:4], fields[4:8])):
            position, tolerance = (struct.unpack('>f', struct.pack('>I', v & 0xffffffff))[0]
                                   for v in (bits, tolerance_bits))
            if not 0 <= m <= len(page['measureBeats']) or kind not in (0, 1, 2) \
                    or not math.isfinite(position) or not 0 <= position <= 1 \
                    or not math.isfinite(tolerance) or not 0 <= tolerance <= .100000002:
                return None
            if kind == 0:
                anchors.append(dict(measureIndex=m, quarterBeatOffset=0))
                continue
            if m == len(page['measureBeats']) or len(raw) != len(page['events']):
                return None
            if kind == 2:
                span = _rest_column_span(page, m, staff, count, position, max(tolerance, .018))
                if span is None:
                    return None
                anchors.append(span[0])
                continue
            notes = [n for r, n in zip(raw, page['events']) if r['measureIndex'] == m
                     and r['staffIndex'] == staff and r['staffCount'] == count
                     and not r.get('articulations', 0) & (1 << 15)
                     and abs(r['positionInMeasure']-position) <= tolerance]
            if not notes or any(n.get('durationFallback', False) or not math.isfinite(n['startBeat'])
                                or not math.isclose(n['startBeat'], notes[0]['startBeat'], abs_tol=.001) for n in notes):
                return None
            beat = notes[0]['startBeat']
            if not starts[m] <= beat <= starts[m+1]:
                return None
            anchors.append(_anchor(beat, starts))
        if not single and (anchors[0]['measureIndex'], anchors[0]['quarterBeatOffset']) >= \
                (anchors[1]['measureIndex'], anchors[1]['quarterBeatOffset']):
            return None
        if single and expression['kind'] == 'PEDAL_DOWN' and anchors[0]['measureIndex'] == len(page['measureBeats']):
            return None
        return tuple(anchors)
    except (ValueError, struct.error, KeyError, TypeError):
        return None


def _resolve_pedal(expression, page):
    if expression['kind'] in ('PEDAL_DOWN', 'PEDAL_UP') \
            and (expression.get('targetEventId') or '').startswith('expression-column:') \
            and any(e['sourceId'] == 'printed-expression-word' for e in expression.get('evidence', [])):
        # Literal cues need final written-column proof in both playback and MusicXML.
        expression['start'], expression['end'], expression['scope'] = None, None, 'UNRESOLVED'
    # Revalidate cached columns even when old anchors claim to be resolved.
    if (expression.get('targetEventId') or '').startswith(('pedal-hooks-v1:', 'pedal-glyph-column-v1:')) \
            and expression['kind'] in ('PEDAL_DOWN', 'PEDAL_UP'):
        span = _pedal_span(expression, page)
        down = expression['kind'] == 'PEDAL_DOWN'
        expression['start'] = span[0 if down else 1] if span else None
        expression['end'] = span[1] if span and down and not (expression.get('targetEventId') or '').startswith('pedal-glyph-column-v1:') else None
        expression['scope'] = 'PART' if span else 'UNRESOLVED'
    elif any(e.get('sourceId') == 'printed-pedal-glyph' for e in expression.get('evidence', [])):
        expression['start'], expression['end'], expression['scope'] = None, None, 'UNRESOLVED'
    return expression


def _active_seconds(performance, beat):
    seconds, end, bpm = 0., 0., performance['openingBpm']
    for segment in performance['segments']:
        first, last, a, b = (segment[k] for k in ('startBeat', 'endBeat', 'startBpm', 'endBpm'))
        length = min(beat, last)-first
        if length > 0:
            slope = (b-a)/(last-first)
            seconds += 60*length/a if abs(slope) < 1e-12 else 60/slope*math.log1p(slope*length/a)
        end, bpm = last, b
        if beat <= last:
            return seconds
    return seconds+max(0, beat-end)*60/bpm


def _seconds(performance, beat, after):
    return _active_seconds(performance, beat)+sum(h['seconds'] for h in performance['holds']
        if h['beat'] < beat or after and math.isclose(h['beat'], beat, abs_tol=1e-8))


def _perform_one(pages, bpm, slide_group=0):
    pages = resolve_pages(pages)
    beats, numeric, expressions, bindings, sounds, aliases, payloads, directions = [], [], [], {}, [], {}, [], []
    paired_sounds = {}
    previous, offset = {}, 0.
    for page_index, page in enumerate(pages):
        local = page['measureBeats']; starts = [0.]
        for duration in local:
            if not math.isfinite(duration) or not .125 <= duration <= 128:
                raise ValueError('Invalid expressive measure duration')
            starts.append(starts[-1]+duration)
        if not math.isclose(starts[-1], page['totalBeats'], abs_tol=1e-6):
            raise ValueError('Expressive page extent disagrees with its measures')
        physical = page.get('sourcePage', page_index)
        paired = source_spans(page)
        note_ids = []
        for index, note in enumerate(page['events']):
            identifier = f'note:{physical}:{index}'; note_ids.append(identifier)
            start, end = offset+note['startBeat'], offset+note['startBeat']+note['durationBeats']
            if index in paired:
                start, end = offset+paired[index]['start'], offset+paired[index]['end']
            if not math.isfinite(start) or not math.isfinite(end) or start < offset or end <= start or end > offset+starts[-1]+1e-6:
                raise ValueError('Invalid expressive note span')
            validate_unpitched(note)
            if event_kind(note) == 'UNPITCHED':
                old = len(sounds)
                sounds.append(dict(id=identifier, start=start, end=end,
                    staff=note['staffIndex'], count=note['staffCount']))
                payloads.append(dict(note, sourceEventId=identifier))
                aliases[identifier] = old
                continue
            lane = (note['staffCount'], note['staffIndex'], note['midi'])
            old = previous.get(lane)
            if note.get('tiedFromPrevious') and lane[1] == 0 and lane[0] in (1, 2):
                alternate = previous.get((3-lane[0], 0, lane[2]))
                if alternate is not None and abs(sounds[alternate]['end']-start) <= .04 and (old is None or sounds[old]['end'] < sounds[alternate]['end']):
                    old = alternate
            if note.get('tiedFromPrevious') and old is not None and abs(sounds[old]['end']-start) <= .125 \
                    and note.get('guitarEffect', {}) == payloads[old].get('guitarEffect', {}) \
                    and (not note.get('_slideRelation') or note['_slideRelation'] == payloads[old].get('_slideRelation')):
                sounds[old]['end'] = max(sounds[old]['end'], end); aliases[identifier] = old
                if note.get('_slideOrigins'):
                    merged = list(payloads[old].get('_slideOrigins', []))
                    merged.extend(origin for origin in note['_slideOrigins'] if origin not in merged)
                    payloads[old]['_slideOrigins'] = merged

            else:
                old = len(sounds); sounds.append(dict(id=identifier, start=start, end=end,
                    staff=note['staffIndex'], count=note['staffCount']))
                payloads.append(dict(note)); aliases[identifier] = old
            previous[lane] = old
            if index in paired:
                paired_sounds[old] = dict(paired[index], start=start, end=end)
        for supplied in page.get('score', {}).get('expressiveEvents', []):
            if supplied['kind'] not in SUPPORTED:
                continue
            expression = _resolve_pedal(copy.deepcopy(supplied), page); members = _column_members(expression, page)
            expression['eventId'] = f'page:{page_index}/'+expression['eventId']
            if _rest_owned(expression):
                span = _rest_span(expression, page)
                expression['start'], expression['end'] = span if span else (None, None)
                expression['scope'] = 'REST' if span else 'UNRESOLVED'
            if members:
                if expression['kind'] == 'FERMATA':
                    pair_ids = {paired[i]['pair'] for i in members if i in paired}
                    members = sorted(set(members) | {i for i, span in paired.items() if span['pair'] in pair_ids})
                owned = [page['events'][i] for i in members]
                spans = [(paired[i]['start'], paired[i]['end']) if i in paired else
                         (page['events'][i]['startBeat'], page['events'][i]['startBeat']+page['events'][i]['durationBeats']) for i in members]
                start, release = spans[0]
                if all(not n.get('durationFallback', False) and math.isclose(span[0], start, abs_tol=1e-7)
                       and math.isclose(span[1], release, abs_tol=1e-7) for n, span in zip(owned, spans)):
                    breath = expression['kind'] in ('BREATH', 'CAESURA')
                    resolved_start = _anchor(release if breath else start, starts)
                    resolved_end = preserve_end(expression, resolved_start, page)
                    expression['start'] = resolved_start
                    expression['end'] = _anchor(release, starts) if expression['kind'] == 'FERMATA' else resolved_end
                    attack = expression['kind'] in ('SFORZANDO', 'SFORZATO', 'SFORZANDO_PIANO')
                    expression['scope'] = 'NOTE' if expression['kind'] == 'FERMATA' or attack else 'PART' if breath or expression['kind'] in ('PEDAL_DOWN', 'PEDAL_UP') else 'SCORE'
                    if has_open(expression):
                        expression['scope'] = 'UNRESOLVED'
                    if expression['kind'] == 'FERMATA':
                        bindings[expression['eventId']] = {aliases[note_ids[i]] for i in members}
            for field in ('start', 'end'):
                if expression[field] is not None:
                    expression[field]['measureIndex'] += len(beats)
            expressions.append(expression)
        from .source_tempo_clock import rhythmic_changes
        for change in rhythmic_changes(page):
            numeric.append(dict(change, measureIndex=change['measureIndex']+len(beats)))
        for supplied in page.get('score', {}).get('playbackDirections', []):
            direction = copy.deepcopy(supplied); direction['measureBoundary'] += len(beats)
            if direction.get('details', {}).get('end') is not None:
                direction['details']['end']['measureIndex'] += len(beats)
            directions.append(direction)
        beats.extend(local); offset += starts[-1]
    data = struct.pack('>idi', 0x45585031, bpm, len(beats))
    data += b''.join(struct.pack('>d', value) for value in beats)+struct.pack('>i', len(numeric))
    data += b''.join(struct.pack('>ifdd', c['measureIndex'], c['positionInMeasure'], c['bpm'], c.get('beatUnit', 1)) for c in numeric)
    data += struct.pack('>i', len(sounds))
    for sound in sounds:
        data += _text(sound['id'])+struct.pack('>ddii', sound['start'], sound['end'], sound['staff'], sound['count'])
    data += struct.pack('>i', len(bindings))
    for identifier, members in bindings.items():
        data += _text(identifier)+struct.pack('>i', len(members))+b''.join(_text(sounds[i]['id']) for i in sorted(members))
    result = _bridge(data+encode(directions, expressions, len(beats)))
    slide_visits = {}
    if any(payload.get('_slideOrigins') for payload in payloads):
        route = result.get('navigationOccurrences')
        if not isinstance(route, list):
            raise ValueError('Continuous slides require explicit performed navigation occurrences')
        source_starts = [0.]
        for value in beats:
            source_starts.append(source_starts[-1]+value)
        previous_source_end = previous_performed_end = None
        visit = None
        for occurrence in route:
            def absolute(anchor):
                return source_starts[anchor['measureIndex']]+anchor['quarterBeatOffset']
            source_start, source_end = absolute(occurrence['start']), absolute(occurrence['end'])
            contiguous = (previous_source_end is not None
                          and previous_source_end == source_start
                          and previous_performed_end == occurrence['performanceStartBeat'])
            if not contiguous:
                visit = f"expression:{slide_group}:{occurrence['occurrenceId']}"
            slide_visits[occurrence['occurrenceId']] = visit
            previous_source_end, previous_performed_end = source_end, occurrence['performanceEndBeat']
    events = []
    for interval in result['notes']:
        payload = payloads[interval['sourceIndex']]
        identifier = interval['performanceId']; start, end = interval['startBeat'], interval['endBeat']
        subdivision = payload.get('tremoloBeats', 0)
        if subdivision not in (0, .5, .25, .125, .0625):
            raise ValueError('Unsupported tremolo subdivision')
        # Subdivide the written/performed beat span before integrating tempo. A fixed
        # MIDI subdivision after time conversion adds extra attacks during slowing/holds.
        count = max(1, math.ceil((end-start)/subdivision-1e-8)) if subdivision else 1
        pair = paired_sounds.get(interval['sourceIndex'])
        windows = pulse_windows(pair, start, end, interval['sourceStartBeat']) if pair else (
            (ordinal, start+ordinal*subdivision if subdivision else start,
             min(end,start+(ordinal+1)*subdivision) if subdivision else end) for ordinal in range(count))
        for ordinal, a, b in windows:
            held = any(identifier in hold['sustainedTargets'] and
                       (a < hold['beat'] <= b+1e-8 if pair else math.isclose(hold['beat'], b, abs_tol=1e-8)) for hold in result['holds'])
            start_seconds, end_seconds = _seconds(result, a, True), _seconds(result, b, held)
            note = dict(payload)
            note.update(startBeat=start_seconds*2, durationBeats=(end_seconds-start_seconds)*2,
                        tiedFromPrevious=False, measureIndex=0,
                        performanceId=identifier if not (subdivision or pair) else identifier+f'/tremolo:{ordinal}')
            if note.get('_slideOrigins'):
                occurrences = interval['navigationOccurrences']
                visits = {slide_visits.get(occurrence) for occurrence in occurrences}
                if not occurrences or None in visits or len(visits) != 1:
                    raise ValueError('Continuous slide interval lacks a unique performed visit')
                note['_slideContext'] = dict(visitId=next(iter(visits)),
                    attack=not interval['clippedEntry'] and ordinal == 0 and not payload.get('tiedFromPrevious', False),
                    clippedEntry=interval['clippedEntry'] or ordinal != 0,
                    clippedRelease=interval['clippedRelease'] or b != end,
                    held=any(identifier in hold['sustainedTargets']
                             and interval['startBeat'] < hold['beat'] <= interval['endBeat']
                             for hold in result['holds']))
            if event_kind(note) == 'UNPITCHED':
                note['_unpitchedAttackId'] = identifier
                note['_unpitchedContinuation'] = False
                note['clippedEntry'] = interval['clippedEntry']
            if subdivision or pair:
                note['tremoloBeats'] = 0
            attacks = [attack for attack in result['attacks'] if attack['staffIndex'] == note['staffIndex']
                       and attack['staffCount'] == note['staffCount'] and math.isclose(attack['beat'], a, abs_tol=1e-7)]
            if attacks and not interval['clippedEntry']:
                note['performanceAttack'] = max(attacks, key=lambda attack: attack['gain'])
            events.append(note)
    duration = result['durationSeconds']*2
    return dict(events=events, measureBeats=[duration] if duration else [], totalBeats=duration,
                score=dict(tempoChanges=[]), pedalControls=[dict(control,
                    beat=_seconds(result, control['beat'], True)*2) for control in result['pedalControls']]), result


def perform_expressions(document, bpm):
    """Return a fixed-tempo performance document only when supported printed evidence exists."""
    pages = document['pages']
    for page in pages:
        for note in page['events']:
            validate_unpitched(note)
        for row in page.get('score', {}).get('rests', []):
            if rest_kind(row) == 'FULL_MEASURE':
                full_measure_span(page, row)
    if not (any(e['kind'] in SUPPORTED or has_open(e) for page in pages for e in page.get('score', {}).get('expressiveEvents', []))
            or any((page.get('score', {}).get('tremoloSource') or {}).get('pairs') for page in pages)):
        return None
    # Use the same arrangement boundaries as ordinary navigation export.
    from .navigation import _bridge as navigation_bridge
    data = struct.pack('>ii', 0x4e415031, len(pages))
    data += b''.join(struct.pack('>ii', len(page['measureBeats']), page.get('score', {}).get('firstMeasureNumber', 0)) for page in pages)
    ranges = navigation_bridge(data)
    played, evidence = [], []
    for group in ranges:
        page, result = _perform_one(pages[group['firstPage']:group['pageAfterLast']], bpm, group['firstPage'])
        played.append(page); evidence.append(result)
    return dict(document, pages=played, expressivePerformance=evidence)
