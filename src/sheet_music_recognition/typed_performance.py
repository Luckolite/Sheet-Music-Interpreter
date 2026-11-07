# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Owned unpitched interval grouping without an invented sounding identity."""
import math
from .typed_events import event_kind, validate_unpitched


def unpitched_intervals(document):
    result, owners, offset = [], {}, 0.
    for page_index, page in enumerate(document['pages']):
        for index, note in enumerate(page['events']):
            if event_kind(note) != 'UNPITCHED':
                continue
            validate_unpitched(note)
            if any(type(value) not in (int, float) or not math.isfinite(value)
                   for value in (offset, note['startBeat'], note['durationBeats'], page['totalBeats'])):
                raise ValueError('Unpitched owned interval needs finite numeric source times')
            start = offset+note['startBeat']
            end = start+note['durationBeats']
            if (not math.isfinite(start) or not math.isfinite(end) or end <= start
                    or note['startBeat'] < 0 or end > offset+page['totalBeats']+1e-6):
                raise ValueError('Unpitched owned interval must be finite and inside the performed page')
            owner = note.get('_unpitchedAttackId')
            if owner is not None and (not isinstance(owner, str) or not owner or len(owner) > 4096):
                raise ValueError('Unpitched owned attack identity must be a bounded source ID')
            if type(note.get('_unpitchedContinuation', False)) is not bool:
                raise ValueError('Unpitched continuation flag must be explicit boolean ownership')
            identifier = owner or f'unpitched:{page_index}:{index}'
            if note.get('_unpitchedContinuation', False):
                previous = owners.get(owner)
                if (owner is None or previous is None or not math.isclose(previous['endBeat'], start, abs_tol=1e-8)
                        or previous['note'].get('sourceEventId') != note.get('sourceEventId')
                        or previous['note'].get('guitarEffect', {}) != note.get('guitarEffect', {})):
                    raise ValueError('Unpitched continuation needs the same owned source and contiguous visit')
                previous['endBeat'] = end
                continue
            if owner is not None and owner in owners:
                raise ValueError('Unpitched owned attack identity cannot merge independent visits')
            interval = dict(sourceId=identifier, startBeat=start, endBeat=end, note=note)
            result.append(interval)
            if owner is not None:
                owners[owner] = interval
        offset += page['totalBeats']
    return result
