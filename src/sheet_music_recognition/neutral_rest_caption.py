# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Literal pedal captions own an existing full silent column, never a pedal action."""
import math
import re
import struct

from .rest_kinds import rest_kind, full_measure_span

PREFIX = 'expression-rest-column:'
CAPTION = re.compile(r'\s*let\s+(?:the\s+)?pedal\s+ring[.!]?\s*', re.IGNORECASE | re.ASCII)


def is_caption(mark):
    if mark.get('kind') != 'UNRESOLVED_DIRECTION' or not isinstance(mark.get('targetEventId'), str) \
            or not mark['targetEventId'].startswith(PREFIX):
        return False
    evidence = mark.get('evidence', [])
    return bool(evidence) and all(e.get('sourceId') == 'printed-expression-word'
        and isinstance(e.get('printedText'), str)
        and CAPTION.fullmatch(e['printedText']) is not None for e in evidence)


def start(mark, page):
    """Resolve only the original unique full-rest identity in its measured part."""
    if not is_caption(mark):
        return None
    if type(mark.get('staffIndex')) is not int or type(mark.get('staffCount')) is not int:
        return None
    try:
        fields = mark['targetEventId'][len(PREFIX):].split(':')
        if len(fields) != 4 or any(re.fullmatch(r'-?\d+', f, flags=re.ASCII) is None for f in fields):
            return None
        m, staff, count, bits = map(int, fields)
        if any(not -2147483648 <= n <= 2147483647 for n in (m, staff, count, bits)):
            return None
        position = struct.unpack('>f', struct.pack('>i', bits))[0]
    except (ValueError, struct.error):
        return None
    if m < 0 or count < 1 or not 0 <= staff < count or not math.isfinite(position) \
            or not 0 <= position <= 1 or staff != mark.get('staffIndex') or count != mark.get('staffCount'):
        return None
    if any(e.get('staffIndex') != staff or e.get('staffCount') != count
           for e in mark['evidence']):
        return None
    belongs = lambda row: row.get('measureIndex') == m and row.get('staffIndex') == staff \
        and row.get('staffCount') == count
    score = page.get('score', {})
    rows = [r for r in score.get('rests', []) if belongs(r)]
    if len(rows) != 1 or rest_kind(rows[0]) != 'FULL_MEASURE' \
            or not math.isfinite(rows[0].get('positionInMeasure', math.nan)) \
            or abs(rows[0]['positionInMeasure'] - position) > .018:
        return None
    if any(belongs(n) for n in score.get('notes', [])):
        return None
    # The ordinary full-rest resolver retains the real inherited/pickup measure span.
    full_measure_span(page, rows[0])
    return dict(measureIndex=m, quarterBeatOffset=0)
