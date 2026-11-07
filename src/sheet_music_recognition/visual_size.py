# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Retained glyph size, independent of note kind, duration, and performance."""
def retained(page, index):
    notes = page.get('score', {}).get('notes', [])
    events = page.get('events', [])
    if len(notes) != len(events) or not 0 <= index < len(notes):
        return 'FULL'
    note, event = notes[index], events[index]
    if (type(event.get('sourceNoteIndex', index)) is not int
            or event.get('sourceNoteIndex', index) != index
            or any(type(note.get(k)) is not int or event.get(k) != note[k]
                   for k in ('measureIndex', 'staffIndex', 'staffCount'))):
        return 'FULL'
    size = note.get('visualSize', 'FULL')
    if type(size) is not str or size not in ('FULL', 'CUE'):
        raise ValueError('Invalid retained note visual size')
    return size
