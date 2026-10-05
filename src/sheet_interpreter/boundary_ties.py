# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Join two proved page-edge shoulders without guessing ties from equal pitches."""

from .typed_events import event_kind, validate_unpitched


def resolve_boundary_ties(document):
    pages = list(document["pages"])
    for page in pages:
        for event in page['events']:
            validate_unpitched(event)

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
                if not ((previous.get("boundaryTies", 0) >> 2) & incoming):
                    continue
                if (previous.get("boundaryPitch") is None
                        or previous["boundaryPitch"] != current.get("boundaryPitch")
                        or previous["staffIndex"] != current["staffIndex"]
                        or previous["staffCount"] != current["staffCount"]
                        or abs(previous["startBeat"] + previous["durationBeats"] - before["totalBeats"]) > .04):
                    continue
                # An unmarked continuation keeps the tied pitch across a bar/key change.
                if current.get("boundaryAccidental") != 2 and previous["midi"] != current["midi"]:
                    continue
                events[number] = dict(current, tiedFromPrevious=True, midi=previous["midi"])
                note_index = current.get("sourceNoteIndex", -1)
                if 0 <= note_index < len(score_notes):
                    score_notes[note_index] = dict(score_notes[note_index], tiedFromPrevious=True)
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
