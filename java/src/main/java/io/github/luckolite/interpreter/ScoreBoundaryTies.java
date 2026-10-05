// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Resolves independently printed shoulders only at actual adjacent-page boundaries. */
final class ScoreBoundaryTies {
    private ScoreBoundaryTies() {}

    private static final class SharpOrder {
        private static final int[] VALUES = {3, 0, 4, 1, 5, 2, 6};
    }

    private static final class FlatOrder {
        private static final int[] VALUES = {6, 2, 5, 1, 4, 0, 3};
    }

    static List<ScoreNoteEvent> resolve(
            List<ScoreNoteEvent> notes, List<ScoreKeyChange> keys, List<Integer> boundaries) {
        for (var note : notes)
            if (note.kind() == ScoreNoteEvent.Kind.UNPITCHED
                    && (note.tiedFromPrevious() || note.boundaryTies() != 0))
                throw new IllegalArgumentException(
                        "Unpitched source tie flags do not prove shared instrument ownership");
        var result = new ArrayList<>(notes);
        for (int i = 0; i < notes.size(); i++) {
            var current = notes.get(i);
            if (current.kind() != ScoreNoteEvent.Kind.PITCHED
                    || (current.boundaryTies() & 3) == 0
                    || !boundaries.contains(current.measureIndex())
                    || current.leadingRestBeats() > 0
                    || current.clefBottomDiatonic() == ScoreNoteEvent.CLEF_UNKNOWN) continue;
            for (var earlier : notes) {
                if (earlier.kind() != ScoreNoteEvent.Kind.PITCHED
                        || earlier.measureIndex() + 1 != current.measureIndex()
                        || earlier.staffIndex() != current.staffIndex()
                        || earlier.staffCount() != current.staffCount()
                        || ((earlier.boundaryTies() >> 2) & current.boundaryTies() & 3) == 0
                        || earlier.followingRestBeats() > 0
                        || earlier.clefBottomDiatonic() == ScoreNoteEvent.CLEF_UNKNOWN
                        || earlier.diatonicPitchIdentity() != current.diatonicPitchIdentity()
                        || earlier.octaveShift() != current.octaveShift()) continue;
                boolean interrupted = false;
                for (var other : notes)
                    if (other.staffIndex() == current.staffIndex()
                            && other.staffCount() == current.staffCount()
                            && (other.measureIndex() == earlier.measureIndex()
                                            && other.positionInMeasure()
                                                    > earlier.positionInMeasure() + .018f
                                    || other.measureIndex() == current.measureIndex()
                                            && other.positionInMeasure()
                                                    < current.positionInMeasure() - .018f)) {
                        interrupted = true;
                        break;
                    }
                if (interrupted) continue;
                int accidental = accidental(earlier, keys);
                if (accidental == Integer.MIN_VALUE) continue;
                if (current.writtenAccidental() != ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        && ScoreNoteEvent.accidentalSemitones(current.writtenAccidental())
                                != ScoreNoteEvent.accidentalSemitones(accidental)) continue;
                result.set(
                        i,
                        new ScoreNoteEvent(
                                        current.measureIndex(),
                                        current.positionInMeasure(),
                                        current.staffStep(),
                                        current.staffIndex(),
                                        current.staffCount(),
                                        current.pageY(),
                                        true,
                                        current.augmentationDots(),
                                        current.beamCount(),
                                        accidental,
                                        current.unbeamedDurationBeats(),
                                        current.tupletDivisor(),
                                        current.followingRestBeats(),
                                        current.articulations(),
                                        current.clefBottomDiatonic(),
                                        current.crossStaffBeam(),
                                        current.leadingRestBeats(),
                                        current.compactOpening(),
                                        current.octaveShift(),
                                        current.boundaryTies(),
                                        current.tupletNormalNotes())
                                .withStemDirection(current.stemDirection())
                                .withKind(current.kind()));
                break;
            }
        }
        return List.copyOf(result);
    }

    private static int accidental(ScoreNoteEvent note, List<ScoreKeyChange> keys) {
        if (note.writtenAccidental() != ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
            return note.writtenAccidental();
        Integer fifths = null;
        for (var key : keys) if (key.measureIndex() <= note.measureIndex()) fifths = key.fifths();
        if (fifths == null) return Integer.MIN_VALUE;
        int letter = Math.floorMod(note.diatonicPitchIdentity(), 7);
        int[] order = fifths >= 0 ? SharpOrder.VALUES : FlatOrder.VALUES;
        for (int k = 0; k < Math.min(7, Math.abs(fifths)); k++)
            if (order[k] == letter) return fifths > 0 ? 1 : -1;
        return ScoreNoteEvent.ACCIDENTAL_NATURAL;
    }
}
