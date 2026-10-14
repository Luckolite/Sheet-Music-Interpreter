# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Retained source clefs on the reconstructed staff timeline; pitches are never changed."""
import xml.etree.ElementTree as ET
from itertools import groupby
from .typed_events import event_kind

LAYOUT = {18: ('F',4,0), 22: ('C',4,0), 24: ('C',3,0), 30: ('G',2,0), 37: ('G',2,1)}


def retained(page, index):
    event = page['events'][index]
    if event.get('clefInferred', False):
        return None
    explicit = event.get('clefBottomDiatonic')
    if explicit is not None and (type(explicit) is not int or explicit not in (*LAYOUT, -1)):
        raise ValueError('Unsupported retained display clef')
    explicit = None if explicit == -1 else explicit
    notes = page.get('score', {}).get('notes', [])
    if len(notes) != len(page['events']):
        return explicit
    source = notes[index]
    # Main preserves decoded-head order; refuse to borrow a clef from an unrelated slot.
    fields = ('measureIndex', 'staffIndex', 'staffCount')
    if (type(event.get('sourceNoteIndex', index)) is not int
            or event.get('sourceNoteIndex', index) != index
            or any(key not in event or key not in source or event[key] != source[key] for key in fields)):
        return explicit
    bottom = source.get('clefBottomDiatonic', -1)
    if type(bottom) is not int or bottom not in (*LAYOUT, -1):
        raise ValueError('Unsupported retained source clef')
    bottom = None if bottom == -1 else bottom
    if explicit is not None and bottom is not None and explicit != bottom:
        raise ValueError('Conflicting retained display clefs')
    return explicit if explicit is not None else bottom


def plan(notes):
    total, count = 0, 0
    for note in notes:
        if event_kind(note) == 'PITCHED':
            total += note['midi']
            count += 1
    bass = (total/count < 60 if count
            else bool(notes) and notes[0].get('clefBottomDiatonic') == 18)
    initial = 18 if bass else 30
    first = notes[0]['start'] if notes else 0
    changes, current = {0: initial}, initial
    previous = -1
    # The exporter already orders notes by attack; retain only actual changes, not every onset.
    for tick, group in groupby(notes, key=lambda n: n['start']):
        if tick < previous:
            raise ValueError('Unordered source clef timeline')
        previous = tick
        values = {n['retainedClef'] for n in group if n.get('retainedClef') is not None}
        if len(values) > 1:
            raise ValueError('Conflicting simultaneous clefs need explicit staff mapping')
        if not values:
            continue
        bottom = next(iter(values))
        if tick == first:
            changes[0] = bottom
            current = bottom
        elif bottom != current:
            changes[tick] = bottom
            current = bottom
    return changes


def write(attributes, bottom):
    sign, line, octave = LAYOUT[bottom]
    clef = ET.SubElement(attributes, 'clef')
    ET.SubElement(clef, 'sign').text = sign
    ET.SubElement(clef, 'line').text = str(line)
    if octave:
        ET.SubElement(clef, 'clef-octave-change').text = str(octave)
