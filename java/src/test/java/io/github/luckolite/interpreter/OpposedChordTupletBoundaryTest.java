// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpposedChordTupletBoundaryTest {
    private static List<ScoreNoteEvent> apply(List<ScoreNoteEvent> notes, byte[] ink) {
        return TripletRhythmDetector.apply(
                notes, List.of(new MeasureRegion(0, 1, .1f, .5f)), ink, 420, 400);
    }

    private static void ordinary(List<ScoreNoteEvent> notes) {
        for (var n : notes)
            assertEquals("Unexpected tuplet at " + n.positionInMeasure(), 1, n.tupletDivisor());
    }

    @Test
    public void twoChordAttacksAreNotThreeTupletAttacks() {
        var notes = new ArrayList<>(OpposedChordTripletOwnershipTest.chords(true));
        notes.removeIf(n -> n.stemDirection() == 1 && n.positionInMeasure() > .7f);
        var ink = OpposedChordTripletOwnershipTest.ink(true);
        for (int y = 160; y < 240; y++)
            for (int x = 288; x < 315; x++) ink[y * 420 + x] = (byte) 255;
        ordinary(apply(notes, ink));
    }

    @Test
    public void chordHeadsCannotReplaceMissingPrintedNumeral() {
        var ink = OpposedChordTripletOwnershipTest.ink(true);
        for (int y = 135; y < 165; y++)
            for (int x = 230; x < 251; x++) ink[y * 420 + x] = (byte) 255;
        ordinary(apply(OpposedChordTripletOwnershipTest.chords(true), ink));
    }

    @Test
    public void aDifferentInputOrderPreservesChordOwnership() {
        var notes = new ArrayList<>(OpposedChordTripletOwnershipTest.chords(true));
        Collections.reverse(notes);
        OpposedChordTripletOwnershipTest.assertOwned(
                apply(notes, OpposedChordTripletOwnershipTest.ink(true)));
    }

    @Test
    public void existingTupletMarksArePreserved() {
        var notes = OpposedChordTripletOwnershipTest.chords(true);
        var once = apply(notes, OpposedChordTripletOwnershipTest.ink(true));
        var twice = apply(once, OpposedChordTripletOwnershipTest.ink(true));
        assertEquals(once, twice);
    }

    @Test
    public void mixedSingleHeadsAndChordsStillHaveThreeAttacks() {
        var notes = new ArrayList<>(OpposedChordTripletOwnershipTest.chords(true));
        notes.removeIf(
                n ->
                        n.stemDirection() == 1
                                && n.staffStep() == 13
                                && n.positionInMeasure() > .5f
                                && n.positionInMeasure() < .65f);
        var ink = OpposedChordTripletOwnershipTest.ink(true);
        for (int y = 217; y <= 223; y++)
            for (int x = 234; x <= 246; x++) ink[y * 420 + x] = (byte) 255;
        var marked = apply(notes, ink);
        for (var n : marked) assertEquals(n.stemDirection() == 1 ? 3 : 1, n.tupletDivisor());
    }

    @Test
    public void anOrdinaryQuarterChordDoesNotBorrowTheUpperNumeral() {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (var n : OpposedChordTripletOwnershipTest.chords(false))
            notes.add(
                    n.stemDirection() == 1
                            ? n
                            : new ScoreNoteEvent(
                                            0,
                                            n.positionInMeasure(),
                                            n.staffStep(),
                                            0,
                                            1,
                                            n.pageY(),
                                            false,
                                            0,
                                            0)
                                    .withStemDirection(-1));
        var ink = OpposedChordTripletOwnershipTest.ink(false);
        for (int y = 306; y <= 312; y++)
            for (int x = 170; x <= 270; x++) ink[y * 420 + x] = (byte) 255;
        OpposedChordTripletOwnershipTest.assertOwned(apply(notes, ink));
    }
}
