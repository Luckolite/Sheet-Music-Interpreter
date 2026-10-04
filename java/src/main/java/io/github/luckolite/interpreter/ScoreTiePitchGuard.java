// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Keeps a decoded tie only when its earlier same-pitch note is present. */
final class ScoreTiePitchGuard {
    private ScoreTiePitchGuard() {}

    static List<ScoreNoteEvent> apply(List<ScoreNoteEvent> notes, List<ScoreKeyChange> keys) {
        return apply(notes, keys, false);
    }

    /** Revisit only an evidenced accidental contradiction after pages are assembled.
     * Do not rerun page-local absence checks or infer C major for an unknown key. */
    static List<ScoreNoteEvent> recheckWithKeyContext(
            List<ScoreNoteEvent> notes, List<ScoreKeyChange> keys) {
        return apply(notes, keys, true);
    }

    /** Caller-supplied inherited key is evaluation context, not a new printed mark.
     * Explicit page changes override it, including a signature at the opening. */
    static ScorePageInterpretation withInitialKeyContext(
            ScorePageInterpretation score, int fifths) {
        var keys = new ArrayList<ScoreKeyChange>();
        keys.add(new ScoreKeyChange(0, fifths));
        keys.addAll(score.keyChanges());
        var notes = recheckWithKeyContext(score.notes(), keys);
        if (notes == score.notes()) return score;
        return new ScorePageInterpretation(
                score.measures(),
                notes,
                score.firstMeasureNumber(),
                score.keyChanges(),
                score.tempoChanges(),
                score.meterChanges(),
                score.rests(),
                score.techniqueChanges(),
                score.dynamicChanges(),
                score.playbackDirections(),
                score.expressiveEvents());
    }

    private static List<ScoreNoteEvent> apply(
            List<ScoreNoteEvent> notes, List<ScoreKeyChange> keys, boolean contextOnly) {
        List<ScoreNoteEvent> result = contextOnly ? null : new ArrayList<>(notes);
        for (int i = 0; i < notes.size(); i++) {
            ScoreNoteEvent current = notes.get(i);
            if (!current.tiedFromPrevious()) continue;
            boolean changed = explicitPitchChange(notes, i, keys);
            if (contextOnly && !changed) continue;
            if (current.measureIndex() == 0 && !changed) continue;
            int pitch = midi(current, keys);
            if (pitch == Integer.MIN_VALUE && !changed) continue;
            boolean prior = false;
            for (int j = i - 1; !changed && pitch != Integer.MIN_VALUE && j >= 0; j--) {
                ScoreNoteEvent earlier = notes.get(j);
                if (current.measureIndex() - earlier.measureIndex() > 1) break;
                if (!sameContinuingStaff(earlier, current)) continue;
                if (earlier.measureIndex() == current.measureIndex()
                        && earlier.positionInMeasure() >= current.positionInMeasure() - .018f)
                    continue;
                if (midi(earlier, keys) == pitch) {
                    prior = true;
                    break;
                }
            }
            if (prior) continue;
            if (result == null) result = new ArrayList<>(notes);
            result.set(
                    i,
                    new ScoreNoteEvent(
                                    current.measureIndex(),
                                    current.positionInMeasure(),
                                    current.staffStep(),
                                    current.staffIndex(),
                                    current.staffCount(),
                                    current.pageY(),
                                    false,
                                    current.augmentationDots(),
                                    current.beamCount(),
                                    current.writtenAccidental(),
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
                            .withStemDirection(current.stemDirection()));
        }
        return result == null ? notes : result;
    }

    /** Two different printed accidentals on one staff position cannot form a tie.
     * An inherited accidental is compared only with known key and clef evidence. */
    private static boolean explicitPitchChange(
            List<ScoreNoteEvent> notes, int index, List<ScoreKeyChange> keys) {
        ScoreNoteEvent current = notes.get(index);
        if (current.writtenAccidental() == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) return false;
        for (int j = index - 1; j >= 0; j--) {
            ScoreNoteEvent earlier = notes.get(j);
            if (current.measureIndex() - earlier.measureIndex() > 1) break;
            if (!sameContinuingStaff(earlier, current)
                    || earlier.diatonicPitchIdentity() != current.diatonicPitchIdentity()) continue;
            if (earlier.measureIndex() == current.measureIndex()
                    && earlier.positionInMeasure() >= current.positionInMeasure() - .018f) continue;
            if (earlier.writtenAccidental() != ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
                return earlier.writtenAccidental() != current.writtenAccidental();
            int prior = midi(earlier, keys), pitch = midi(current, keys);
            return prior != Integer.MIN_VALUE && pitch != Integer.MIN_VALUE && prior != pitch;
        }
        return false;
    }

    /** A hidden lower staff does not change the identity of the continuing top staff.
     * Only adjacent systems with the same explicit clef qualify; recognition still
     * requires matching pitches and returning arcs at both printed endpoints. */
    static boolean sameContinuingStaff(ScoreNoteEvent earlier, ScoreNoteEvent current) {
        if (earlier.staffIndex() != current.staffIndex()) return false;
        if (earlier.staffCount() == current.staffCount()) return true;
        return earlier.staffIndex() == 0
                && Math.min(earlier.staffCount(), current.staffCount()) == 1
                && Math.max(earlier.staffCount(), current.staffCount()) == 2
                && current.measureIndex() == earlier.measureIndex() + 1
                && current.pageY() - earlier.pageY() > .04f
                && current.positionInMeasure() < .58f
                && earlier.clefBottomDiatonic() != ScoreNoteEvent.CLEF_UNKNOWN
                && earlier.clefBottomDiatonic() == current.clefBottomDiatonic();
    }

    private static int midi(ScoreNoteEvent note, List<ScoreKeyChange> keys) {
        if (note.clefBottomDiatonic() == ScoreNoteEvent.CLEF_UNKNOWN) return Integer.MIN_VALUE;
        int fifths = Integer.MIN_VALUE;
        for (ScoreKeyChange key : keys)
            if (key.measureIndex() <= note.measureIndex()) fifths = key.fifths();
        if (fifths == Integer.MIN_VALUE) return Integer.MIN_VALUE;
        int diatonic = note.diatonicPitchIdentity();
        int letter = Math.floorMod(diatonic, 7), octave = Math.floorDiv(diatonic, 7);
        int accidental = note.writtenAccidental();
        if (accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) {
            accidental = 0;
            int[] order =
                    fifths >= 0 ? new int[] {3, 0, 4, 1, 5, 2, 6} : new int[] {6, 2, 5, 1, 4, 0, 3};
            for (int k = 0; k < Math.abs(fifths); k++)
                if (order[k] == letter) accidental = fifths > 0 ? 1 : -1;
        }
        return (octave + 1 + note.octaveShift()) * 12
                + new int[] {0, 2, 4, 5, 7, 9, 11}[letter]
                + ScoreNoteEvent.accidentalSemitones(accidental);
    }
}
