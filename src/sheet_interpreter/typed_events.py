# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Export validation for legacy pitched and explicitly typed unpitched events."""


def event_kind(event):
    kind = event.get('kind', 'PITCHED')
    if kind not in ('PITCHED', 'UNPITCHED'):
        raise ValueError('Unknown decoded note kind')
    return kind


def validate_unpitched(event):
    """Display position describes notation, never a sounding MIDI pitch."""
    if event_kind(event) != 'UNPITCHED':
        return
    if event.get('displayStep') not in ('C', 'D', 'E', 'F', 'G', 'A', 'B'):
        raise ValueError('Unpitched display step must be C..B')
    if type(event.get('displayOctave')) is not int or not 0 <= event['displayOctave'] <= 9:
        raise ValueError('Unpitched display octave must be 0..9')
    if ('clefBottomDiatonic' in event and (type(event['clefBottomDiatonic']) is not int
            or event['clefBottomDiatonic'] not in (18, 22, 24, 30, 37))):
        raise ValueError('Unpitched display clef must be a supported retained clef')
    if any(field in event for field in ('midi', 'boundaryPitch', 'boundaryAccidental', 'upperMidi', 'lowerMidi', 'frequency')):
        raise ValueError('An unpitched event must not carry a tonal pitch identity')
    if event.get('tiedFromPrevious') or event.get('boundaryTies', 0):
        raise ValueError('Unpitched source ties need explicit instrument identity semantics')
    if (event.get('octaveShift', 0) or event.get('tremoloBeats', 0)
            or event.get('glissando') or event.get('ornament') or event.get('ornaments')):
        raise ValueError('Unpitched tonal ornaments or tremolo need an explicit instrument realization')
    effect = event.get('guitarEffect', {})
    if effect and (not isinstance(effect, dict) or effect.get('type') != 'dead'
            or effect.get('semitones', 0) != 0 or effect.get('vibrato', False)
            or effect.get('palmMute', False)):
        raise ValueError('Only neutral DEAD evidence is supported for unpitched preview')
    attack = event.get('performanceAttack')
    if attack is not None:
        import math
        if (not isinstance(attack, dict) or any(type(attack.get(k)) not in (int, float)
                or not math.isfinite(attack[k]) or attack[k] < 0 for k in ('gain', 'settledGain', 'seconds'))):
            raise ValueError('Unpitched expressive gain attack must be finite and nonnegative')



def notation_order(event):
    """Keep legacy pitched ordering; distinct unpitched heads retain stable source order."""
    if event_kind(event) == 'PITCHED':
        return 0, event['midi']
    return 1, event['displayOctave'] * 7 + 'CDEFGAB'.index(event['displayStep'])


def display_position(event, flats=False):
    if event_kind(event) == 'UNPITCHED':
        return event['displayOctave'] * 7 + 'CDEFGAB'.index(event['displayStep'])
    midi = event['midi']
    letters = (0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6) if flats else (0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
    return (midi // 12 - 1) * 7 + letters[midi % 12]
