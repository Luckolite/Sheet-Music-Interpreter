# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Written ornament glyphs from proved source heads, never performed subdivisions."""
import xml.etree.ElementTree as ET
from .articulation_export import source_flags
from .typed_events import event_kind

TYPES = {1: 'trill-mark', 2: 'turn', 3: 'inverted-turn',
         4: 'inverted-mordent', 5: 'mordent'}
MASK = (7 << 5) | (1 << 8) | (3 << 9) | (3 << 11) | (1 << 13) | (1 << 14) | (7 << 16)


def retained(page, index):
    flags = source_flags(page, index) & MASK
    kind, beams = (flags >> 5) & 7, (flags >> 16) & 7
    isolated = kind == 6 and not flags & (1 << 14)
    if kind not in TYPES and not isolated and not 1 <= beams <= 4:
        return 0
    if event_kind(page['events'][index]) != 'PITCHED':
        if isolated and not 1 <= beams <= 4:
            return 0
        raise ValueError('Unpitched source ornaments need explicit instrument semantics')
    return flags


def write(notation, flags):
    kind = (flags >> 5) & 7
    glyph = TYPES.get(kind)
    beams = (flags >> 16) & 7
    beams = beams if 1 <= beams <= 4 else 0
    if kind == 6 and not flags & (1 << 14):
        articulations = notation.find('articulations')
        if articulations is None:
            articulations = ET.SubElement(notation, 'articulations')
        ET.SubElement(articulations, 'plop' if flags & (1 << 13) else 'scoop')
    if glyph is None and not beams:
        return
    ornaments = ET.SubElement(notation, 'ornaments')
    if glyph is not None:
        if flags & (1 << 8) and kind in (2, 3):
            glyph = 'delayed-turn' if kind == 2 else 'delayed-inverted-turn'
        ET.SubElement(ornaments, glyph)
    if glyph is not None:
        for shift, side in ((9, 'above'), (11, 'below')):
            accidental = (flags >> shift) & 3
            if accidental:
                ET.SubElement(ornaments, 'accidental-mark', placement=side).text = (
                    'flat', 'natural', 'sharp')[accidental-1]
    if beams:
        ET.SubElement(ornaments, 'tremolo', type='single').text = str(beams)
