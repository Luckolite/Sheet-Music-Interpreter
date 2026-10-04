// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original explicit triplet voice with rests beneath three ordinary quarter attacks. */
public class ParallelRestTripletClockTest {
    private static List<ScoreNoteEvent> original() {
        var notes = new ArrayList<ScoreNoteEvent>();
        float[] positions = {.03f, .10f, .27f, .43f, .50f, .65f, .74f, .89f, .96f};
        for (int i = 0; i < 9; i++)
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            positions[i],
                            i % 3,
                            0,
                            2,
                            .5f,
                            false,
                            0,
                            1,
                            2,
                            0,
                            3,
                            i == 2 || i == 4 || i == 6 ? 1 / 3f : 0,
                            0,
                            30,
                            false,
                            0,
                            false,
                            0,
                            0,
                            2,
                            -1));
        for (float x : new float[] {.35f, .58f, .815f})
            notes.add(
                    new ScoreNoteEvent(0, x, 8, 0, 2, .5f, false, 0, 0, 2, 1).withStemDirection(1));
        return notes;
    }

    private static ScoreNoteEvent rest(ScoreNoteEvent n, float after) {
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
    public void explicitRestSlotsDoNotDistortFirstTriplet() {
        var notes = original();
        double[] onsets = {0, 1 / 3d, 2 / 3d, 4 / 3d, 5 / 3d, 7 / 3d, 8 / 3d, 10 / 3d, 11 / 3d};
        for (int i = 0; i < 9; i++) {
            assertEquals(onsets[i], ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 1e-6);
            assertEquals(
                    1 / 3d,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(i), notes, 4),
                    1e-6);
        }
    }

    @Test
    public void opposingQuartersStartAtPrintedRestSlots() {
        var notes = original();
        for (int i = 9; i < 12; i++) {
            assertEquals(i - 8, ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 1e-6);
            assertEquals(
                    1, ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(i), notes, 4), 1e-6);
        }
    }

    @Test
    public void missingPrintedRatioCannotProveRestVoice() {
        var notes = original();
        notes.set(4, notes.get(4).withTupletRatio(1, 1));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void ordinaryHalfBeatRestIsNotATripletSlot() {
        var notes = original();
        notes.set(4, rest(notes.get(4), .5f));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void noOpposingVoiceCannotProveRestOwnership() {
        var notes = original();
        notes.subList(9, 12).clear();
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void incompleteOpposingVoiceCannotProveWholeBar() {
        var notes = original();
        notes.remove(11);
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void missingRestSlotCannotFillBarByHeadCount() {
        var notes = original();
        notes.set(6, rest(notes.get(6), 0));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void unknownMovingShaftIsAmbiguous() {
        var notes = original();
        notes.set(4, notes.get(4).withStemDirection(0));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void counterVoiceOutsideItsRestGapRemainsIndependent() {
        var notes = original();
        notes.set(
                9,
                new ScoreNoteEvent(0, .22f, 8, 0, 2, .5f, false, 0, 0, 2, 1).withStemDirection(1));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void anotherMeterCannotReuseThisProof() {
        var notes = original();
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 3));
    }
}
