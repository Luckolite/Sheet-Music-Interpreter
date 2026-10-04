// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete triplet bar with one real rest and independent closing quarters. */
public class SingleRestTripletClockTest {
    private List<ScoreNoteEvent> original(boolean same) {
        var n = new ArrayList<ScoreNoteEvent>();
        float[] positions = {.03f, .105f, .205f, .28f, .355f, .46f, .535f, .61f, .715f, .86f, .96f};
        for (int i = 0; i < positions.length; i++)
            n.add(
                    new ScoreNoteEvent(
                            0,
                            positions[i],
                            i % 4,
                            0,
                            2,
                            .5f,
                            false,
                            0,
                            1,
                            2,
                            0,
                            3,
                            i == 8 ? 1 / 3f : 0,
                            0,
                            30,
                            false,
                            0,
                            false,
                            0,
                            0,
                            2,
                            1));
        n.add(new ScoreNoteEvent(0, .785f, 7, 0, 2, .45f, false, 0, 0, 2, 1).withStemDirection(1));
        n.add(
                new ScoreNoteEvent(0, .785f, 8, 0, 2, .4f, false, 0, 0, 2, 1)
                        .withStemDirection(same ? 1 : -1));
        return n;
    }

    private ScoreNoteEvent rest(ScoreNoteEvent n, float after) {
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
    public void movingVoiceUsesTheCompletePrintedClock() {
        var n = original(false);
        for (int i = 0; i < 11; i++)
            assertEquals(
                    (i + (i >= 9 ? 1 : 0)) / 3d,
                    ScoreNoteTiming.beatInMeasure(n.get(i), n, 4),
                    1e-6);
    }

    @Test
    public void movingValuesStayTripletEighths() {
        var n = original(false);
        for (int i = 0; i < 11; i++)
            assertEquals(
                    1 / 3d, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(i), n, 4), 1e-6);
    }

    @Test
    public void bothClosingQuarterVoicesBeginAtTheRest() {
        var n = original(false);
        for (int i = 11; i < 13; i++) {
            assertEquals(3, ScoreNoteTiming.beatInMeasure(n.get(i), n, 4), 1e-6);
            assertEquals(1, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(i), n, 4), 1e-6);
        }
    }

    @Test
    public void sameDirectionClosingQuartersRemainIndependent() {
        var n = original(true);
        assertNotNull(ParallelTripletClock.find(n.get(0), n, 4));
        assertEquals(10 / 3d, ScoreNoteTiming.beatInMeasure(n.get(9), n, 4), 1e-6);
    }

    @Test
    public void missingActualRestCannotFillTheBar() {
        var n = original(false);
        n.set(8, rest(n.get(8), 0));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }

    @Test
    public void ordinaryHalfBeatRestCannotFillATripletSlot() {
        var n = original(false);
        n.set(8, rest(n.get(8), .5f));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }

    @Test
    public void unknownMovingShaftStaysAmbiguous() {
        var n = original(false);
        n.set(4, n.get(4).withStemDirection(0));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }

    @Test
    public void absentPrintedRatioStaysAmbiguous() {
        var n = original(false);
        n.set(4, n.get(4).withTupletRatio(1, 1));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }

    @Test
    public void noCounterVoiceCannotProveTheRestLane() {
        var n = original(false);
        n.subList(11, 13).clear();
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }

    @Test
    public void counterAttackOutsideTheRestGapCannotAnchorTheVoice() {
        var n = original(false);
        n.set(
                11,
                new ScoreNoteEvent(0, .68f, 7, 0, 2, .45f, false, 0, 0, 2, 1).withStemDirection(1));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }

    @Test
    public void anotherMeterCannotReuseTheProof() {
        var n = original(false);
        assertNull(ParallelTripletClock.find(n.get(0), n, 3));
    }

    @Test
    public void unknownCounterShaftStaysAmbiguous() {
        var n = original(false);
        n.set(11, n.get(11).withStemDirection(0));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }
}
