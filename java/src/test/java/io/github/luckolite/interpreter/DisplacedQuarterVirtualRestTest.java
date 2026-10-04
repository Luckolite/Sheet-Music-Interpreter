// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original displaced quarter voice sharing a written column with a triplet rest. */
public class DisplacedQuarterVirtualRestTest {
    private TripletRhythmDetector.Rhythm read(
            float offset, boolean doubled, boolean numeral, boolean rest) {
        var notes = new ArrayList<ScoreNoteEvent>();
        notes.add(RestTripletTest.note(.3125f, 0, 0));
        notes.add(RestTripletTest.note(.375f, 0, 0));
        notes.add(
                new ScoreNoteEvent(0, .25f + offset, 5, 0, 1, .38f, false, 0, 0, 2, 1, 1)
                        .withStemDirection(-1));
        if (doubled)
            notes.add(
                    new ScoreNoteEvent(0, .25f + offset, 6, 0, 1, .34f, false, 0, 0, 2, 1, 1)
                            .withStemDirection(1));
        byte[] gray = RestTripletTest.ink(false);
        if (!numeral) Arrays.fill(gray, (byte) 255);
        return TripletRhythmDetector.withRests(
                notes,
                rest ? List.of(RestTripletTest.rest(.25f)) : List.of(),
                RestTripletTest.BARS,
                gray,
                RestTripletTest.W,
                RestTripletTest.H);
    }

    private void marked(TripletRhythmDetector.Rhythm r) {
        assertEquals(1. / 6, r.rests().get(0).durationBeats(), .00001);
        assertEquals(3, r.notes().get(0).tupletDivisor());
        assertEquals(3, r.notes().get(1).tupletDivisor());
        for (var n : r.notes().subList(2, r.notes().size())) {
            assertEquals(1, n.tupletDivisor());
            assertEquals(1, n.unbeamedDurationBeats(), .00001);
        }
    }

    @Test
    public void rightDisplacedQuarterKeepsTheRestColumn() {
        marked(read(.014f, false, true, true));
    }

    @Test
    public void leftDisplacedQuarterKeepsTheRestColumn() {
        marked(read(-.014f, false, true, true));
    }

    @Test
    public void twoIndependentQuarterHeadsKeepTheirValues() {
        marked(read(.014f, true, true, true));
    }

    @Test
    public void exactColumnKeepsExistingGrouping() {
        marked(read(0, false, true, true));
    }

    @Test
    public void aSeparateQuarterAttackStillBlocksTheGroup() {
        var r = read(.023f, false, true, true);
        assertEquals(.25, r.rests().get(0).durationBeats(), .00001);
        assertEquals(1, r.notes().get(0).tupletDivisor());
    }

    @Test
    public void aMissingNumeralCannotInventTriplets() {
        var r = read(.014f, true, false, true);
        assertEquals(.25, r.rests().get(0).durationBeats(), .00001);
        assertTrue(r.notes().stream().allMatch(n -> n.tupletDivisor() == 1));
    }

    @Test
    public void soundingOnlyColumnsKeepTheirSpacingRule() {
        var r = read(.014f, true, true, false);
        assertTrue(r.rests().isEmpty());
        assertTrue(r.notes().stream().allMatch(n -> n.tupletDivisor() == 1));
    }
}
