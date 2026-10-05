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
from .semantic_wire import encode
from .typed_events import event_kind, validate_unpitched

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


def _rest_span(expression, page):
    """Same exact-slot proof as ScoreRestFermataDetector, using exported musical note times."""
    if not _rest_owned(expression):
        return None
    try:
        m, staff, count, bits = map(int, expression['targetEventId'][len('printed-rest:'):].split(':'))
        position = struct.unpack('>f', struct.pack('>I', bits & 0xffffffff))[0]
    except (ValueError, struct.error):
        return None
    if not math.isfinite(position) or not 0 <= position <= 1 or not 0 <= m < len(page['measureBeats']) \
            or staff != expression['staffIndex'] or count != expression['staffCount']:
        return None
    return _rest_column_span(page, m, staff, count, position, .018)


def _rest_column_span(page, m, staff, count, position, tolerance):
    raw = page.get('score', {}).get('notes', [])
    if len(raw) != len(page['events']):
        return None
    belongs = lambda row: row['measureIndex'] == m and row['staffIndex'] == staff and row['staffCount'] == count
    rests = [r for r in page.get('score', {}).get('rests', []) if belongs(r)]
    targets = [r for r in rests if abs(r['positionInMeasure']-position) <= tolerance]
    if len(targets) != 1:
        return None
    target = targets[0]; duration = target['durationBeats']; beats = page['measureBeats'][m]
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
    slot = [r for r in rests if before < r['positionInMeasure'] < after]
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
    if expression['kind'] not in ('PEDAL_DOWN', 'PEDAL_UP') or not target.startswith('pedal-hooks-v1:'):
        return None
    try:
        fields = list(map(int, target[len('pedal-hooks-v1:'):].split(':')))
        if len(fields) != 10 or any(not -2147483648 <= value <= 2147483647 for value in fields):
            return None
        staff, count = fields[-2:]
        if staff < 0 or count <= staff or staff != expression['staffIndex'] or count != expression['staffCount']:
            return None
        if not any(e['sourceId'] == 'printed-pedal-bracket' and e['staffIndex'] == staff
                   and e['staffCount'] == count for e in expression.get('evidence', [])):
            return None
        anchors = []
        raw = page.get('score', {}).get('notes', [])
        starts = [0.]
        for duration in page['measureBeats']:
            starts.append(starts[-1]+duration)
        for m, kind, bits, tolerance_bits in (fields[:4], fields[4:8]):
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
        if (anchors[0]['measureIndex'], anchors[0]['quarterBeatOffset']) >= \
                (anchors[1]['measureIndex'], anchors[1]['quarterBeatOffset']):
            return None
        return tuple(anchors)
    except (ValueError, struct.error, KeyError, TypeError):
        return None


def _resolve_pedal(expression, page):
    # Revalidate cached columns even when old anchors claim to be resolved.
    if (expression.get('targetEventId') or '').startswith('pedal-hooks-v1:') \
            and expression['kind'] in ('PEDAL_DOWN', 'PEDAL_UP'):
        span = _pedal_span(expression, page)
        down = expression['kind'] == 'PEDAL_DOWN'
        expression['start'] = span[0 if down else 1] if span else None
        expression['end'] = span[1] if span and down else None
        expression['scope'] = 'PART' if span else 'UNRESOLVED'
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


def _perform_one(pages, bpm):
    beats, numeric, expressions, bindings, sounds, aliases, payloads, directions = [], [], [], {}, [], {}, [], []
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
        note_ids = []
        for index, note in enumerate(page['events']):
            identifier = f'note:{physical}:{index}'; note_ids.append(identifier)
            start, end = offset+note['startBeat'], offset+note['startBeat']+note['durationBeats']
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
                    and note.get('guitarEffect', {}) == payloads[old].get('guitarEffect', {}):
                sounds[old]['end'] = max(sounds[old]['end'], end); aliases[identifier] = old
            else:
                old = len(sounds); sounds.append(dict(id=identifier, start=start, end=end,
                    staff=note['staffIndex'], count=note['staffCount']))
                payloads.append(dict(note)); aliases[identifier] = old
            previous[lane] = old
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
                owned = [page['events'][i] for i in members]
                start = owned[0]['startBeat']; release = start+owned[0]['durationBeats']
                if all(not n.get('durationFallback', False) and math.isclose(n['startBeat'], start, abs_tol=1e-7)
                       and math.isclose(n['startBeat']+n['durationBeats'], release, abs_tol=1e-7) for n in owned):
                    breath = expression['kind'] in ('BREATH', 'CAESURA')
                    expression['start'] = _anchor(release if breath else start, starts)
                    expression['end'] = _anchor(release, starts) if expression['kind'] == 'FERMATA' else None
                    attack = expression['kind'] in ('SFORZANDO', 'SFORZATO', 'SFORZANDO_PIANO')
                    expression['scope'] = 'NOTE' if expression['kind'] == 'FERMATA' or attack else 'PART' if breath else 'SCORE'
                    if expression['kind'] == 'FERMATA':
                        bindings[expression['eventId']] = {aliases[note_ids[i]] for i in members}
            for field in ('start', 'end'):
                if expression[field] is not None:
                    expression[field]['measureIndex'] += len(beats)
            expressions.append(expression)
        for change in page.get('score', {}).get('tempoChanges', []):
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
        for ordinal in range(count):
            a = start+ordinal*subdivision if subdivision else start
            b = min(end,a+subdivision) if subdivision else end
            held = any(identifier in hold['sustainedTargets'] and math.isclose(hold['beat'], b, abs_tol=1e-8) for hold in result['holds'])
            start_seconds, end_seconds = _seconds(result, a, True), _seconds(result, b, held)
            note = dict(payload)
            note.update(startBeat=start_seconds*2, durationBeats=(end_seconds-start_seconds)*2,
                        tiedFromPrevious=False, measureIndex=0,
                        performanceId=identifier if not subdivision else identifier+f'/tremolo:{ordinal}')
            if event_kind(note) == 'UNPITCHED':
                note['_unpitchedAttackId'] = identifier
                note['_unpitchedContinuation'] = False
                note['clippedEntry'] = interval['clippedEntry']
            if subdivision:
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
    if not any(e['kind'] in SUPPORTED for page in pages for e in page.get('score', {}).get('expressiveEvents', [])):
        return None
    # Use the same arrangement boundaries as ordinary navigation export.
    from .navigation import _bridge as navigation_bridge
    data = struct.pack('>ii', 0x4e415031, len(pages))
    data += b''.join(struct.pack('>ii', len(page['measureBeats']), page.get('score', {}).get('firstMeasureNumber', 0)) for page in pages)
    ranges = navigation_bridge(data)
    played, evidence = [], []
    for group in ranges:
        page, result = _perform_one(pages[group['firstPage']:group['pageAfterLast']], bpm)
        played.append(page); evidence.append(result)
    return dict(document, pages=played, expressivePerformance=evidence)
