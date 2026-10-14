# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Retain basic source marks without interpreting ornament or guitar-effect bits."""
import xml.etree.ElementTree as ET

SYMBOLS = ((1, 'accent'), (2, 'strong-accent'), (4, 'staccato'),
           (8, 'tenuto'), (16, 'staccatissimo'))
MASK = 31


def source_flags(page, index):
    # Main emits one event per raw head, in the same order. Edited or partial
    # event lists cannot borrow marks from a different source slot.
    events = page['events']
    notes = page.get('score', {}).get('notes', [])
    if len(notes) != len(events):
        return 0
    event, source = events[index], notes[index]
    fields = ('measureIndex', 'staffIndex', 'staffCount')
    if (type(event.get('sourceNoteIndex', index)) is not int
            or event.get('sourceNoteIndex', index) != index
            or any(key not in event or key not in source or event[key] != source[key] for key in fields)):
        return 0
    flags = source.get('articulations', 0)
    if type(flags) is not int or not 0 <= flags <= 0x7fffffff:
        raise ValueError('Invalid retained articulation flags')
    return flags


def retained(page, index):
    return source_flags(page, index) & MASK


def chord_marks(chord, start):
    """Common shared-stem glyphs appear once; differing head marks survive."""
    flags = [n.get('retainedArticulations', 0) if start == n['start'] else 0 for n in chord]
    common = MASK
    for value in flags:
        common &= value
    return [value if i == 0 else value & ~common for i, value in enumerate(flags)]


def write(notation, flags):
    articulations = notation.find('articulations')
    if articulations is None:
        articulations = ET.SubElement(notation, 'articulations')
    for bit, tag in SYMBOLS:
        if flags & bit:
            ET.SubElement(articulations, tag, {'type': 'up'} if bit == 2 else {})
