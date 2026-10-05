// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScorePlacementMapTest {
    private static ScoreNoteEvent n(float x, int step) {
        return new ScoreNoteEvent(0, x, step, 0, 1, .3f, false, 0, 0, 2, 1).withClef(30);
    }

    private static List<ScoreNoteEvent> gracePhrase() {
        return List.of(
                n(.1f, 0),
                new ScoreNoteEvent(0, .25f, -2, 0, 1, .3f, false, 0, 3, 2, 1)
                        .withClef(30)
                        .withArticulations(NoteOrnament.GRACE),
                n(.3f, 1),
                n(.7f, 2));
    }

    @Test
    public void writtenGracePrincipalAndStealRemainDistinctFromPlaybackClock() {
        var notes = gracePhrase();
        var written = ScorePlacementMap.resolveWritten(notes, 4, List.of());
        var playback = ScoreRhythmProjection.resolve(notes, notes, 4, List.of());
        assertEquals(2, written.get(1).principalIndex());
        assertEquals(0, written.get(1).ordinal());
        assertFalse(written.get(1).afterGrace());
        assertTrue(written.get(1).stealPercent() > 0);
        assertNotEquals(written.get(2).onsetBeats(), playback.get(2).onsetBeats(), 0);
        assertTrue(written.get(2).durationBeats() > playback.get(2).durationBeats());
        var selected = ScorePlacementMap.writtenByOwners(written, notes, List.of(1, 2, 3));
        assertEquals(1, selected.get(0).principalIndex());
        assertEquals(written.get(1).ordinal(), selected.get(0).ordinal());
        assertEquals(written.get(1).stealPercent(), selected.get(0).stealPercent(), 0);
    }

    @Test
    public void omittedOrUnresolvedPrincipalFailsWithoutGeometricReassignment() {
        var notes = gracePhrase();
        var written = ScorePlacementMap.resolveWritten(notes, 4, List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> ScorePlacementMap.writtenByOwners(written, notes, List.of(1, 3)));
        var unknown = List.of(n(.4f, 3).withArticulations(NoteOrnament.GRACE));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScorePlacementMap.resolveWritten(unknown, 4, List.of()));
    }

    @Test
    public void mapsRejectInvalidIndicesAndPrincipalPayloadsAndAreImmutable() {
        var notes = List.of(n(.1f, 0));
        var p = new ScoreRhythmProjection.Placement(0, 1, false);
        assertThrows(
                IllegalArgumentException.class, () -> ScorePlacementMap.validated(Map.of(1, p), 1));
        var copy = ScorePlacementMap.validated(Map.of(0, p), 1);
        assertThrows(UnsupportedOperationException.class, () -> copy.clear());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.validatedWritten(
                                Map.of(
                                        0,
                                        new ScoreNoteTiming.WrittenPlacement(
                                                Double.NaN, 1, -1, -1, false, 0)),
                                notes));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.validatedWritten(
                                Map.of(
                                        0,
                                        new ScoreNoteTiming.WrittenPlacement(
                                                0, 1, 0, 0, false, 10)),
                                notes));
    }

    @Test
    public void exactSelectionReorderingRetainsOriginalNoteIndexClocks() {
        var notes = List.of(n(.1f, 0), n(.4f, 1));
        var map =
                Map.of(
                        0,
                        new ScoreRhythmProjection.Placement(0, 1, false),
                        1,
                        new ScoreRhythmProjection.Placement(2, 1, true));
        var projected = ScorePlacementMap.select(notes, List.of(notes.get(1), notes.get(0)), map);
        assertEquals(map.get(1), projected.get(0));
        assertEquals(map.get(0), projected.get(1));
    }

    @Test
    public void trailingGraceAndLaterMeasurePrincipalsKeepGlobalIndicesAndAfterGrace() {
        var first = new ScoreNoteEvent(0, .1f, 0, 0, 1, .3f, false, 0, 0, 2, 4).withClef(30);
        var after =
                new ScoreNoteEvent(0, .8f, 1, 0, 1, .3f, false, 0, 3, 2, 1)
                        .withClef(30)
                        .withArticulations(NoteOrnament.GRACE);
        var prefix =
                new ScoreNoteEvent(1, .2f, -2, 0, 1, .3f, false, 0, 3, 2, 1)
                        .withClef(30)
                        .withArticulations(NoteOrnament.GRACE);
        var next = new ScoreNoteEvent(1, .3f, 2, 0, 1, .3f, false, 0, 0, 2, 4).withClef(30);
        var notes = List.of(first, after, prefix, next);
        var written = ScorePlacementMap.resolveWritten(notes, 4, List.of());
        assertTrue(written.get(1).afterGrace());
        assertEquals(0, written.get(1).principalIndex());
        assertEquals(4, written.get(1).onsetBeats(), 0);
        assertEquals(3, written.get(2).principalIndex());
        var reordered = ScorePlacementMap.writtenByOwners(written, notes, List.of(2, 3, 0, 1));
        assertEquals(1, reordered.get(0).principalIndex());
        assertEquals(2, reordered.get(3).principalIndex());
        assertTrue(reordered.get(3).afterGrace());
    }
}
