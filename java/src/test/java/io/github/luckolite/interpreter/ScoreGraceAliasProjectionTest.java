// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original dense-owner projection controls for complete retained grace groups. */
public final class ScoreGraceAliasProjectionTest {
    private static ScoreNoteEvent note(int staff, int step, boolean grace) {
        var value =
                new ScoreNoteEvent(
                                0,
                                grace ? .4f : .5f,
                                step,
                                staff,
                                2,
                                .3f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_NATURAL,
                                grace ? .25f : 1f)
                        .withClef(ScoreNoteEvent.CLEF_TREBLE);
        return grace ? value.withArticulations(NoteOrnament.GRACE) : value;
    }

    private static List<ScoreNoteEvent> notes() {
        return List.of(note(0, 2, true), note(0, 0, false), note(1, 2, true), note(1, 0, false));
    }

    private static Map<Integer, ScoreNoteTiming.WrittenPlacement> written() {
        return Map.of(
                0,
                new ScoreNoteTiming.WrittenPlacement(1, .25, 1, 0, false, 10),
                1,
                new ScoreNoteTiming.WrittenPlacement(1, 1, -1, -1, false, 0),
                2,
                new ScoreNoteTiming.WrittenPlacement(1, .25, 3, 0, false, 10),
                3,
                new ScoreNoteTiming.WrittenPlacement(1, 1, -1, -1, false, 0));
    }

    @Test
    public void exactProducerAliasesRemapTheDiscardedPrincipalToItsDenseRepresentative() {
        var clocks = written();
        var before = new HashMap<>(clocks);
        assertThrows(
                IllegalArgumentException.class,
                () -> ScorePlacementMap.writtenByOwners(clocks, notes(), List.of(0, 3)));
        var result =
                ScorePlacementMap.writtenByOwners(
                        clocks, notes(), List.of(0, 3), List.of(List.of(0, 2), List.of(1, 3)));
        assertEquals(Set.of(0, 1), result.keySet());
        assertEquals(1, result.get(0).principalIndex());
        assertEquals(0, result.get(0).ordinal());
        assertEquals(10, result.get(0).stealPercent(), 0);
        assertEquals(.25, result.get(0).durationBeats(), 0);
        assertEquals(before, clocks);
    }

    @Test
    public void genuinelyExcludedPrincipalDoesNotAcquireAnAliasByPitch() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.writtenByOwners(
                                written(),
                                notes(),
                                List.of(0, 3),
                                List.of(List.of(0), List.of(3))));
    }

    @Test
    public void duplicatedMissingAndOutOfRangeAliasesAreRefused() {
        for (var aliases :
                List.of(
                        List.of(List.of(0, 2), List.of(0, 1, 3)),
                        List.of(List.of(0, 2), List.of(1)),
                        List.of(List.of(0, 2), List.of(1, 3, 4))))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ScorePlacementMap.writtenByOwners(
                                    written(), notes(), List.of(0, 3), aliases));
    }

    @Test
    public void conflictingPrincipalClocksCannotBeHiddenByAnAliasRoster() {
        var clocks = new HashMap<>(written());
        clocks.put(3, new ScoreNoteTiming.WrittenPlacement(2, 1, -1, -1, false, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.writtenByOwners(
                                clocks,
                                notes(),
                                List.of(0, 3),
                                List.of(List.of(0, 2), List.of(1, 3))));
    }

    @Test
    public void coalescedGracesMustHaveTheSameSelectedPrincipalAndOrdinals() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.writtenByOwners(
                                written(),
                                notes(),
                                List.of(0, 1, 3),
                                List.of(List.of(0, 2), List.of(1), List.of(3))));
        // Retaining both independent ordinal-zero graces while merging only principals is
        // ambiguous.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.writtenByOwners(
                                written(),
                                notes(),
                                List.of(0, 2, 3),
                                List.of(List.of(0), List.of(2), List.of(1, 3))));
    }

    @Test
    public void strictUnmergedSelectionPreservesBothOriginalPrincipalGroups() {
        var result = ScorePlacementMap.writtenByOwners(written(), notes(), List.of(0, 1, 2, 3));
        assertEquals(written(), result);
        assertEquals(1, result.get(0).principalIndex());
        assertEquals(3, result.get(2).principalIndex());
    }
}
