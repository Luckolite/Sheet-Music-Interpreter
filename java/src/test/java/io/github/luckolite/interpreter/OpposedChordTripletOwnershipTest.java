// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic shared-shaft chords and independent opposing attacks. */
public class OpposedChordTripletOwnershipTest {
    static List<ScoreNoteEvent> chords(boolean twoOpposingAttacks) {
        var n = new ArrayList<>(SyntheticOpposedTupletFixture.exercise(true));
        if (!twoOpposingAttacks) {
            n.remove(n.size() - 1);
            n.add(
                    new ScoreNoteEvent(
                                    0,
                                    180f / SyntheticOpposedTupletFixture.W,
                                    2,
                                    0,
                                    1,
                                    270f / SyntheticOpposedTupletFixture.H,
                                    false,
                                    0,
                                    1)
                            .withStemDirection(-1));
        }
        for (int x : new int[] {180, 240, 300})
            n.add(
                    new ScoreNoteEvent(
                                    0,
                                    x / (float) SyntheticOpposedTupletFixture.W,
                                    13,
                                    0,
                                    1,
                                    220f / SyntheticOpposedTupletFixture.H,
                                    false,
                                    0,
                                    1)
                            .withStemDirection(1));
        return n;
    }

    static byte[] ink(boolean twoOpposingAttacks) {
        var g = SyntheticOpposedTupletFixture.ink(twoOpposingAttacks);
        for (int x : new int[] {180, 240, 300})
            SyntheticOpposedTupletFixture.rect(g, x - 6, 217, x + 6, 223);
        if (!twoOpposingAttacks) {
            SyntheticOpposedTupletFixture.rect(g, 174, 257, 186, 263);
            SyntheticOpposedTupletFixture.rect(g, 174, 267, 186, 273);
            SyntheticOpposedTupletFixture.rect(g, 173, 260, 175, 311);
            SyntheticOpposedTupletFixture.rect(g, 173, 306, 265, 312);
        }
        return g;
    }

    static void assertOwned(List<ScoreNoteEvent> notes) {
        assertEquals(6, notes.stream().filter(n -> n.stemDirection() == 1).count());
        for (var n : notes) assertEquals(n.stemDirection() == 1 ? 3 : 1, n.tupletDivisor());
    }

    @Test
    public void threeSharedShaftChordAttacksOwnTheirBeamAndNumeral() {
        assertOwned(
                TripletRhythmDetector.apply(
                        chords(true),
                        List.of(new MeasureRegion(0, 1, .1f, .5f)),
                        ink(true),
                        420,
                        400));
    }

    @Test
    public void virtualRestEntryPointPreservesChordTupletOwnership() {
        var rest = new ScoreRestEvent(0, .06f, .65f, .025f, 0, 1, .5);
        var result =
                TripletRhythmDetector.withRests(
                        chords(true),
                        List.of(rest),
                        List.of(new MeasureRegion(0, 1, .1f, .5f)),
                        ink(true),
                        420,
                        400);
        assertOwned(result.notes());
        assertEquals(.5, result.rests().get(0).durationBeats(), 0);
    }

    @Test
    public void oneOpposingChordCannotBorrowTheUpperTuplet() {
        var result =
                TripletRhythmDetector.apply(
                        chords(false),
                        List.of(new MeasureRegion(0, 1, .1f, .5f)),
                        ink(false),
                        420,
                        400);
        assertTrue(
                result.stream()
                        .filter(n -> n.stemDirection() == -1)
                        .allMatch(n -> n.tupletDivisor() == 1));
    }
}
