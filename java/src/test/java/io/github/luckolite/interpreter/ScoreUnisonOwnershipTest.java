// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original literal clock/group fixtures; no score scans, inference, rendering or Android. */
public final class ScoreUnisonOwnershipTest {
    private static ScoreNoteEvent note(int measure, int staff, int step, boolean grace) {
        var value =
                new ScoreNoteEvent(
                                measure,
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
        return List.of(
                note(0, 0, 2, true),
                note(0, 0, 0, false),
                note(0, 1, 2, true),
                note(0, 1, 0, false));
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

    private static Map<Integer, ScoreRhythmProjection.Placement> playback() {
        return Map.of(
                0,
                new ScoreRhythmProjection.Placement(1, .1, true),
                1,
                new ScoreRhythmProjection.Placement(1.1, .9, true),
                2,
                new ScoreRhythmProjection.Placement(1, .1, true),
                3,
                new ScoreRhythmProjection.Placement(1.1, .9, true));
    }

    private static Optional<List<ScoreUnisonOwnership.GraceAlias>> equivalent(
            ScoreUnisonOwnership proof) {
        return proof.equivalent(1, 3, Set.of(0, 1, 2, 3), ScoreNoteEvent::staffStep);
    }

    @Test
    public void exactWholeGroupsReturnBothSourceGraceOwners() {
        var proof = new ScoreUnisonOwnership(notes(), written(), playback());
        assertEquals(
                List.of(new ScoreUnisonOwnership.GraceAlias(0, 2)),
                equivalent(proof).orElseThrow());
        assertEquals(
                List.of(new ScoreUnisonOwnership.GraceAlias(2, 0)),
                proof.equivalent(3, 1, Set.of(0, 1, 2, 3), ScoreNoteEvent::staffStep)
                        .orElseThrow());
    }

    @Test
    public void distinctPerformedPrincipalOrGraceAttacksCannotBecomeOneUnison() {
        for (int changed : List.of(2, 3)) {
            var clocks = new HashMap<>(playback());
            var old = clocks.get(changed);
            clocks.put(
                    changed,
                    new ScoreRhythmProjection.Placement(
                            old.onsetBeats() + .01,
                            old.durationBeats(),
                            old.independentDuration()));
            assertTrue(equivalent(new ScoreUnisonOwnership(notes(), written(), clocks)).isEmpty());
        }
    }

    @Test
    public void unadornedPrincipalAndGracePrincipalRemainDistinct() {
        var source = List.of(note(0, 0, 0, false), note(0, 1, 2, true), note(0, 1, 0, false));
        var clocks =
                Map.of(
                        0,
                        new ScoreNoteTiming.WrittenPlacement(1, 1, -1, -1, false, 0),
                        1,
                        new ScoreNoteTiming.WrittenPlacement(1, .25, 2, 0, false, 10),
                        2,
                        new ScoreNoteTiming.WrittenPlacement(1, 1, -1, -1, false, 0));
        // Equal performed principal clocks still cannot erase the unequal attached group.
        var played =
                Map.of(
                        0,
                        new ScoreRhythmProjection.Placement(1, 1, true),
                        1,
                        new ScoreRhythmProjection.Placement(1, .1, true),
                        2,
                        new ScoreRhythmProjection.Placement(1, 1, true));
        assertTrue(
                new ScoreUnisonOwnership(source, clocks, played)
                        .equivalent(0, 2, Set.of(0, 1, 2), ScoreNoteEvent::staffStep)
                        .isEmpty());
    }

    @Test
    public void groupPitchSideAndStolenTimeRemainPartOfTheProof() {
        var pitch = new ArrayList<>(notes());
        pitch.set(2, note(0, 1, 3, true));
        assertTrue(equivalent(new ScoreUnisonOwnership(pitch, written(), playback())).isEmpty());
        for (boolean after : List.of(false, true)) {
            var clocks = new HashMap<>(written());
            clocks.put(
                    2, new ScoreNoteTiming.WrittenPlacement(1, .25, 3, 0, after, after ? 10 : 12));
            assertTrue(equivalent(new ScoreUnisonOwnership(notes(), clocks, playback())).isEmpty());
        }
    }

    @Test
    public void missingPerformedOrSelectedGroupEvidenceRetainsBothPrincipals() {
        assertTrue(equivalent(new ScoreUnisonOwnership(notes(), written(), Map.of())).isEmpty());
        var proof = new ScoreUnisonOwnership(notes(), written(), playback());
        assertTrue(proof.equivalent(1, 3, Set.of(0, 1, 3), ScoreNoteEvent::staffStep).isEmpty());
        assertTrue(proof.equivalent(1, 3, Set.of(0, 1, 2), ScoreNoteEvent::staffStep).isEmpty());
    }

    @Test
    public void candidateOwnersMustBeDistinctSelectedPitchedPrincipals() {
        var proof = new ScoreUnisonOwnership(notes(), written(), playback());
        for (int[] pair : new int[][] {{-1, 3}, {1, 4}, {1, 1}, {0, 3}, {1, 2}})
            assertTrue(
                    proof.equivalent(
                                    pair[0], pair[1], Set.of(0, 1, 2, 3), ScoreNoteEvent::staffStep)
                            .isEmpty());
        var unpitched =
                List.of(
                        note(0, 0, 0, false).withKind(ScoreNoteEvent.Kind.UNPITCHED),
                        note(0, 1, 0, false));
        assertTrue(
                new ScoreUnisonOwnership(unpitched, Map.of(), Map.of())
                        .equivalent(0, 1, Set.of(0, 1), ScoreNoteEvent::staffStep)
                        .isEmpty());
        var differentMeasure = List.of(note(0, 0, 0, false), note(1, 1, 0, false));
        assertTrue(
                new ScoreUnisonOwnership(differentMeasure, Map.of(), Map.of())
                        .equivalent(0, 1, Set.of(0, 1), ScoreNoteEvent::staffStep)
                        .isEmpty());
    }

    @Test
    public void immutableSourceSnapshotSurvivesCallerMutation() {
        var source = new ArrayList<>(notes());
        var clocks = new HashMap<>(written());
        var played = new HashMap<>(playback());
        var proof = new ScoreUnisonOwnership(source, clocks, played);
        source.clear();
        clocks.clear();
        played.clear();
        assertEquals(
                List.of(new ScoreUnisonOwnership.GraceAlias(0, 2)),
                equivalent(proof).orElseThrow());
    }

    @Test
    public void uncertainGraceMeasureDoesNotDisableUnrelatedOrdinaryUnisons() {
        var source =
                List.of(
                        note(0, 0, 2, true),
                        note(0, 0, 0, false),
                        note(0, 1, 0, false),
                        note(1, 0, 0, false),
                        note(1, 1, 0, false));
        var proof = new ScoreUnisonOwnership(source, Map.of(), Map.of());
        assertTrue(
                proof.equivalent(1, 2, Set.of(0, 1, 2, 3, 4), ScoreNoteEvent::staffStep).isEmpty());
        assertEquals(
                List.of(),
                proof.equivalent(3, 4, Set.of(0, 1, 2, 3, 4), ScoreNoteEvent::staffStep)
                        .orElseThrow());
    }
}
