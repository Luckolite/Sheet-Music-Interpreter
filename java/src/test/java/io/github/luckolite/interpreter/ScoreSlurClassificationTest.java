// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreSlurClassificationTest {
    private ScoreSlurSource source() {
        var arc =
                new ScoreSlurPages.Span(
                        "pending",
                        new ScoreSlurPages.Anchor(9, 0),
                        new ScoreSlurPages.Anchor(9, 1),
                        ScoreSlurSpan.Placement.ABOVE,
                        ScoreSlurSpan.LineType.SOLID,
                        List.of(new ScoreSlurSpan.ArcEvidence(9, .1f, .2f, .8f, .3f)));
        return ScoreSlurSource.capture(
                List.of(), List.of(arc), List.of(new ScoreSlurPages.Page(9, 2)));
    }

    private List<ScoreSlurClassification.Note> notes(int pitch, long start, long end) {
        return List.of(
                new ScoreSlurClassification.Note(0, start, 0, 60),
                new ScoreSlurClassification.Note(1, end, 0, pitch));
    }

    @Test
    public void finalDifferentPitchPromotesWithoutChangingOriginsOrInput() {
        var source = source();
        var result = ScoreSlurClassification.resolve(source, notes(62, 0, 100), Set.of());
        assertEquals(source.candidates(), result.spans());
        assertTrue(result.candidates().isEmpty());
        assertEquals(source.noteOrigins(), result.noteOrigins());
        assertTrue(source.spans().isEmpty());
    }

    @Test
    public void samePitchReattackStaysDeferred() {
        assertEquals(
                source(), ScoreSlurClassification.resolve(source(), notes(60, 0, 100), Set.of()));
    }

    @Test
    public void exactSoundingTieRemovesDuplicateArc() {
        var result =
                ScoreSlurClassification.resolve(
                        source(), notes(60, 0, 100), Set.of(new ScoreSlurClassification.Tie(0, 1)));
        assertTrue(result.spans().isEmpty());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    public void contradictedTieDoesNotEraseEvidence() {
        assertEquals(
                source(),
                ScoreSlurClassification.resolve(
                        source(),
                        notes(62, 0, 100),
                        Set.of(new ScoreSlurClassification.Tie(0, 1))));
    }

    @Test
    public void unpitchedMissingAndReversedOwnersStayDeferred() {
        for (var notes :
                List.of(
                        notes(-1, 0, 100),
                        notes(62, 100, 0),
                        notes(62, 100, 100),
                        List.<ScoreSlurClassification.Note>of()))
            assertEquals(source(), ScoreSlurClassification.resolve(source(), notes, Set.of()));
    }

    @Test
    public void originalOwnersSurviveReorderAndInsertedNotes() {
        var source = source().edited(List.of(1, -1, 0));
        var result =
                ScoreSlurClassification.resolve(
                        source,
                        List.of(
                                new ScoreSlurClassification.Note(2, 0, 0, 60),
                                new ScoreSlurClassification.Note(0, 100, 0, 62)),
                        Set.of());
        assertEquals(2, result.resolve().resolved().get(0).startOwner());
        assertEquals(0, result.resolve().resolved().get(0).endOwner());
    }

    @Test
    public void deletedOriginalOwnerCannotBorrowInsertedNote() {
        var source = source().edited(List.of(-1, 1));
        assertEquals(source, ScoreSlurClassification.resolve(source, notes(62, 0, 100), Set.of()));
    }

    @Test
    public void graceOrderingCanProveAnEqualTickPhrase() {
        var result =
                ScoreSlurClassification.resolve(
                        source(),
                        List.of(
                                new ScoreSlurClassification.Note(0, 100, -1, 60),
                                new ScoreSlurClassification.Note(1, 100, 0, 62)),
                        Set.of());
        assertEquals(1, result.spans().size());
    }

    @Test
    public void confirmedIntentSurvivesLaterSamePitchEdit() {
        var source = ScoreSlurClassification.resolve(source(), notes(62, 0, 100), Set.of());
        assertSame(source, ScoreSlurClassification.resolve(source, notes(60, 0, 100), Set.of()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateAssembledOwnershipFails() {
        ScoreSlurClassification.resolve(
                source(),
                List.of(
                        new ScoreSlurClassification.Note(0, 0, 0, 60),
                        new ScoreSlurClassification.Note(0, 100, 0, 62)),
                Set.of());
    }

    @Test(expected = IllegalArgumentException.class)
    public void staleAssembledTieFails() {
        ScoreSlurClassification.resolve(
                source(), notes(62, 0, 100), Set.of(new ScoreSlurClassification.Tie(0, 2)));
    }
}
