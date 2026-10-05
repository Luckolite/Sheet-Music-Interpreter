// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic written grace clocks; unpitched sound ownership is never inferred. */
public final class UnpitchedGraceTimingTest {
    private static final String MESSAGE =
            "Unpitched grace ownership requires an explicit realization";

    private static ScoreNoteEvent note(int bar, float x, ScoreNoteEvent.Kind kind) {
        return new ScoreNoteEvent(bar, x, 2, 0, 1, .4f, false, 0, 0, 2, 4).withKind(kind);
    }

    private static ScoreNoteEvent grace(int bar, float x, ScoreNoteEvent.Kind kind) {
        return new ScoreNoteEvent(bar, x, 2, 0, 1, .4f, false, 0, 2, 2, 0)
                .withArticulations(NoteOrnament.GRACE)
                .withKind(kind);
    }

    private static String rejected(Runnable operation) {
        try {
            operation.run();
            fail("Unsupported raw unpitched grace ownership must reject before timing");
        } catch (IllegalArgumentException error) {
            assertEquals(MESSAGE, error.getMessage());
            return error.getMessage();
        }
        throw new AssertionError("Expected rejection");
    }

    private static void rejectEveryOwnedTiming(List<ScoreNoteEvent> notes) {
        rejectEveryOwnedTiming(notes, -1);
    }

    private static void rejectEveryOwnedTiming(List<ScoreNoteEvent> notes, int ordinaryPrincipal) {
        var before = new ArrayList<>(notes);
        for (int mode = 0; mode < 2; mode++) {
            try (var scope = mode == 0 ? null : ScoreNoteTiming.beginTimingSession()) {
                for (int index = 0; index < notes.size(); index++) {
                    var target = notes.get(index);
                    rejected(() -> ScoreNoteTiming.beatInMeasure(target, notes, 4));
                    rejected(() -> ScoreNoteTiming.resolvedWrittenDurationBeats(target, notes, 4));
                    if (index != ordinaryPrincipal)
                        rejected(() -> ScoreNoteTiming.writtenPlacement(target, notes, 4));
                    else {
                        var written = new ScoreNoteTiming.WrittenPlacement(0, 4, -1, -1, false, 0);
                        assertEquals(
                                "An ordinary U written metric does not borrow grace sound ownership",
                                written,
                                ScoreNoteTiming.writtenPlacement(target, notes, 4).orElseThrow());
                        assertEquals(
                                Map.of(index, written),
                                ScorePlacementMap.validatedWritten(Map.of(index, written), notes));
                    }
                }
            }
        }
        assertEquals(before, notes);
        for (int i = 0; i < notes.size(); i++)
            assertSame("Source occurrence must remain intact", before.get(i), notes.get(i));
    }

    @Test
    public void rawUnpitchedPrefixCannotBorrowOrShiftPitchedPrincipal() {
        rejectEveryOwnedTiming(
                List.of(
                        grace(0, .1f, ScoreNoteEvent.Kind.UNPITCHED),
                        note(0, .3f, ScoreNoteEvent.Kind.PITCHED)));
    }

    @Test
    public void rawUnpitchedTerminalCannotBorrowOrShortenPitchedPrincipal() {
        rejectEveryOwnedTiming(
                List.of(
                        note(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        grace(0, .8f, ScoreNoteEvent.Kind.UNPITCHED)));
    }

    @Test
    public void mixedGraceGroupDoesNotHideRawUnpitchedAttackBehindPitchedGrace() {
        rejectEveryOwnedTiming(
                List.of(
                        grace(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        grace(0, .2f, ScoreNoteEvent.Kind.UNPITCHED),
                        note(0, .3f, ScoreNoteEvent.Kind.PITCHED)));
    }

    @Test
    public void rawUnpitchedOrphanRejectsInsteadOfAcquiringGeometryOrDisappearing() {
        rejectEveryOwnedTiming(List.of(grace(0, .4f, ScoreNoteEvent.Kind.UNPITCHED)));
    }

    @Test
    public void wholeSourcePreflightRejectsRawUnpitchedGraceInAnotherBar() {
        rejectEveryOwnedTiming(
                List.of(
                        note(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        grace(1, .8f, ScoreNoteEvent.Kind.UNPITCHED)));
    }

    @Test
    public void pitchedPrefixCannotInferAnUnpitchedPrincipalOwner() {
        rejectEveryOwnedTiming(
                List.of(
                        grace(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        note(0, .3f, ScoreNoteEvent.Kind.UNPITCHED)),
                1);
    }

    @Test
    public void pitchedTerminalCannotInferAnUnpitchedPrincipalOwner() {
        rejectEveryOwnedTiming(
                List.of(
                        note(0, .1f, ScoreNoteEvent.Kind.UNPITCHED),
                        grace(0, .8f, ScoreNoteEvent.Kind.PITCHED)),
                0);
    }

    @Test
    public void bothGraceSidesCannotBorrowAnUnpitchedPrincipalClock() {
        rejectEveryOwnedTiming(
                List.of(
                        grace(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        note(0, .3f, ScoreNoteEvent.Kind.UNPITCHED),
                        grace(0, .8f, ScoreNoteEvent.Kind.PITCHED)),
                1);
    }

    @Test
    public void retainedTargetAndPrincipalIndicesRejectWithSameOwnershipContract() {
        for (boolean targetUnpitched : List.of(true, false)) {
            var notes =
                    List.of(
                            grace(
                                    0,
                                    .1f,
                                    targetUnpitched
                                            ? ScoreNoteEvent.Kind.UNPITCHED
                                            : ScoreNoteEvent.Kind.PITCHED),
                            note(
                                    0,
                                    .3f,
                                    targetUnpitched
                                            ? ScoreNoteEvent.Kind.PITCHED
                                            : ScoreNoteEvent.Kind.UNPITCHED));
            var grace = new ScoreNoteTiming.WrittenPlacement(0, .25, 1, 0, false, 6.25);
            var principal = new ScoreNoteTiming.WrittenPlacement(0, 4, -1, -1, false, 0);
            String map =
                    rejected(
                            () ->
                                    ScorePlacementMap.validatedWritten(
                                            Map.of(0, grace, 1, principal), notes));
            String timing =
                    rejected(() -> ScoreNoteTiming.writtenPlacement(notes.get(0), notes, 4));
            assertEquals(map, timing);
            assertEquals(
                    targetUnpitched ? ScoreNoteEvent.Kind.UNPITCHED : ScoreNoteEvent.Kind.PITCHED,
                    notes.get(0).kind());
        }
    }

    @Test
    public void duplicateRawGraceOccurrencesRemainUnchangedAfterRepeatedSessionRejections() {
        var raw =
                grace(0, .1f, ScoreNoteEvent.Kind.UNPITCHED)
                        .withBoundaryTies(3)
                        .withTupletRatio(7, 4)
                        .withStemDirection(-1);
        var notes = new ArrayList<>(List.of(raw, raw, note(0, .3f, ScoreNoteEvent.Kind.PITCHED)));
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            for (int i = 0; i < 3; i++)
                rejected(() -> ScoreNoteTiming.beatInMeasure(notes.get(2), notes, 4));
            var clean =
                    List.of(
                            grace(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                            note(0, .3f, ScoreNoteEvent.Kind.PITCHED));
            assertClock(clean, new double[] {0, .25}, new double[] {.25, 3.75});
        }
        assertEquals(3, notes.size());
        assertSame(raw, notes.get(0));
        assertSame(raw, notes.get(1));
        assertEquals(3, raw.boundaryTies());
        assertEquals(7, raw.tupletDivisor());
        assertEquals(4, raw.tupletNormalNotes());
        assertEquals(-1, raw.stemDirection());
        assertEquals(NoteOrnament.GRACE, raw.articulations());
    }

    private static void assertClock(
            List<ScoreNoteEvent> notes, double[] starts, double[] durations) {
        for (int i = 0; i < notes.size(); i++) {
            assertEquals(starts[i], ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4), 0);
            assertEquals(
                    durations[i],
                    ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(i), notes, 4),
                    0);
        }
    }

    @Test
    public void ordinaryUnpitchedWrittenRhythmRemainsValidAndRetainsBothOccurrenceIndices() {
        var raw = note(0, .1f, ScoreNoteEvent.Kind.UNPITCHED);
        var notes = List.of(raw, raw);
        assertClock(notes, new double[] {0, 0}, new double[] {4, 4});
        var ordinary = new ScoreNoteTiming.WrittenPlacement(0, 4, -1, -1, false, 0);
        var retained = ScorePlacementMap.validatedWritten(Map.of(0, ordinary, 1, ordinary), notes);
        assertEquals(Set.of(0, 1), retained.keySet());
        assertEquals(ordinary, ScoreNoteTiming.writtenPlacement(raw, notes, 4).orElseThrow());
    }

    @Test
    public void unrelatedOrdinaryUnpitchedBarDoesNotChangePitchedGraceClocks() {
        var notes =
                List.of(
                        grace(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        note(0, .3f, ScoreNoteEvent.Kind.PITCHED),
                        note(1, .1f, ScoreNoteEvent.Kind.UNPITCHED));
        assertClock(notes, new double[] {0, .25, 0}, new double[] {.25, 3.75, 4});
        assertEquals(
                new ScoreNoteTiming.WrittenPlacement(0, .25, 1, 0, false, 6.25),
                ScoreNoteTiming.writtenPlacement(notes.get(0), notes, 4).orElseThrow());
    }

    @Test
    public void legacyPitchedBeforeAfterAndBothSideClocksRemainExactWithAndWithoutSession() {
        var before =
                List.of(
                        grace(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        grace(0, .2f, ScoreNoteEvent.Kind.PITCHED),
                        note(0, .3f, ScoreNoteEvent.Kind.PITCHED));
        var after =
                List.of(
                        note(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        grace(0, .8f, ScoreNoteEvent.Kind.PITCHED),
                        grace(0, .9f, ScoreNoteEvent.Kind.PITCHED));
        var both =
                List.of(
                        grace(0, .1f, ScoreNoteEvent.Kind.PITCHED),
                        note(0, .3f, ScoreNoteEvent.Kind.PITCHED),
                        grace(0, .8f, ScoreNoteEvent.Kind.PITCHED),
                        grace(0, .9f, ScoreNoteEvent.Kind.PITCHED));
        for (int mode = 0; mode < 2; mode++)
            try (var session = mode == 0 ? null : ScoreNoteTiming.beginTimingSession()) {
                assertClock(before, new double[] {0, .125, .25}, new double[] {.125, .125, 3.75});
                assertClock(after, new double[] {0, 3.75, 3.875}, new double[] {3.75, .125, .125});
                assertClock(
                        both,
                        new double[] {0, .25, 3.75, 3.875},
                        new double[] {.25, 3.5, .125, .125});
                assertEquals(
                        new ScoreNoteTiming.WrittenPlacement(0, 4, -1, -1, false, 0),
                        ScoreNoteTiming.writtenPlacement(both.get(1), both, 4).orElseThrow());
            }
    }
}
