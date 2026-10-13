// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreSlurPagesTest {
    private ScoreSlurPages.Span span(int startPage, int startNote, int endPage, int endNote) {
        return new ScoreSlurPages.Span(
                "original-phrase",
                new ScoreSlurPages.Anchor(startPage, startNote),
                new ScoreSlurPages.Anchor(endPage, endNote),
                ScoreSlurSpan.Placement.ABOVE,
                ScoreSlurSpan.LineType.DOTTED,
                List.of(new ScoreSlurSpan.ArcEvidence(startPage, .8f, .1f, .1f, .2f)));
    }

    @Test
    public void skippedAndEmptyPhysicalPagesHaveExactOffsets() {
        var source = span(3, 2, 10, 1);
        var joined =
                ScoreSlurPages.join(
                        List.of(source),
                        List.of(
                                new ScoreSlurPages.Page(1, 4), new ScoreSlurPages.Page(3, 3),
                                new ScoreSlurPages.Page(8, 0), new ScoreSlurPages.Page(10, 5)));
        assertEquals(12, joined.noteCount());
        assertTrue(joined.unresolved().isEmpty());
        var resolved = joined.resolved().get(0);
        assertEquals(6, resolved.startOwner());
        assertEquals(8, resolved.endOwner());
        assertEquals(source.evidence(), resolved.evidence());
        assertEquals(source.id(), resolved.id());
        assertEquals(source.placement(), resolved.placement());
        assertEquals(source.lineType(), resolved.lineType());
    }

    @Test
    public void explicitPageOrderIsPreservedForLaterMusicalValidation() {
        var joined =
                ScoreSlurPages.join(
                        List.of(span(3, 2, 10, 1)),
                        List.of(new ScoreSlurPages.Page(10, 5), new ScoreSlurPages.Page(3, 3)));
        assertEquals(7, joined.resolved().get(0).startOwner());
        assertEquals(1, joined.resolved().get(0).endOwner());
    }

    @Test
    public void missingPageKeepsEvidenceAndDoesNotAttachToAdjacentPage() {
        var source = span(3, 2, 10, 1);
        var joined = ScoreSlurPages.join(List.of(source), List.of(new ScoreSlurPages.Page(3, 20)));
        assertTrue(joined.resolved().isEmpty());
        assertEquals(
                new ScoreSlurPages.Unresolved(
                        source,
                        ScoreSlurPages.Missing.NONE,
                        ScoreSlurPages.Missing.PAGE_NOT_JOINED),
                joined.unresolved().get(0));
    }

    @Test
    public void outOfRangeLocalOwnerDoesNotBleedIntoFollowingPage() {
        var source = span(3, 3, 10, 1);
        var joined =
                ScoreSlurPages.join(
                        List.of(source),
                        List.of(new ScoreSlurPages.Page(3, 3), new ScoreSlurPages.Page(10, 5)));
        assertTrue(joined.resolved().isEmpty());
        assertEquals(ScoreSlurPages.Missing.NOTE_NOT_ON_PAGE, joined.unresolved().get(0).start());
    }

    @Test
    public void duplicatePhysicalPageOrSpanIsRejected() {
        var source = span(3, 0, 3, 1);
        var page = new ScoreSlurPages.Page(3, 2);
        try {
            ScoreSlurPages.join(List.of(source), List.of(page, page));
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            ScoreSlurPages.join(List.of(source, source), List.of(page));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void joinedNoteCountCannotOverflow() {
        try {
            ScoreSlurPages.join(
                    List.of(),
                    List.of(
                            new ScoreSlurPages.Page(0, Integer.MAX_VALUE),
                            new ScoreSlurPages.Page(1, 1)));
            fail();
        } catch (ArithmeticException expected) {
        }
    }
}
