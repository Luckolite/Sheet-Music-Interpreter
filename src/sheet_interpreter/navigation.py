# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Project decoded note events through the production Java route, without re-inference."""
import copy
import json
import math
import struct
import subprocess
import tempfile
import warnings
from bisect import bisect_left, bisect_right
from pathlib import Path
from .runtime import java_executable
from .semantic_wire import encode
from .typed_events import event_kind, validate_unpitched


def _bridge(data):
    jar = Path(__file__).with_name('interpreter.jar')
    if not jar.is_file():
        raise RuntimeError('Build the Java interpreter before exporting navigation')
    if len(data) > 64*1024*1024:
        raise ValueError('Navigation request too large')
    with tempfile.TemporaryDirectory(prefix='sheet-navigation-') as folder:
        request, result = Path(folder)/'request.nav', Path(folder)/'route.json'
        request.write_bytes(data)
        run = subprocess.run([java_executable(), '-Xmx512m', '-cp', str(jar),
            'io.github.luckolite.interpreter.NavigationBridge', str(request), str(result)],
            capture_output=True, text=True, encoding='utf-8', timeout=60)
        if run.returncode:
            raise ValueError('Navigation engine rejected decoded input: '+run.stderr.strip())
        return json.loads(result.read_text(encoding='utf-8'))


def _route(beats, directions):
    data = struct.pack('>ii', 0x4e415631, len(beats))
    data += b''.join(struct.pack('>f', value) for value in beats)
    return _bridge(data+encode(directions, [], len(beats)))


def project_navigation(document, bpm=120):
    pages=document['pages']
    for page in pages:
        for note in page['events']:
            validate_unpitched(note)
    if not any(page.get('score', {}).get('playbackDirections') for page in pages):
        return document
    if len(pages)<=1:
        return _project_one(document,bpm)
    data=struct.pack('>ii',0x4e415031,len(pages))
    for page in pages:
        data+=struct.pack('>ii',len(page['measureBeats']),page.get('score',{}).get('firstMeasureNumber',0))
    ranges=_bridge(data)
    output,diagnostics,occurrences=[],[],[]
    for index, part in enumerate(ranges):
        first,last=part['firstPage'],part['pageAfterLast']
        played=_project_one(dict(document,pages=pages[first:last]),bpm,first)
        output.extend(played['pages']);diagnostics.extend(played.get('navigationDiagnostics',[]))
        occurrences.extend(dict(occurrence,arrangementIndex=index,sourceFirstPage=first)
                           for occurrence in played.get('navigationOccurrences',[]))
    return dict(document,pages=output,navigationDiagnostics=diagnostics,navigationOccurrences=occurrences)


def _project_one(document, bpm=120, source_page_offset=0):
    pages = document['pages']
    if not any(page.get('score', {}).get('playbackDirections') for page in pages):
        return document  # Linear MIDI export still needs no Java process or inference imports.
    beats, directions, notes, tempos = [], [], [], []
    offset = 0.0
    for page_index, page in enumerate(pages):
        local = list(page['measureBeats']); starts = [0.0]
        for value in local:
            if not isinstance(value, (int, float)) or not math.isfinite(value) or not .125 <= value <= 128:
                raise ValueError('Invalid decoded measure duration')
            starts.append(starts[-1]+value)
        if not math.isclose(starts[-1], page['totalBeats'], abs_tol=1e-6):
            raise ValueError('Decoded page extent disagrees with its measures')
        for direction in page.get('score', {}).get('playbackDirections', []):
            row = copy.deepcopy(direction); row['measureBoundary'] += len(beats)
            if row.get('details', {}).get('end') is not None:
                row['details']['end']['measureIndex'] += len(beats)
            directions.append(row)
        for index, note in enumerate(page['events']):
            validate_unpitched(note)
            physical_page=page.get('sourcePage',page_index+source_page_offset)
            row = dict(note, startBeat=offset+note['startBeat'], sourceEventId=f'note:{physical_page}:{index}')
            if (not math.isfinite(row['startBeat']) or not math.isfinite(row['durationBeats'])
                    or row['durationBeats'] <= 0 or row['startBeat'] < offset
                    or row['startBeat'] >= offset+starts[-1]):
                raise ValueError('Invalid decoded note span')
            notes.append(row)
        for change in page.get('score', {}).get('tempoChanges', []):
            m = change['measureIndex']; position = change['positionInMeasure']
            if not 0 <= m < len(local) or not math.isfinite(position) or not 0 <= position <= 1:
                raise ValueError('Invalid decoded tempo anchor')
            if not math.isfinite(change['bpm']) or not 15 <= change['bpm'] <= 1600:
                raise ValueError('Invalid decoded tempo')
            tempos.append((offset+starts[m]+local[m]*position, change['bpm']))
        beats.extend(local); offset += starts[-1]
    starts = [0.0]
    for value in beats: starts.append(starts[-1]+value)
    # Traversal occurrences cover at most one source bar. Index overlapping note
    # spans once instead of scanning an entire document for every repeated bar.
    note_buckets=[[] for _ in beats]
    entries=0
    for note in notes:
        first=max(0,bisect_right(starts,note['startBeat'])-1)
        last=min(len(beats),bisect_left(starts,note['startBeat']+note['durationBeats']))
        entries+=max(0,last-first)
        if entries>1000000:
            raise ValueError('Decoded sustain spans exceed bounded export capacity')
        for measure in range(first,last):note_buckets[measure].append(note)
    route = _route(beats, directions)
    if not route['complete']:
        raise ValueError('Navigation traversal is incomplete: '+str(route['diagnostics']))
    if route['diagnostics']:
        warnings.warn('Navigation diagnostics: '+str(route['diagnostics']), RuntimeWarning)
    tempos.sort(key=lambda item: item[0])
    output_notes, output_tempos, spans = [], [], []
    prior_end = None
    run_predecessors = {}
    unpitched_visits = {}
    run_visit = None
    for occurrence in route['occurrences']:
        first, last = occurrence['start'], occurrence['end']
        source_start = starts[first['measureIndex']]+first['quarterBeatOffset']
        source_end = starts[last['measureIndex']]+last['quarterBeatOffset']
        performed = occurrence['performanceStartBeat']; span = source_end-source_start
        index = len(spans); spans.append(span)
        contiguous = prior_end is not None and math.isclose(prior_end, source_start, abs_tol=1e-8)
        if not contiguous:
            run_predecessors.clear()
            unpitched_visits.clear()
            run_visit = occurrence["occurrenceId"]
        for note in sorted(note_buckets[first['measureIndex']], key=lambda row: row['startBeat']):
            onset, finish = note['startBeat'], note['startBeat']+note['durationBeats']
            a, b = max(onset, source_start), min(finish, source_end)
            if b <= a: continue
            if event_kind(note) == 'UNPITCHED':
                owner = note['sourceEventId'] + '@' + str(run_visit)
                previous_end = unpitched_visits.get(owner)
                continuation = contiguous and previous_end is not None and math.isclose(previous_end, a, abs_tol=1e-8)
                output_notes.append(dict(note, measureIndex=index, startBeat=performed+a-source_start,
                    durationBeats=b-a, tiedFromPrevious=False, boundaryTies=0,
                    occurrenceId=occurrence['occurrenceId'], _unpitchedAttackId=owner,
                    _unpitchedContinuation=continuation, clippedEntry=onset < a))
                unpitched_visits[owner] = b
                continue
            tied = bool(note['tiedFromPrevious'])
            if onset < source_start: tied = contiguous
            elif tied:
                # A jump can land just before a jittered tied onset. Its old
                # predecessor may have been skipped, even when a same-pitch
                # performed note happens to end nearby. Only source notes
                # already visited in this contiguous run can own that tie.
                lanes = [(note['staffCount'], note['staffIndex'], note['midi'])]
                if note['staffIndex'] == 0 and note['staffCount'] in (1, 2):
                    lanes.append((3-note['staffCount'], 0, note['midi']))
                tied = any(run_predecessors.get(lane, float('inf')) < onset for lane in lanes)
            output_notes.append(dict(note, measureIndex=index, startBeat=performed+a-source_start,
                durationBeats=b-a, tiedFromPrevious=tied, boundaryTies=0,
                occurrenceId=occurrence['occurrenceId']))
            lane = (note['staffCount'], note['staffIndex'], note['midi'])
            run_predecessors[lane] = min(onset, run_predecessors.get(lane, onset))
        current = bpm
        for at, value in tempos:
            if at <= source_start: current = value
            else: break
        if not contiguous or not output_tempos:
            output_tempos.append(dict(measureIndex=index, positionInMeasure=0, bpm=current))
        for at, value in tempos:
            if source_start < at < source_end or contiguous and at == source_start:
                output_tempos.append(dict(measureIndex=index, positionInMeasure=(at-source_start)/span, bpm=value))
        prior_end = source_end
    total = route['occurrences'][-1]['performanceEndBeat'] if spans else 0
    return dict(document, navigationDiagnostics=route['diagnostics'], navigationOccurrences=route['occurrences'],
        pages=[dict(events=output_notes, measureBeats=spans, totalBeats=total,
                    score=dict(tempoChanges=output_tempos))])
