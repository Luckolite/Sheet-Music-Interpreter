# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Source spelling for concert-pitch XML, only while its sounding identity agrees."""
from .clef_export import LAYOUT
from .typed_events import event_kind


def retained(page, index, key):
    event = page['events'][index]
    if event_kind(event) != 'PITCHED' or event.get('clefInferred', False):
        return None
    notes = page.get('score', {}).get('notes', [])
    if len(notes) != len(page['events']):
        return None
    source = notes[index]
    if event_kind(source) != 'PITCHED':
        return None
    fields = ('measureIndex', 'staffIndex', 'staffCount')
    if (type(event.get('sourceNoteIndex', index)) is not int
            or event.get('sourceNoteIndex', index) != index
            or any(k not in event or k not in source or event[k] != source[k] for k in fields)):
        return None
    bottom = source.get('clefBottomDiatonic', -1)
    if bottom == -1:
        return None
    step, accidental = source.get('staffStep'), source.get('writtenAccidental')
    shift = source.get('octaveShift', 0)
    if step is None or accidental is None:
        return None
    if (type(bottom) is not int or bottom not in LAYOUT
            or any(type(v) is not int for v in (step, accidental, shift))
            or accidental not in (-2, -1, 0, 1, 2, 3)):
        raise ValueError('Invalid retained pitch spelling')
    if event.get('clefBottomDiatonic', bottom) != bottom:
        return None
    diatonic = bottom + step + 7 * shift
    octave, letter = divmod(diatonic, 7)
    if accidental == 2:  # FROM_KEY is distinct from the double-sharp wire value 3.
        if type(key) is not int or not -7 <= key <= 7:
            raise ValueError('Invalid retained pitch key')
        order = (3, 0, 4, 1, 5, 2, 6) if key >= 0 else (6, 2, 5, 1, 4, 0, 3)
        alter = (1 if key > 0 else -1) if letter in order[:abs(key)] else 0
    else:
        alter = 2 if accidental == 3 else accidental
    midi = (octave + 1) * 12 + (0, 2, 4, 5, 7, 9, 11)[letter] + alter
    if midi != event['midi']:
        return None  # Edited or retuned heads must not borrow stale source spelling.
    return 'CDEFGAB'[letter], alter, octave
