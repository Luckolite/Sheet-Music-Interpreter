# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""MusicXML guitar gestures; bend changes are semitones, fractions included."""
import xml.etree.ElementTree as ET
from .guitar_effect import validate


def write(notation, effect):
    kind, delta, _ = validate(effect)
    technical = None
    if kind in ('bend', 'bend_release', 'whammy', 'whammy_release', 'pre_bend'):
        technical = notation.find('technical')
        if technical is None:
            technical = ET.SubElement(notation, 'technical')
        bend = ET.SubElement(technical, 'bend', {'shape': 'curved'})
        ET.SubElement(bend, 'bend-alter').text = f'{delta:g}'
        if kind == 'pre_bend':
            ET.SubElement(bend, 'pre-bend')
        if kind.startswith('whammy'):
            ET.SubElement(bend, 'with-bar')
        if kind.endswith('_release'):
            release = ET.SubElement(technical, 'bend', {'shape': 'curved'})
            ET.SubElement(release, 'bend-alter').text = f'{-delta:g}'
            ET.SubElement(release, 'release')
            if kind.startswith('whammy'):
                ET.SubElement(release, 'with-bar')
    elif kind.startswith('strum_'):
        # A downstroke crosses bass to treble; MusicXML's direction is sounding pitch order.
        ET.SubElement(notation, 'arpeggiate', {'direction': 'up' if kind == 'strum_down' else 'down'})
    else:
        description = kind
        if delta:
            description += f' ({delta:+g} semitones)'
        ET.SubElement(notation, 'other-notation', {'type': 'single'}).text = description
    if effect.get('vibrato'):
        ET.SubElement(notation, 'other-notation', {'type': 'single'}).text = 'vibrato'
