// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SevenMeterContextTest {
    static List<ScoreNoteEvent> notes(float rest) {
        var n = new ArrayList<ScoreNoteEvent>();
        n.add(new ScoreNoteEvent(0, .1f, 0, 0, 1, .4f, false, 0, 1, 2, 0, 1, rest));
        for (int i = 0; i < 7; i++)
            n.add(
                    new ScoreNoteEvent(0, .4f + i * .08f, i, 0, 1, .4f, false, 0, 2, 2, 0, 1)
                            .withTupletRatio(7, 4));
        return n;
    }

    static List<ScoreMeterChange> meter() {
        return List.of(new ScoreMeterChange(0, 3, 4));
    }

    static List<ScoreNoteEvent> run(List<ScoreNoteEvent> n) {
        return TripletRhythmDetector.contextualSevens(n, meter(), false);
    }

    @Test
    public void sevenInSixFillsProvedThreeBeatBar() {
        var n = notes(1);
        var r = run(n);
        assertEquals(n.get(0), r.get(0));
        for (int i = 1; i < 8; i++) assertEquals(n.get(i).withTupletRatio(7, 6), r.get(i));
    }

    @Test
    public void existingSevenInFourCompleteBarStays() {
        var n = notes(1.5f);
        assertEquals(n, run(n));
    }

    @Test
    public void missingMeterAbstains() {
        var n = notes(1);
        assertEquals(n, TripletRhythmDetector.contextualSevens(n, List.of(), false));
    }

    @Test
    public void conflictingMeterAbstains() {
        var n = notes(1);
        assertEquals(
                n,
                TripletRhythmDetector.contextualSevens(
                        n,
                        List.of(new ScoreMeterChange(0, 3, 4), new ScoreMeterChange(0, 4, 4)),
                        false));
    }

    @Test
    public void pickupIsNotPadded() {
        var n = notes(1);
        assertEquals(n, TripletRhythmDetector.contextualSevens(n, meter(), true));
    }

    @Test
    public void sixMembersCannotStandForSeven() {
        var n = new ArrayList<>(notes(1));
        n.remove(7);
        assertEquals(n, run(n));
    }

    @Test
    public void futureMeterCannotBeBorrowed() {
        var n = notes(1);
        assertEquals(
                n,
                TripletRhythmDetector.contextualSevens(
                        n, List.of(new ScoreMeterChange(1, 3, 4)), false));
    }

    @Test
    public void mixedBeamValuesAbstain() {
        var n = new ArrayList<>(notes(1));
        n.set(
                4,
                new ScoreNoteEvent(0, .64f, 3, 0, 1, .4f, false, 0, 3, 2, 0, 1)
                        .withTupletRatio(7, 4));
        assertEquals(n, run(n));
    }

    @Test
    public void overfullBarIsNotChanged() {
        var n = notes(2);
        assertEquals(n, run(n));
    }

    @Test
    public void explicitSevenInSixRemains() {
        var n = run(notes(1));
        assertEquals(n, run(n));
    }
}
