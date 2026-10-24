// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** An unexamined alternative cannot turn a bounded search prefix into unique ownership. */
public class PrintedSystemSlurBudgetOriginalsTest {
    private static PrintedSystemSlurJoinOriginalsTest.Field ambiguous() {
        var f = new PrintedSystemSlurJoinOriginalsTest.Field(true, 1, 0);
        var original = f.frames.get(1);
        var heads = new ArrayList<>(original.heads());
        // Crowded retained metadata makes the later shaft-supported alternative costlier
        // than the first head-adjacent cap. Every owner still has a distinct source identity.
        for (int i = 0; i < 100; i++) {
            int owner = f.notes.size();
            heads.add(new PrintedSlurOwners.GeometryHead(owner, 400, 480, 0, 1));
            f.notes.add(
                    new ScoreNoteEvent(
                            1,
                            .6f,
                            2,
                            0,
                            1,
                            480f / f.h,
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            1));
        }
        var frame =
                new PrintedSlurPageCapture.Frame<>(
                        original.left(),
                        original.top(),
                        original.right(),
                        original.bottom(),
                        original.track(),
                        heads);
        f.frames.set(1, frame);
        f.pieces.set(1, new PrintedSystemSlurJoins.Observed(frame, f.pieces.get(1).bow()));
        for (int x = 278; x <= 281; x++) for (int y = 419; y <= 460; y++) f.gray[y * f.w + x] = 0;
        f.pieces.add(
                new PrintedSystemSlurJoins.Observed(frame, f.curve(1, 102, 431, 280, 420, 24)));
        return f;
    }

    @Test
    public void exhaustedAlternativeHalfCannotLeaveAUniquePrefix() {
        assertTrue(ambiguous().joined(505_000).isEmpty());
    }

    @Test
    public void exhaustedAlternativePairCannotLeaveAnEarlierMatch() {
        assertTrue(ambiguous().joined(1_010_000).isEmpty());
    }

    @Test
    public void noPartialAllowanceMayCreateUniquenessAbsentAtFullBudget() {
        var f = ambiguous();
        assertTrue(f.joined().isEmpty());
        for (long budget = 200_000; budget <= 1_300_000; budget += 10_000)
            assertTrue("unexamined rival at budget " + budget, f.joined(budget).isEmpty());
    }

    @Test
    public void reversedAlternativeOrderCannotExposeAPrefix() {
        var f = ambiguous();
        Collections.reverse(f.pieces);
        for (long budget : new long[] {505_000, 1_010_000, 8_000_000})
            assertTrue(f.joined(budget).isEmpty());
    }

    @Test
    public void fullBudgetStillSeesBothAmbiguousAlternatives() {
        assertTrue(ambiguous().joined().isEmpty());
    }

    @Test
    public void aCompleteUnambiguousSearchStillPublishesBothPhysicalPieces() {
        var f = new PrintedSystemSlurJoinOriginalsTest.Field(true, 1, 0);
        var spans = f.joined();
        assertEquals(1, spans.size());
        assertEquals(2, spans.get(0).evidence().size());
    }
}
