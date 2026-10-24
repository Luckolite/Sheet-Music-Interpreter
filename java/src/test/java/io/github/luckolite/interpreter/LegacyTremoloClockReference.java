// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Explicit pair time advancement; printed note values remain untouched. */
final class LegacyTremoloClockReference {
    private LegacyTremoloClockReference() {}

    static IdentityHashMap<ScoreNoteEvent, Double> advances(ScorePageInterpretation score) {
        return advances(score.notes(), score.tremoloSource());
    }

    static IdentityHashMap<ScoreNoteEvent, Double> advances(
            List<ScoreNoteEvent> notes, ScoreTremoloSource source) {
        IdentityHashMap<ScoreNoteEvent, Double> result = new IdentityHashMap<>();
        for (var pair : source.resolve().resolved()) {
            var first = notes.get(pair.first().get(0));
            var second = notes.get(pair.second().get(0));
            double duration = ScoreNoteTiming.writtenDurationBeats(first);
            if (!Double.isFinite(duration)
                    || duration <= 0
                    || first.measureIndex() != second.measureIndex()
                    || first.staffIndex() != second.staffIndex()
                    || first.staffCount() != second.staffCount()
                    || first.positionInMeasure() >= second.positionInMeasure())
                throw new IllegalArgumentException(
                        "Tremolo pair needs ordered same-lane duration ownership");
            Set<Integer> members = new HashSet<>();
            members.addAll(pair.first());
            members.addAll(pair.second());
            for (int index = 0; index < notes.size(); index++) {
                var note = notes.get(index);
                if (!members.contains(index)
                        && note.measureIndex() == first.measureIndex()
                        && note.staffIndex() == first.staffIndex()
                        && note.staffCount() == first.staffCount()
                        && note.positionInMeasure() > first.positionInMeasure() + .018f
                        && note.positionInMeasure() < second.positionInMeasure() - .018f)
                    throw new IllegalArgumentException("Unowned attack inside tremolo pair");
            }
            for (var side : List.of(pair.first(), pair.second()))
                for (int index : side) {
                    var note = notes.get(index);
                    var endpoint = side == pair.first() ? first : second;
                    if (note.kind() != ScoreNoteEvent.Kind.PITCHED
                            || note.measureIndex() != endpoint.measureIndex()
                            || note.staffIndex() != endpoint.staffIndex()
                            || note.staffCount() != endpoint.staffCount()
                            || Math.abs(note.positionInMeasure() - endpoint.positionInMeasure())
                                    > .018
                            || Math.abs(ScoreNoteTiming.writtenDurationBeats(note) - duration)
                                    > 1e-7
                            || (note.articulations() & 32768) != 0
                            || side == pair.first() && note.followingRestBeats() > 0
                            || side == pair.second() && note.leadingRestBeats() > 0)
                        throw new IllegalArgumentException(
                                "Unresolved tremolo endpoint duration or rest ownership");
                    result.put(note, duration * .5);
                }
        }
        return result;
    }
}
