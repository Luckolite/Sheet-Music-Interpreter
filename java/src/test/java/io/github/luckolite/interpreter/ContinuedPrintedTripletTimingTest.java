// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original written quarter followed by three beam groups; two explicitly carry 3:2. */
public class ContinuedPrintedTripletTimingTest {
    private static List<ScoreNoteEvent> phrase() {
        var notes = new ArrayList<ScoreNoteEvent>();
        notes.add(
                new ScoreNoteEvent(0, .04f, 0, 0, 1, .5f, false, 0, 0, 2, 1).withStemDirection(1));
        for (int i = 0; i < 9; i++) {
            var n =
                    new ScoreNoteEvent(0, .27f + i * .08f, i % 3, 0, 1, .5f, false, 0, 1)
                            .withStemDirection(1);
            if (i < 6) n = n.withTupletRatio(3, 2);
            notes.add(n);
        }
        return notes;
    }

    @Test
    public void repeatedUnnumberedLastGroupKeepsTripletDurations() {
        var notes = phrase();
        for (int i = 1; i < 10; i++)
            assertEquals(
                    1 / 3d,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(i), notes, 4),
                    1e-6);
        assertEquals(1, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(0), notes, 4), 1e-6);
    }

    @Test
    public void quarterThenThreeGroupsHaveExactAttacks() {
        var notes = phrase();
        for (int i = 1; i < 10; i++)
            assertEquals(
                    1 + (i - 1) / 3d, ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 1e-6);
    }

    @Test
    public void originalQuarterChordRetainsEveryHead() {
        var notes = phrase();
        notes.add(
                new ScoreNoteEvent(0, .041f, 4, 0, 1, .5f, false, 0, 0, 2, 1).withStemDirection(1));
        assertEquals(
                1, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(10), notes, 4), 1e-6);
        assertEquals(
                1 / 3d, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(9), notes, 4), 1e-6);
    }

    @Test
    public void noPrintedTripletsCannotProveContinuation() {
        var notes = phrase();
        for (int i = 1; i < 10; i++) notes.set(i, notes.get(i).withTupletRatio(1, 1));
        assertNotEquals(
                1 / 3d, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(9), notes, 4), 1e-6);
    }

    @Test
    public void aSingleNumberedGroupIsInsufficientForRepeatedContinuation() {
        var notes = phrase();
        for (int i = 4; i < 10; i++) notes.set(i, notes.get(i).withTupletRatio(1, 1));
        assertNotEquals(
                1 / 3d, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(9), notes, 4), 1e-6);
    }

    @Test
    public void ordinaryLongerMeterKeepsLastEighths() {
        var notes = phrase();
        assertEquals(
                .5, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(9), notes, 6), 1e-6);
    }

    @Test
    public void missingFinalAttackCannotFillBarByAssumption() {
        var notes = phrase();
        notes.remove(9);
        assertNotEquals(
                1 / 3d, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(8), notes, 4), 1e-6);
    }

    @Test
    public void writtenRestBlocksTheContinuationProof() {
        var notes = phrase();
        notes.set(7, notes.get(7).withLeadingRest(.5f));
        assertNotEquals(
                1 / 3d, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(9), notes, 4), 1e-6);
    }

    @Test
    public void conflictingShaftsInsideAnUnmarkedGroupAreAmbiguous() {
        var notes = phrase();
        notes.set(8, notes.get(8).withStemDirection(-1));
        assertNotEquals(
                1 / 3d, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(9), notes, 4), 1e-6);
    }

    @Test
    public void anotherTupletRatioCannotBecomeThreeInTwo() {
        var notes = phrase();
        notes.set(5, notes.get(5).withTupletRatio(5, 4));
        assertNotEquals(
                1 / 3d, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(9), notes, 4), 1e-6);
    }
}
