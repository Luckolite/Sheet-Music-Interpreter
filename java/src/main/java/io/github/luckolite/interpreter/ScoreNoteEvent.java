// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and platform-independent diagnostics.
package io.github.luckolite.interpreter;

/** One notehead plus written rhythm/tie/accidental metadata read by the measure-guide pass. */
public record ScoreNoteEvent(
        int measureIndex,
        float positionInMeasure,
        int staffStep,
        int staffIndex,
        int staffCount,
        float pageY,
        boolean tiedFromPrevious,
        int augmentationDots,
        int beamCount,
        int writtenAccidental,
        float unbeamedDurationBeats,
        int tupletDivisor,
        float followingRestBeats,
        int articulations,
        int clefBottomDiatonic,
        boolean crossStaffBeam,
        float leadingRestBeats,
        boolean compactOpening,
        int octaveShift,
        int boundaryTies,
        int tupletNormalNotes,
        int stemDirection) {

    /** Existing callers without printed shaft evidence keep an unknown direction. */
    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats,
            int articulations,
            int clefBottomDiatonic,
            boolean crossStaffBeam,
            float leadingRestBeats,
            boolean compactOpening,
            int octaveShift,
            int boundaryTies,
            int tupletNormalNotes) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                boundaryTies,
                tupletNormalNotes,
                0);
    }

    public ScoreNoteEvent withStemDirection(int direction) {
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                boundaryTies,
                tupletNormalNotes,
                direction);
    }

    /** Legacy tuplets retain their former ratios; explicit normal counts preserve 5:3 and other ratios. */
    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats,
            int articulations,
            int clefBottomDiatonic,
            boolean crossStaffBeam,
            float leadingRestBeats,
            boolean compactOpening,
            int octaveShift,
            int boundaryTies) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                boundaryTies,
                defaultTupletNormalNotes(tupletDivisor));
    }

    public static int defaultTupletNormalNotes(int actual) {
        return switch (actual) {
            case 3 -> 2;
            case 5, 6, 7 -> 4;
            default -> 1;
        };
    }

    public ScoreNoteEvent withTupletRatio(int actual, int normal) {
        if (actual != 1 && actual != 3 && actual != 5 && actual != 6 && actual != 7)
            throw new IllegalArgumentException("Unsupported tuplet actual count");
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                actual,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                boundaryTies,
                normal,
                stemDirection);
    }

    /** Optical evidence only: incoming above/below, then outgoing above/below. */
    public static final int BOUNDARY_TIES_ALL = 15;

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats,
            int articulations,
            int clefBottomDiatonic,
            boolean crossStaffBeam,
            float leadingRestBeats,
            boolean compactOpening,
            int octaveShift) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                0);
    }

    public ScoreNoteEvent withBoundaryTies(int evidence) {
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                evidence,
                tupletNormalNotes,
                stemDirection);
    }

    /** Compatibility constructor: notes without an octave mark keep their written register. */
    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats,
            int articulations,
            int clefBottomDiatonic,
            boolean crossStaffBeam,
            float leadingRestBeats,
            boolean compactOpening) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                0);
    }

    public ScoreNoteEvent withOctaveShift(int shift) {
        if (shift < -2 || shift > 2)
            throw new IllegalArgumentException("Octave shift must be -2..2");
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                shift,
                boundaryTies,
                tupletNormalNotes,
                stemDirection);
    }

    /** Source-compatible constructor for callers without opening-measure geometry. */
    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats,
            int articulations,
            int clefBottomDiatonic,
            boolean crossStaffBeam,
            float leadingRestBeats) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                false);
    }

    public ScoreNoteEvent withCompactOpening() {
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                true,
                octaveShift,
                boundaryTies,
                tupletNormalNotes,
                stemDirection);
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats,
            int articulations,
            int clefBottomDiatonic,
            boolean crossStaffBeam) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                0);
    }

    public ScoreNoteEvent withLeadingRest(float beats) {
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                crossStaffBeam,
                beats,
                compactOpening,
                octaveShift,
                boundaryTies,
                tupletNormalNotes,
                stemDirection);
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats,
            int articulations,
            int clefBottomDiatonic) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                false);
    }

    public ScoreNoteEvent withCrossStaffBeam() {
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                Math.max(1, beamCount),
                writtenAccidental,
                0,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clefBottomDiatonic,
                true,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                boundaryTies,
                tupletNormalNotes,
                stemDirection);
    }

    public static final int CLEF_UNKNOWN = -1;
    public static final int CLEF_TREBLE = 30; // E4, C=0 diatonic numbering
    public static final int CLEF_ALTO = 24; // F3; middle C on the third line
    public static final int CLEF_TENOR = 22; // D3; middle C on the fourth line
    public static final int CLEF_TREBLE_OTTAVA = 37; // E5, treble clef with 8 above
    public static final int CLEF_BASS = 18; // G2

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats,
            int articulations) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                CLEF_UNKNOWN);
    }

    public ScoreNoteEvent withClef(int clef) {
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                articulations,
                clef,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                boundaryTies,
                tupletNormalNotes,
                stemDirection);
    }

    public int diatonicPitchIdentity() {
        return staffStep + (clefBottomDiatonic == CLEF_UNKNOWN ? 0 : clefBottomDiatonic);
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor,
            float followingRestBeats) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                0);
    }

    public ScoreNoteEvent withArticulations(int marks) {
        return new ScoreNoteEvent(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                followingRestBeats,
                marks,
                clefBottomDiatonic,
                crossStaffBeam,
                leadingRestBeats,
                compactOpening,
                octaveShift,
                boundaryTies,
                tupletNormalNotes,
                stemDirection);
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats,
            int tupletDivisor) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                tupletDivisor,
                0);
    }

    public static final int ACCIDENTAL_DOUBLE_FLAT = -2;
    public static final int ACCIDENTAL_FLAT = -1;
    public static final int ACCIDENTAL_NATURAL = 0;
    public static final int ACCIDENTAL_SHARP = 1;
    public static final int ACCIDENTAL_DOUBLE_SHARP = 3;

    public static int accidentalSemitones(int accidental) {
        return accidental == ACCIDENTAL_DOUBLE_SHARP ? 2 : accidental;
    }

    /** No local glyph: use the key signature unless an earlier accidental carries in the measure. */
    public static final int ACCIDENTAL_FROM_KEY = 2;

    /** Zero means the optical pass could not safely distinguish quarter/half/whole. */
    public static final float DURATION_UNKNOWN = 0f;

    public static final float DURATION_QUARTER = 1f;
    public static final float DURATION_HALF = 2f;
    public static final float DURATION_WHOLE = 4f;

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental,
            float unbeamedDurationBeats) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                unbeamedDurationBeats,
                1);
    }

    /** Quarter-beat multiplier from the printed actual and normal note counts. */
    public double durationScale() {
        return tupletNormalNotes / (double) tupletDivisor;
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount,
            int writtenAccidental) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                writtenAccidental,
                DURATION_UNKNOWN);
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                .5f,
                false,
                0,
                0,
                ACCIDENTAL_FROM_KEY,
                DURATION_UNKNOWN);
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                0,
                0,
                ACCIDENTAL_FROM_KEY,
                DURATION_UNKNOWN);
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                0,
                ACCIDENTAL_FROM_KEY,
                DURATION_UNKNOWN);
    }

    public ScoreNoteEvent(
            int measureIndex,
            float positionInMeasure,
            int staffStep,
            int staffIndex,
            int staffCount,
            float pageY,
            boolean tiedFromPrevious,
            int augmentationDots,
            int beamCount) {
        this(
                measureIndex,
                positionInMeasure,
                staffStep,
                staffIndex,
                staffCount,
                pageY,
                tiedFromPrevious,
                augmentationDots,
                beamCount,
                ACCIDENTAL_FROM_KEY,
                DURATION_UNKNOWN);
    }

    public ScoreNoteEvent {
        if (stemDirection < -1 || stemDirection > 1)
            throw new IllegalArgumentException("Invalid printed stem direction");
        if ((boundaryTies & ~BOUNDARY_TIES_ALL) != 0)
            throw new IllegalArgumentException("Invalid boundary tie evidence");
        if (octaveShift < -2 || octaveShift > 2)
            throw new IllegalArgumentException("Octave shift must be -2..2");
        if (!Float.isFinite(leadingRestBeats) || leadingRestBeats < 0 || leadingRestBeats > 16)
            leadingRestBeats = 0;
        if (clefBottomDiatonic != CLEF_TREBLE
                && clefBottomDiatonic != CLEF_BASS
                && clefBottomDiatonic != CLEF_TREBLE_OTTAVA
                && clefBottomDiatonic != CLEF_ALTO
                && clefBottomDiatonic != CLEF_TENOR) clefBottomDiatonic = CLEF_UNKNOWN;
        articulations &= NoteArticulation.ALL;
        if (!Float.isFinite(followingRestBeats)
                || followingRestBeats < 0
                || followingRestBeats > 16) followingRestBeats = 0;
        if (tupletDivisor != 3 && tupletDivisor != 5 && tupletDivisor != 6 && tupletDivisor != 7)
            tupletDivisor = 1;
        if (tupletNormalNotes < 1
                || tupletNormalNotes > 16
                || tupletDivisor == 1 && tupletNormalNotes != 1)
            throw new IllegalArgumentException("Invalid tuplet normal count");
        augmentationDots = Math.max(0, Math.min(2, augmentationDots));
        beamCount = Math.max(0, Math.min(4, beamCount));
        if (writtenAccidental < ACCIDENTAL_DOUBLE_FLAT
                || writtenAccidental > ACCIDENTAL_DOUBLE_SHARP)
            writtenAccidental = ACCIDENTAL_FROM_KEY;
        if (!Float.isFinite(unbeamedDurationBeats)
                || unbeamedDurationBeats < .25f
                || unbeamedDurationBeats > DURATION_WHOLE) unbeamedDurationBeats = DURATION_UNKNOWN;
    }
}
