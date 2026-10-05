// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original pure-helper source-index/display/clock controls; no score or app APIs. */
public class TypedScorePlacementMapTest {
    private static ScoreNoteEvent note(int measure, int step, ScoreNoteEvent.Kind kind) {
        return new ScoreNoteEvent(measure, .25f, step, 0, 1, .3f, false, 0, 0, 2, 1)
                .withClef(ScoreNoteEvent.CLEF_TREBLE)
                .withKind(kind);
    }

    private static ScoreNoteEvent pitched() {
        return note(0, 0, ScoreNoteEvent.Kind.PITCHED);
    }

    private static ScoreNoteEvent unpitched() {
        return note(0, 0, ScoreNoteEvent.Kind.UNPITCHED);
    }

    private static Map<Integer, ScoreNoteTiming.WrittenPlacement> graceClock() {
        return Map.of(
                0,
                new ScoreNoteTiming.WrittenPlacement(0, .5, 1, 0, false, 10),
                1,
                new ScoreNoteTiming.WrittenPlacement(0, 1, -1, -1, false, 0));
    }

    @Test
    public void renderedOwnersRejectPitchedReplacementOfSameDisplayUnpitched() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScorePlacementMap.renderedOwners(List.of(unpitched()), List.of(pitched())));
    }

    @Test
    public void editOwnersRejectKindSubstitutionAtTheSameWrittenSlot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScorePlacementMap.editOwners(List.of(unpitched()), List.of(pitched())));
    }

    @Test
    public void unpitchedDisplayIdentityIgnoresClefAndOctaveMetadata() {
        var u = unpitched().withOctaveShift(1);
        var visible = u.withClef(ScoreNoteEvent.CLEF_UNKNOWN).withOctaveShift(0);
        assertEquals(List.of(0), ScorePlacementMap.renderedOwners(List.of(u), List.of(visible)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.renderedOwners(
                                List.of(u),
                                List.of(
                                        note(0, 7, ScoreNoteEvent.Kind.UNPITCHED)
                                                .withClef(ScoreNoteEvent.CLEF_UNKNOWN))));
    }

    @Test
    public void unpitchedDisplayEditsCannotWrapByATonalOctave() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.editOwners(
                                List.of(unpitched()),
                                List.of(note(0, 7, ScoreNoteEvent.Kind.UNPITCHED))));
    }

    @Test
    public void equalUnpitchedRecordsNeedExplicitOwnersEvenWithEqualClocks() {
        var u = unpitched();
        var p = new ScoreRhythmProjection.Placement(0, .5, true);
        assertThrows(
                IllegalArgumentException.class,
                () -> ScorePlacementMap.select(List.of(u, u), List.of(u), Map.of(0, p, 1, p)));
    }

    @Test
    public void aPartialClockDoesNotHideAmbiguousUnpitchedOccurrence() {
        var u = unpitched();
        var p = new ScoreRhythmProjection.Placement(0, .5, true);
        assertThrows(
                IllegalArgumentException.class,
                () -> ScorePlacementMap.select(List.of(u, u), List.of(u), Map.of(0, p)));
    }

    @Test
    public void explicitOwnersKeepDistinctEqualRecordClocksAndSelectionOrder() {
        var u = unpitched();
        var a = new ScoreRhythmProjection.Placement(0, .5, true);
        var b = new ScoreRhythmProjection.Placement(2, .25, true);
        var clock = Map.of(0, a, 1, b);
        assertEquals(Map.of(0, b, 1, a), ScorePlacementMap.byOwners(clock, 2, List.of(1, 0)));
        assertEquals(
                clock,
                ScorePlacementMap.edited(List.of(u, u), List.of(u, u), clock, List.of(0, 1)));
        assertEquals(List.of(0, 1), ScorePlacementMap.renderedOwners(List.of(u, u), List.of(u, u)));
    }

    @Test
    public void kindRetaggingInvalidatesBothClocksOnlyInItsEditedMeasure() {
        var u = unpitched();
        var p = note(1, 2, ScoreNoteEvent.Kind.PITCHED);
        var playback =
                Map.of(
                        0,
                        new ScoreRhythmProjection.Placement(.25, 1, true),
                        1,
                        new ScoreRhythmProjection.Placement(.5, 2, true));
        var written =
                Map.of(
                        0,
                        new ScoreNoteTiming.WrittenPlacement(.25, 1, -1, -1, false, 0),
                        1,
                        new ScoreNoteTiming.WrittenPlacement(.5, 2, -1, -1, false, 0));
        var result =
                ScorePlacementMap.editedClocks(
                        List.of(u, p),
                        List.of(u.withKind(ScoreNoteEvent.Kind.PITCHED), p),
                        playback,
                        written,
                        List.of(0, 1));
        assertEquals(Map.of(1, playback.get(1)), result.playback());
        assertEquals(Map.of(1, written.get(1)), result.written());
    }

    @Test
    public void writtenGraceRejectsAnUnpitchedPrincipal() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.validatedWritten(
                                graceClock(),
                                List.of(
                                        pitched().withArticulations(NoteOrnament.GRACE),
                                        unpitched())));
    }

    @Test
    public void writtenGraceRejectsAnUnpitchedGraceOwner() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScorePlacementMap.validatedWritten(
                                graceClock(),
                                List.of(
                                        unpitched().withArticulations(NoteOrnament.GRACE),
                                        pitched())));
    }

    @Test
    public void existingPitchedGraceAndTonalOctaveEditContractsRemain() {
        assertEquals(
                graceClock(),
                ScorePlacementMap.validatedWritten(
                        graceClock(),
                        List.of(pitched().withArticulations(NoteOrnament.GRACE), pitched())));
        assertEquals(
                List.of(0),
                ScorePlacementMap.editOwners(
                        List.of(pitched()), List.of(note(0, 7, ScoreNoteEvent.Kind.PITCHED))));
        assertEquals(
                List.of(0),
                ScorePlacementMap.renderedOwners(List.of(pitched()), List.of(pitched())));
    }
}
