# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Unique releasing source owners; an overlapping later attack cannot replace a held note."""
from bisect import bisect_left, bisect_right
from collections import Counter, defaultdict


def releasing_links(rows, relations, compatible=lambda earlier, current: True):
    """Return current-index -> prior-index with unique owners at both endpoints.

    Callers supply their established clock units, lane identities and release tolerances.
    No performed sound alias or input list position is used as a pitch/time heuristic.
    """
    releases = defaultdict(list)
    for index, row in enumerate(rows):
        if row.get('eligible', True) and row['end'] > row['start']:
            releases[row['lane']].append((row['end'], index))
    for values in releases.values():
        values.sort()
    proposed = {}
    for index, row in enumerate(rows):
        if not row.get('eligible', True) or not row.get('tied', False) or row['end'] <= row['start']:
            continue
        candidates = set()
        for lane, tolerance in relations(row):
            values = releases.get(lane, ())
            first = bisect_left(values, (row['start'] - tolerance, -1))
            last = bisect_right(values, (row['start'] + tolerance, len(rows)))
            for _, earlier in values[first:last]:
                if rows[earlier]['start'] < row['start'] and compatible(rows[earlier], row):
                    candidates.add(earlier)
        if len(candidates) == 1:
            proposed[index] = candidates.pop()
    uses = Counter(proposed.values())
    return {current: prior for current, prior in proposed.items() if uses[prior] == 1}


def sound_lanes(row, tolerance, hidden_tolerance):
    lane = row['lane']
    yield lane, tolerance
    count, staff, pitch = lane
    if staff == 0 and count in (1, 2):
        yield (3 - count, staff, pitch), hidden_tolerance
