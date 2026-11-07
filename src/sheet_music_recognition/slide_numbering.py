# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Bound straight-slide numbers by their paired endpoints in MusicXML document order."""


def assign(root):
    """Replace internal relation tokens with number-level values 1..16.

    MusicXML overlap follows document order, including backups and voices. A
    stop may precede its start, so a token is live between its two occurrences,
    irrespective of endpoint type. Split-note placement is already complete.
    Validate and plan every part before mutating any node or writing the file.
    """
    planned = []
    for part in root.findall('part'):
        markers = list(part.iter('slide'))
        pairs = {}
        for marker in markers:
            token = marker.get('number')
            kind = marker.get('type')
            if token is None or kind not in ('start', 'stop'):
                raise ValueError('Straight slide needs an explicit paired endpoint identity')
            pairs.setdefault(token, []).append(kind)
        if any(sorted(kinds) != ['start', 'stop'] for kinds in pairs.values()):
            raise ValueError('Straight slide needs exactly one start and one stop per identity')
        active = {}
        available = set(range(1, 17))
        for marker in markers:
            token = marker.get('number')
            if token in active:
                number = active.pop(token)
                available.add(number)
            else:
                if not available:
                    raise ValueError('More than 16 overlapping straight slides in MusicXML document order are unsupported')
                number = min(available)
                available.remove(number)
                active[token] = number
            planned.append((marker, number))
        if active:
            raise ValueError('Straight slide has an unclosed endpoint identity')
    for marker, number in planned:
        marker.set('number', str(number))
