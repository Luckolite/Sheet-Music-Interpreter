// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Literal edit projections normalize surviving grace groups only after principal transport. */
public class ScoreGraceEditProjectionTest {
    private static ScoreNoteEvent note(int measure, float position, boolean grace) {
        var value =
                new ScoreNoteEvent(
                                measure,
                                position,
                                0,
                                0,
                                1,
                                .3f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_NATURAL,
                                measure == 1 ? 4 : 1)
                        .withClef(ScoreNoteEvent.CLEF_TREBLE);
        return grace ? value.withArticulations(NoteOrnament.GRACE) : value;
    }

    private static List<ScoreNoteEvent> source() {
        return List.of(
                note(0, .2f, true), note(0, .25f, true), note(0, .3f, false), note(1, .2f, false));
    }

    private static Map<Integer, ScoreNoteTiming.WrittenPlacement> written(boolean after) {
        return Map.of(
                0,
                new ScoreNoteTiming.WrittenPlacement(1, .125, 2, 0, after, 12.5),
                1,
                new ScoreNoteTiming.WrittenPlacement(1, .125, 2, 1, after, 12.5),
                2,
                new ScoreNoteTiming.WrittenPlacement(1, 1, -1, -1, false, 0),
                3,
                new ScoreNoteTiming.WrittenPlacement(0, 4, -1, -1, false, 0));
    }

    private static Map<Integer, ScoreRhythmProjection.Placement> playback(boolean after) {
        return Map.of(
                0,
                new ScoreRhythmProjection.Placement(after ? 1.75 : 1, .125, true),
                1,
                new ScoreRhythmProjection.Placement(after ? 1.875 : 1.125, .125, true),
                2,
                new ScoreRhythmProjection.Placement(after ? 1 : 1.25, .75, true),
                3,
                new ScoreRhythmProjection.Placement(0, 4, true));
    }

    @Test
    public void deletingFirstGraceTransportsThenRenumbersBeforeAndAfterGroups() {
        for (boolean after : List.of(false, true)) {
            var notes = source();
            var owners = List.of(1, 2, 3);
            var changed = owners.stream().map(notes::get).toList();
            var projected = ScorePlacementMap.writtenByOwners(written(after), notes, owners);
            assertEquals(1, projected.get(0).ordinal());
            assertEquals(1, projected.get(0).principalIndex());
            var result =
                    ScorePlacementMap.editedClocks(
                            notes, changed, playback(after), written(after), owners);
            assertEquals(0, result.written().get(0).ordinal());
            assertEquals(1, result.written().get(0).principalIndex());
            assertEquals(25, result.written().get(0).stealPercent(), 0);
            assertEquals(after, result.written().get(0).afterGrace());
            assertEquals(.25, result.playback().get(0).durationBeats(), 0);
            assertEquals(after ? 1.75 : 1, result.playback().get(0).onsetBeats(), 0);
            assertEquals(written(after).get(3), result.written().get(2));
            assertEquals(playback(after).get(3), result.playback().get(2));
            assertEquals(
                    result.written(),
                    ScorePlacementMap.validatedWritten(result.written(), changed));
            assertThrows(UnsupportedOperationException.class, () -> result.written().clear());
        }
    }

    @Test
    public void trueAliasProjectionDoesNotAcceptIncompleteGraceOrdinals() {
        var owners = List.of(1, 2, 3);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.writtenByOwners(
                                written(false),
                                source(),
                                owners,
                                List.of(List.of(1), List.of(2), List.of(3))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.writtenByOwners(
                                written(false), source(), owners, List.of()));
    }

    @Test
    public void intermediateEditProjectionStillRefusesMissingPrincipal() {
        var owners = List.of(0, 1, 3);
        assertThrows(
                IllegalArgumentException.class,
                () -> ScorePlacementMap.writtenByOwners(written(false), source(), owners));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.editedClocks(
                                source(),
                                owners.stream().map(source()::get).toList(),
                                playback(false),
                                written(false),
                                owners));
    }

    @Test
    public void deletingEntireGraceGroupRestoresPrincipalsUnstolenClock() {
        var notes = source();
        var owners = List.of(2, 3);
        var result =
                ScorePlacementMap.editedClocks(
                        notes,
                        owners.stream().map(notes::get).toList(),
                        playback(false),
                        written(false),
                        owners);
        assertEquals(1, result.playback().get(0).onsetBeats(), 0);
        assertEquals(1, result.playback().get(0).durationBeats(), 0);
        assertFalse(result.written().get(0).grace());
        assertEquals(2, result.written().size());
    }
}
