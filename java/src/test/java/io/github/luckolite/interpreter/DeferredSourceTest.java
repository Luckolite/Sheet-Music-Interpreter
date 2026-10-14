// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeferredSourceTest {
    private ScoreSlurPages.Span arc(String id) {
        return new ScoreSlurPages.Span(
                id,
                new ScoreSlurPages.Anchor(8, 0),
                new ScoreSlurPages.Anchor(9, 1),
                ScoreSlurSpan.Placement.ABOVE,
                ScoreSlurSpan.LineType.SOLID,
                List.of(new ScoreSlurSpan.ArcEvidence(8, .1f, .2f, .8f, .3f)));
    }

    private ScoreSlurSource deferred() {
        return ScoreSlurSource.capture(
                List.of(),
                List.of(arc("pending")),
                List.of(new ScoreSlurPages.Page(8, 1), new ScoreSlurPages.Page(9, 2)));
    }

    @Test
    public void geometryDoesNotEngraveBeforeMusicalProof() {
        var source = deferred();
        assertTrue(source.spans().isEmpty());
        assertTrue(source.resolve().resolved().isEmpty());
        assertTrue(source.engravingSpans(List.of(0, 2)).isEmpty());
        assertEquals(1, source.candidates().size());
        assertTrue(source.hasOriginalOwners());
    }

    @Test
    public void reorderInsertionAndDeletionRetainOriginalGeometry() {
        var source = deferred();
        var edited = source.edited(List.of(2, -1, 0)).edited(List.of(1, 0));
        assertEquals(source.candidates(), edited.candidates());
        assertEquals(Optional.of(new ScoreSlurPages.Anchor(9, 1)), edited.noteOrigins().get(1));
        assertTrue(edited.resolve().resolved().isEmpty());
        assertEquals(3, source.noteOrigins().size());
    }

    @Test
    public void joinDeduplicatesCrossPageCandidateWithoutRenumbering() {
        var source = deferred();
        var first =
                new ScoreSlurSource(
                        List.of(), source.noteOrigins().subList(0, 1), source.candidates());
        var second =
                new ScoreSlurSource(
                        List.of(), source.noteOrigins().subList(1, 3), source.candidates());
        assertEquals(source, ScoreSlurSource.join(List.of(first, second)));
    }

    @Test
    public void unownedCandidateIsRetainedAcrossJoin() {
        var source =
                new ScoreSlurSource(
                        List.of(), ScoreSlurSource.empty(2).noteOrigins(), List.of(arc("pending")));
        var joined = ScoreSlurSource.join(List.of(source, ScoreSlurSource.empty(1)));
        assertEquals(source.candidates(), joined.candidates());
        assertFalse(joined.hasOriginalOwners());
    }

    @Test
    public void confirmedAndDeferredIdsCannotCollide() {
        try {
            new ScoreSlurSource(List.of(arc("same")), List.of(), List.of(arc("same")));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void conflictingJoinedClassificationCannotSilentlyPromote() {
        var candidate = new ScoreSlurSource(List.of(), List.of(), List.of(arc("same")));
        var phrase = new ScoreSlurSource(List.of(arc("same")), List.of());
        for (var pages : List.of(List.of(candidate, phrase), List.of(phrase, candidate))) {
            try {
                ScoreSlurSource.join(pages);
                fail();
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void candidateListsAreImmutable() {
        var input = new ArrayList<>(List.of(arc("pending")));
        var source = new ScoreSlurSource(List.of(), List.of(), input);
        input.clear();
        assertEquals(1, source.candidates().size());
        try {
            source.candidates().clear();
            fail();
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void duplicateCandidateIdsFailCapture() {
        try {
            ScoreSlurSource.capture(List.of(), List.of(arc("same"), arc("same")), List.of());
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }
}
