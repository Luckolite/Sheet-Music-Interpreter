# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Join two proved page-edge shoulders without guessing ties from equal pitches."""

from .typed_events import event_kind, validate_unpitched


def _valid_boundary_ties(value):
    if type(value) is not int or value < 0 or value & ~((1 << 22) - 1):
        return False
    if not value & 16:
        return value <= 15
    string = value >> 5 & 7
    count = 6 + (value >> 8 & 1)
    fret = value >> 9 & 63
    effective_open = value >> 15 & 127
    return bool(value & 15) and string < count and fret <= 36 and effective_open + fret <= 127


def _compatible_boundary_ties(before, after):
    if not _valid_boundary_ties(before) or not _valid_boundary_ties(after):
        return False
    return ((before & ~15) == (after & ~15) if before & 16 and after & 16
            else not (before | after) & 16)


def resolve_boundary_ties(document):
    pages = list(document["pages"])
    for page in pages:
        for event in page['events']:
            validate_unpitched(event)
            if not _valid_boundary_ties(event.get('boundaryTies', 0)):
                raise ValueError('Invalid boundary tie identity')

    for index in range(1, len(pages)):
        before, after = pages[index - 1], pages[index]
        if ("sourcePage" in before or "sourcePage" in after) and (
                before.get("sourcePage", -2) + 1 != after.get("sourcePage", -2)):
            continue
        events = list(after["events"])
        changed = False
        score_notes = list(after.get("score", {}).get("notes", []))
        for number, current in enumerate(events):
            if event_kind(current) == 'UNPITCHED':
                continue
            incoming = current.get("boundaryTies", 0) & 3
            if not incoming or abs(current["startBeat"]) > .04:
                continue
            for previous in before["events"]:
                if event_kind(previous) == 'UNPITCHED':
                    continue
                if not _compatible_boundary_ties(previous.get("boundaryTies", 0), current.get("boundaryTies", 0)):
                    continue
                if not (((previous.get("boundaryTies", 0) & 15) >> 2) & incoming):
                    continue
                tab_typed = bool(current.get("boundaryTies", 0) & 16)
                if (not tab_typed and (previous.get("boundaryPitch") is None
                        or previous["boundaryPitch"] != current.get("boundaryPitch"))
                        or previous["staffIndex"] != current["staffIndex"]
                        or previous["staffCount"] != current["staffCount"]
                        or abs(previous["startBeat"] + previous["durationBeats"] - before["totalBeats"]) > .04):
                    continue
                # An unmarked continuation keeps the tied pitch across a bar/key change.
                if (tab_typed or current.get("boundaryAccidental") != 2) and previous["midi"] != current["midi"]:
                    continue
                events[number] = dict(current, tiedFromPrevious=True, midi=previous["midi"])
                note_index = current.get("sourceNoteIndex", -1)
                if 0 <= note_index < len(score_notes):
                    score_notes[note_index] = dict(score_notes[note_index], tiedFromPrevious=True)
                    if not tab_typed:
                        pitch = previous["boundaryPitch"]
                        natural = (pitch // 7 + 1) * 12 + (0, 2, 4, 5, 7, 9, 11)[pitch % 7]
                        accidental = previous["midi"] - natural
                        score_notes[note_index]["writtenAccidental"] = 3 if accidental == 2 else accidental
                changed = True
                break
        if changed:
            score = dict(after.get("score", {}))
            if "notes" in score:
                score["notes"] = score_notes
            pages[index] = dict(after, events=events, score=score)
    return dict(document, pages=pages)
