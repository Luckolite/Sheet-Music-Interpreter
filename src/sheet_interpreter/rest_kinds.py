# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Retained rest meaning, separate from optical position and whole-glyph base duration."""
import math


def rest_kind(row):
    kind = row.get('kind', 'LITERAL')
    if type(kind) is not str or kind not in ('LITERAL', 'FULL_MEASURE'):
        raise ValueError('Invalid rest kind')
    if kind == 'FULL_MEASURE':
        duration = row.get('durationBeats')
        if type(duration) not in (int, float) or duration != 4:
            raise ValueError('Full-measure rest must retain its undotted whole-glyph base')
    return kind


def full_measure_span(page, row):
    """Use only the producer's performed measure span; never infer it from geometry."""
    if rest_kind(row) != 'FULL_MEASURE':
        raise ValueError('Expected full-measure rest')
    m, staff, count = (row.get(key) for key in ('measureIndex', 'staffIndex', 'staffCount'))
    beats = page.get('measureBeats')
    if (type(m) is not int or not isinstance(beats, (list, tuple)) or not 0 <= m < len(beats)
            or type(staff) is not int or type(count) is not int or not 0 <= staff < count):
        raise ValueError('Full-measure rest needs a known measure and physical staff')
    position = row.get('positionInMeasure')
    if type(position) not in (int, float) or not math.isfinite(position) or not 0 <= position <= 1:
        raise ValueError('Invalid full-measure rest optical position')
    duration = beats[m]
    if type(duration) not in (int, float) or not math.isfinite(duration) or duration <= 0:
        raise ValueError('Full-measure rest needs a finite positive performed measure span')
    return duration
