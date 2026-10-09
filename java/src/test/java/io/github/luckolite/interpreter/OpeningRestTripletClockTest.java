// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rest-first triplet lane beneath an independent quarter voice. */
public class OpeningRestTripletClockTest {
    private static List<ScoreNoteEvent> original() {
        var notes = new ArrayList<ScoreNoteEvent>();
        float[] positions = {.11f, .18f, .36f, .43f, .61f, .68f, .86f, .93f};
        for (int i = 0; i < 8; i++) {
            var n =
                    new ScoreNoteEvent(0, positions[i], i % 2 + 2, 0, 2, .48f, false, 0, 1)
                            .withTupletRatio(3, 2)
                            .withStemDirection(-1);
            if (i == 0) n = n.withLeadingRest(1 / 3f);
            if (i == 1 || i == 3 || i == 5) n = after(n, 1 / 3f);
            notes.add(n);
        }
        for (float x : new float[] {.27f, .52f, .77f})
            notes.add(
                    new ScoreNoteEvent(0, x, 11, 0, 2, .4f, false, 0, 0, 2, 1)
                            .withStemDirection(1));
        return notes;
    }

    private static ScoreNoteEvent after(ScoreNoteEvent n, float after) {
        return new ScoreNoteEvent(
                n.measureIndex(),
                n.positionInMeasure(),
                n.staffStep(),
                n.staffIndex(),
                n.staffCount(),
                n.pageY(),
                n.tiedFromPrevious(),
                n.augmentationDots(),
                n.beamCount(),
                n.writtenAccidental(),
                n.unbeamedDurationBeats(),
                n.tupletDivisor(),
                after,
                n.articulations(),
                n.clefBottomDiatonic(),
                n.crossStaffBeam(),
                n.leadingRestBeats(),
                n.compactOpening(),
                n.octaveShift(),
                n.boundaryTies(),
                n.tupletNormalNotes(),
                n.stemDirection());
    }

    @Test
    public void openingRestRetainsCompleteTripletClock() {
        var notes = original();
        assertNotNull(ParallelTripletClock.find(notes.get(0), notes, 4));
        for (int i = 0; i < 8; i++) {
            assertEquals(
                    i / 2 + (i % 2 + 1) / 3d,
                    ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4),
                    1e-6);
            assertEquals(
                    1 / 3d,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(i), notes, 4),
                    1e-6);
        }
    }

    @Test
    public void upperQuarterAttacksStartInTheirPrintedRestSlots() {
        var notes = original();
        for (int i = 8; i < 11; i++) {
            assertEquals(i - 7, ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 1e-6);
            assertEquals(
                    1, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(i), notes, 4), 1e-6);
        }
    }

    @Test
    public void ordinaryOpeningHalfBeatCannotFillTripletSlot() {
        var notes = original();
        notes.set(0, notes.get(0).withLeadingRest(.5f));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void missingOpeningRestCannotInventFullBar() {
        var notes = original();
        notes.set(0, notes.get(0).withLeadingRest(0));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void interiorLeadingRestIsNotAnOpeningRest() {
        var notes = original();
        notes.set(2, notes.get(2).withLeadingRest(1 / 3f));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void missingExplicitNumeralCannotProveLane() {
        var notes = original();
        notes.set(3, notes.get(3).withTupletRatio(1, 1));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void missingQuarterCounterAttackCannotProveWholeBar() {
        var notes = original();
        notes.remove(10);
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void missingInteriorRestCannotFillBarByCount() {
        var notes = original();
        notes.set(3, after(notes.get(3), 0));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void unknownStemCannotProveRestLane() {
        var notes = original();
        notes.set(2, notes.get(2).withStemDirection(0));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void wrongMeterCannotReuseProof() {
        var notes = original();
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 3));
    }
}
