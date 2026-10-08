// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original generic counterpoint with a complete written prefix and an unrecognized tail. */
public class PrintedTripletVoicePrefixTest {
    private ScoreNoteEvent note(int slot, int beams, int direction, boolean printed) {
        var n =
                new ScoreNoteEvent(
                                0,
                                .08f + slot * .07f,
                                beams == 0 ? 4 : -3,
                                0,
                                4,
                                .4f,
                                false,
                                0,
                                beams,
                                2,
                                beams == 0 ? 1 : 0)
                        .withStemDirection(direction);
        return printed ? n.withTupletRatio(3, 2) : n;
    }

    private List<ScoreNoteEvent> exercise(boolean initialRest) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int slot = 0; slot < 12; slot++) {
            if (slot % 3 == 0 && slot >= 3) notes.add(note(slot, 0, 1, false));
            else if (!(initialRest && slot == 0)) {
                var n = note(slot, 1, -1, slot < (initialRest ? 9 : 3));
                if (initialRest && slot == 1) n = n.withLeadingRest(1 / 3f);
                if (initialRest && (slot == 2 || slot == 5)) n = following(n, 1 / 3f);
                notes.add(n);
            }
        }
        return notes;
    }

    private ScoreNoteEvent following(ScoreNoteEvent n, float beats) {
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
                        beats,
                        n.articulations(),
                        n.clefBottomDiatonic(),
                        n.crossStaffBeam(),
                        n.leadingRestBeats())
                .withTupletRatio(n.tupletDivisor(), n.tupletNormalNotes())
                .withStemDirection(n.stemDirection());
    }

    @Test
    public void printedFirstTripletKeepsWrittenOnsetsBesideQuarterVoice() {
        var notes = exercise(false);
        for (int i = 0; i < 3; i++) {
            assertEquals(i / 3.0, ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 1e-6);
            assertEquals(
                    1 / 3.0,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(i), notes, 4),
                    1e-6);
        }
        for (var n : notes)
            if (n.beamCount() == 0)
                assertEquals(1, ScoreNoteTiming.resolvedWrittenDurationBeats(n, notes, 4), 0);
    }

    @Test
    public void printedRestTripletsKeepTheirOwnPrefixClock() {
        var notes = exercise(true);
        double[] expected = {1 / 3.0, 2 / 3.0, 4 / 3.0, 5 / 3.0, 7 / 3.0, 8 / 3.0};
        int i = 0;
        for (var n : notes)
            if (n.tupletDivisor() == 3)
                assertEquals(expected[i++], ScoreNoteTiming.beatInMeasure(n, notes, 4), 1e-6);
        assertEquals(expected.length, i);
    }

    @Test
    public void anUnmarkedTailStillNeedsItsOwnRecognitionEvidence() {
        var notes = exercise(false);
        var tail = notes.get(notes.size() - 1);
        assertEquals(.5, ScoreNoteTiming.writtenDurationBeats(tail), 0);
        assertNull(PrintedTripletVoicePrefix.find(tail, notes, 4));
    }

    @Test
    public void anIncompletePrintedTripletIsNotACompletePrefix() {
        var notes = exercise(false);
        var target = notes.get(0);
        notes.remove(1);
        assertNull(PrintedTripletVoicePrefix.find(target, notes, 4));
    }

    @Test
    public void oppositePrintedShaftsAreRequired() {
        var notes = exercise(false);
        var target = notes.get(0);
        for (int i = 0; i < notes.size(); i++)
            if (notes.get(i).beamCount() == 0) notes.set(i, notes.get(i).withStemDirection(-1));
        assertNull(PrintedTripletVoicePrefix.find(target, notes, 4));
    }

    @Test
    public void oneQuarterIsNotAProvedCountervoice() {
        var notes = exercise(false);
        notes.removeIf(n -> n.beamCount() == 0 && n.positionInMeasure() > .4f);
        assertNull(PrintedTripletVoicePrefix.find(notes.get(0), notes, 4));
    }

    @Test
    public void missingPrintedRatioCannotBeInventedFromSpacing() {
        var notes = exercise(false);
        for (int i = 0; i < 3; i++) notes.set(i, notes.get(i).withTupletRatio(1, 1));
        assertNull(PrintedTripletVoicePrefix.find(notes.get(0), notes, 4));
    }

    @Test
    public void ordinaryEighthRestCannotBeScaledToATripletSilence() {
        var notes = exercise(true);
        notes.set(0, notes.get(0).withLeadingRest(.5f));
        assertNull(PrintedTripletVoicePrefix.find(notes.get(0), notes, 4));
    }

    @Test
    public void aLaterPrintedRunCannotPretendToStartAtTheBarline() {
        var notes = exercise(false);
        notes.set(0, notes.get(0).withTupletRatio(1, 1));
        assertNull(PrintedTripletVoicePrefix.find(notes.get(1), notes, 4));
    }

    @Test
    public void reversedInputKeepsThePrintedPrefixClock() {
        var notes = exercise(true);
        var target = notes.get(4);
        Collections.reverse(notes);
        assertEquals(5 / 3.0, ScoreNoteTiming.beatInMeasure(target, notes, 4), 1e-6);
    }

    @Test
    public void printedPrefixDoesNotGuessTheOtherVoicesStart() {
        var notes = exercise(true);
        for (var n : notes)
            if (n.beamCount() == 0) {
                var clock = ParallelTripletClock.find(n, notes, 4);
                assertNotNull(clock);
                assertTrue(Double.isNaN(clock.onset(n, notes)));
                assertEquals(1, clock.duration(n), 0);
            }
    }

    @Test
    public void laterMarkedGroupCannotEraseCompleteOpeningTriplet() {
        var notes = exercise(false);
        notes.set(7, notes.get(7).withTupletRatio(3, 2));
        notes.set(8, notes.get(8).withTupletRatio(3, 2));
        for (int i = 0; i < 3; i++)
            assertEquals(i / 3.0, ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 1e-6);
        for (var note : notes)
            if (note.beamCount() == 0)
                assertEquals(1, ScoreNoteTiming.resolvedWrittenDurationBeats(note, notes, 4), 0);
    }

    @Test
    public void laterMarkedGroupStillNeedsItsOwnStartEvidence() {
        var notes = exercise(false);
        notes.set(7, notes.get(7).withTupletRatio(3, 2));
        notes.set(8, notes.get(8).withTupletRatio(3, 2));
        assertNull(PrintedTripletVoicePrefix.find(notes.get(7), notes, 4));
        assertNull(PrintedTripletVoicePrefix.find(notes.get(8), notes, 4));
        assertEquals(1 / 3.0, ScoreNoteTiming.writtenDurationBeats(notes.get(7)), 1e-6);
    }
}
