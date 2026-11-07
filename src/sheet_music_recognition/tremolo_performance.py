# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Source-owned paired spans and phase-preserving alternating chord pulses."""
import math
from .tremolo_export import retained


def source_spans(page):
    """Map each printed owner to the full pair span without merging identities."""
    marks = retained(page)
    groups = {}
    for owner, mark in marks.items():
        groups.setdefault(mark['pair'], []).append(owner)
    spans = {}
    for identifier, owners in groups.items():
        start = min(page['events'][i]['startBeat'] for i in owners)
        end = max(page['events'][i]['startBeat'] + page['events'][i]['durationBeats'] for i in owners)
        for owner in owners:
            spans[owner] = dict(pair=identifier, start=start, end=end,
                                side=0 if marks[owner]['type'] == 'start' else 1,
                                strokes=marks[owner]['strokes'])
    return spans


def pulse_windows(span, start, end, source_start):
    """Clip a projected occurrence while preserving its original pulse phase.

    start/end are projected beats; source_start is the retained source beat at
    projected start. Holds are applied afterward by the expressive clock.
    """
    values = (span['start'], span['end'], start, end, source_start)
    if any(type(v) not in (int, float) or not math.isfinite(v) for v in values):
        raise ValueError('Nonfinite paired tremolo clock')
    length = span['end'] - span['start']
    if length <= 0 or end <= start or source_start < span['start'] - 1e-7 or source_start + end - start > span['end'] + 1e-7:
        raise ValueError('Projected tremolo lies outside its source span')
    # Match the app's deliberate 75-percent tremolo attack density.
    written_count = min(8192, math.ceil(length * (2 ** span['strokes']) - 1e-7))
    count = max(min(2, written_count), math.floor(written_count * .75 + .5))
    origin = start - (source_start - span['start'])
    step = length / count
    for ordinal in range(span['side'], count, 2):
        a, b = max(start, origin + ordinal * step), min(end, origin + (ordinal + 1) * step)
        if b - a > 1e-8:
            yield ordinal, a, b
