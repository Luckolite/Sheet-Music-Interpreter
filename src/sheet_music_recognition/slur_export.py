# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Retained printed-slur ownership and document-order MusicXML numbering."""
import xml.etree.ElementTree as ET
from .slur_wire import anchor, encode_source
from .grace_export import order as musical_order


def retained_spans(pages, include_candidates=False):
    owners, spans, identities = {}, [], set()
    offset = 0
    for page in pages:
        score = page.get('score', {})
        notes, events = score.get('notes', []), page['events']
        source = score.get('slurSource')
        if source is not None:
            encode_source(source, len(notes))  # Reuse the bounded retained-source contract.
            arcs = source['spans'] + (source.get('candidates', []) if include_candidates else [])
            if any(source['noteOrigins']) and len(notes) != len(events):
                raise ValueError('Slur source needs one event per decoded note')
            for index, origin in enumerate(source['noteOrigins']):
                if origin is None:
                    continue
                event, note = events[index], notes[index]
                # Main emits every head in decoded-note order, including distinct equal heads.
                supplied = event.get('sourceNoteIndex', index)
                if type(supplied) is not int or supplied != index or any(
                        event.get(key) != note.get(key)
                        for key in ('measureIndex', 'staffIndex', 'staffCount')):
                    raise ValueError('Slur event and decoded note ownership disagree')
                key = anchor(origin)
                if key in owners:
                    raise ValueError('Ambiguous original slur owner across pages')
                owners[key] = offset + index
            for span in arcs:
                if span['id'] in identities:
                    raise ValueError('Duplicate retained slur identity across pages')
                identities.add(span['id'])
                spans.append(span)
        offset += len(events)
    # Deleted/unselected original endpoints stay retained in source, without borrowing a note.
    return [(span, owners[anchor(span['start'])], owners[anchor(span['end'])])
            for span in spans if anchor(span['start']) in owners and anchor(span['end']) in owners]


def classify(spans, pages, events, ties, known_context, exported_parts=None):
    """Resolve retained geometry only from the actual assembled sounding owners."""
    candidate_ids = {span['id'] for page in pages
                     for span in (page.get('score', {}).get('slurSource') or {}).get('candidates', [])}
    def known(event):
        return (event.get('retainedClef') is not None and not event.get('durationFallback', False)
                and type(event.get('midi')) is int and 0 <= event['midi'] <= 127
                and event.get('kind', 'PITCHED') == 'PITCHED')
    def part(event):
        index, count = event.get('staffIndex'), event.get('staffCount')
        return ((index, count) if type(index) is int and type(count) is int
                and 0 <= index < count else None)
    selected = []
    exported_parts = {} if exported_parts is None else exported_parts
    for span, start, end in spans:
        if span['id'] not in candidate_ids:
            selected.append((span, start, end))
            continue
        a, b = events[start], events[end]
        if not known_context or not known(a) or not known(b) or musical_order(a) >= musical_order(b):
            continue
        actual_part = exported_parts.get(start)
        if (part(a) is None or part(b) is None or actual_part is None
                or actual_part is not exported_parts.get(end)):
            continue
        if (start, end) in ties:
            continue
        phrase = a['midi'] != b['midi']
        if not phrase and part(a) is not None and part(a) == part(b) and a['end'] <= b['start']:
            phrase = any(known(middle) and part(middle) == part(a)
                         and middle['midi'] != a['midi']
                         and a['start'] < middle['start'] < b['start']
                         and middle['end'] > middle['start']
                         and a['end'] <= middle['start'] and middle['end'] <= b['start']
                         for middle in events)
        if phrase:
            selected.append((span, start, end))
    return selected


def apply(root, spans, attacks, events):
    if not spans:
        return
    requested = set(attacks.values())
    positions = {node: i for i, node in enumerate(root.iter('note')) if node in requested}
    parts = {note: part for part in root.findall('part') for note in part.iter('note') if note in requested}
    bound = []
    for span, start_owner, end_owner in spans:
        start, end = attacks.get(start_owner), attacks.get(end_owner)
        if start is None or end is None:
            raise ValueError('Missing retained slur attack fragment')
        if musical_order(events[start_owner]) >= musical_order(events[end_owner]):
            raise ValueError('Retained slur endpoints are not in musical order')
        if parts[start] is not parts[end]:
            raise ValueError('Cross-part slur needs an explicit part mapping')
        a, b = positions[start], positions[end]
        bound.append((min(a,b), max(a,b), span['id'], span, start, end, a, b))
    live, marks = {}, []
    for first, last, _, span, start, end, a, b in sorted(bound, key=lambda row: row[:3]):
        lane = live.setdefault(parts[start], [])
        lane[:] = [row for row in lane if not (row[1] < first or
                   row[1] == first and row[2] is start and a < b)]
        number = next((n for n in range(1,17) if all(row[0] != n for row in lane)), None)
        if number is None:
            raise ValueError('More than sixteen slurs overlap in MusicXML document order')
        lane.append((number,last,end if a < b else None))
        marks.extend([(False,number,end,span),(True,number,start,span)])
    # Validate/allocate every pair before appending either endpoint.
    for is_start, number, note, span in sorted(marks, key=lambda row: row[:2]):
        notation = note.find('notations')
        if notation is None:
            notation = ET.SubElement(note,'notations')
        attributes = dict(type='start' if is_start else 'stop',number=str(number))
        if is_start:
            attributes['line-type'] = span['lineType'].lower()
            if span['placement'] != 'AUTO':
                attributes['placement'] = span['placement'].lower()
                attributes['orientation'] = 'over' if span['placement'] == 'ABOVE' else 'under'
        ET.SubElement(notation,'slur',attributes)
