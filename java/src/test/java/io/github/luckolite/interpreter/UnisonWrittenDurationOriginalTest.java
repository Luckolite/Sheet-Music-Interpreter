// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UnisonWrittenDurationOriginalTest {
    static ScoreNoteEvent n(int beams, float duration, int dots) {
        return new ScoreNoteEvent(
                        0,
                        .4f,
                        4,
                        0,
                        2,
                        .3f,
                        false,
                        dots,
                        beams,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        duration,
                        1,
                        0)
                .withClef(ScoreNoteEvent.CLEF_TREBLE);
    }

    @Test
    public void equalEighthDurationsMayCoalesce() {
        assertTrue(ScoreUnisonRhythm.sameWrittenDuration(n(1, 0, 0), n(1, 0, 0)));
    }

    @Test
    public void quarterAndEighthRemainDifferent() {
        assertFalse(ScoreUnisonRhythm.sameWrittenDuration(n(0, 1, 0), n(1, 0, 0)));
    }

    @Test
    public void halfAndDottedQuarterRemainDifferent() {
        assertFalse(ScoreUnisonRhythm.sameWrittenDuration(n(0, 2, 0), n(0, 1, 1)));
    }

    @Test
    public void tripletAndStraightSixteenthsRemainDifferent() {
        assertFalse(
                ScoreUnisonRhythm.sameWrittenDuration(
                        n(2, 0, 0).withTupletRatio(3, 2), n(2, 0, 0)));
    }

    @Test
    public void equivalentDottedTripletAndEighthUseTheirWrittenValue() {
        assertTrue(
                ScoreUnisonRhythm.sameWrittenDuration(
                        n(1, 0, 1).withTupletRatio(3, 2), n(1, 0, 0)));
    }

    @Test
    public void differentDotsAreNotEquivalent() {
        assertFalse(ScoreUnisonRhythm.sameWrittenDuration(n(1, 0, 0), n(1, 0, 1)));
    }

    @Test
    public void unknownQuarterEncodingDoesNotProveEquality() {
        assertFalse(ScoreUnisonRhythm.sameWrittenDuration(n(0, 0, 0), n(0, 0, 0)));
    }

    @Test
    public void graceSlotsDoNotProveAnOrdinaryWrittenHold() {
        assertFalse(
                ScoreUnisonRhythm.sameWrittenDuration(
                        n(1, 0, 0).withArticulations(NoteOrnament.GRACE),
                        n(1, 0, 0).withArticulations(NoteOrnament.GRACE)));
    }

    @Test
    public void unpitchedRhythmIsOutsideThisPitchUnisonContract() {
        assertFalse(
                ScoreUnisonRhythm.sameWrittenDuration(
                        n(1, 0, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED), n(1, 0, 0)));
    }

    @Test
    public void missingEvidenceAbstains() {
        assertFalse(ScoreUnisonRhythm.sameWrittenDuration(null, n(1, 0, 0)));
        assertFalse(ScoreUnisonRhythm.sameWrittenDuration(n(1, 0, 0), null));
    }
}
