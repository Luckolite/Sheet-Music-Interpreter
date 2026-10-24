// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ClockProducerSourceOriginalTest {
    private static ExactComponentMembers.Budget budget() {
        return new ExactComponentMembers.Budget(1000000, 1000000);
    }

    private static ExactComponentMembers.Collector collector(
            byte[] gray, byte[] labels, Object first, Object second) {
        var c = new ExactComponentMembers.Collector(gray, labels, 10, 10, budget(), budget());
        assertTrue(c.bindInput(labels, gray, 10, 10));
        var a = c.begin();
        a.add(1, 1, 3);
        c.record(first, new ExactComponentMembers.Stats(3, 1, 3, 1, 1, 2, 1), (byte) 2, a);
        var b = c.begin();
        b.add(3, 1, 3);
        c.record(second, new ExactComponentMembers.Stats(3, 1, 3, 3, 3, 2, 3), (byte) 2, b);
        assertTrue(c.seal(budget()));
        return c;
    }

    private static byte[] labels() {
        byte[] l = new byte[100];
        for (int i : new int[] {11, 12, 13, 31, 32, 33}) l[i] = 2;
        return l;
    }

    @Test
    public void completeTraversalAndUnknownFinalOwnerBothSurvive() {
        Object a = new Object(), b = new Object(), unbound = new Object();
        var c = collector(new byte[100], labels(), a, b);
        var ledger =
                ScoreClockProducerSource.capture(
                        c, List.of(a, b), List.of(a, unbound), 2, budget());
        assertEquals(3, ledger.emissions().size());
        assertEquals(2, ledger.finalNotes());
        assertEquals(List.of(11, 12, 13), ledger.emissions().get(0).pixels());
        assertEquals(List.of(0), ledger.emissions().get(0).finalNoteOwners());
        assertEquals(List.of(31, 32, 33), ledger.emissions().get(1).pixels());
        assertTrue(ledger.emissions().get(1).finalNoteOwners().isEmpty());
        assertEquals("UNKNOWN_MISSING_PRODUCER_TOKEN", ledger.emissions().get(2).disposition());
        assertEquals(List.of(1), ledger.emissions().get(2).finalNoteOwners());
        assertFalse(ledger.missingAuthorities().isEmpty());
    }

    @Test
    public void sameBoxObjectCannotBorrowARealToken() {
        Object a = new Object(), b = new Object(), sameBoxButForeign = new Object();
        var c = collector(new byte[100], labels(), a, b);
        var ledger =
                ScoreClockProducerSource.capture(
                        c, List.of(a, b), List.of(sameBoxButForeign), 1, budget());
        assertTrue(ledger.emissions().get(2).pixels().isEmpty());
        assertEquals("UNKNOWN_MISSING_PRODUCER_TOKEN", ledger.emissions().get(2).disposition());
    }

    @Test
    public void separateVoicesOnOneHeadDoNotMintVoiceOwnership() {
        Object a = new Object(), b = new Object();
        var c = collector(new byte[100], labels(), a, b);
        var ledger =
                ScoreClockProducerSource.capture(c, List.of(a, b, a), List.of(a, a), 2, budget());
        assertEquals(2, ledger.emissions().size());
        assertEquals(List.of(0, 1), ledger.emissions().get(0).finalNoteOwners());
        assertTrue(ledger.missingAuthorities().contains("COMPLETE_VOICE_AND_REST_OWNERSHIP"));
    }

    @Test
    public void sourceMutationAndExhaustionKeepAllRowsUnknown() {
        for (boolean mutation : new boolean[] {false, true}) {
            Object a = new Object(), b = new Object();
            byte[] gray = new byte[100];
            var c = collector(gray, labels(), a, b);
            if (mutation) gray[50] = 1;
            var ledger =
                    ScoreClockProducerSource.capture(
                            c,
                            List.of(a, b),
                            List.of(a),
                            1,
                            mutation ? budget() : new ExactComponentMembers.Budget(0, 0));
            assertEquals(2, ledger.emissions().size());
            assertTrue(ledger.emissions().stream().allMatch(e -> e.pixels().isEmpty()));
        }
    }

    @Test
    public void emittedMembersAndOwnerListsAreImmutable() {
        Object a = new Object(), b = new Object();
        var c = collector(new byte[100], labels(), a, b);
        var ledger = ScoreClockProducerSource.capture(c, List.of(a, b), List.of(a), 1, budget());
        assertThrows(
                UnsupportedOperationException.class,
                () -> ledger.emissions().get(0).pixels().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> ledger.emissions().get(0).finalNoteOwners().clear());
    }

    @Test
    public void semanticPopulationIsMeasuredAndMissingEmissionsRemainVisible() {
        Object a = new Object(), b = new Object();
        var c = collector(new byte[100], labels(), a, b);
        var partial = ScoreClockProducerSource.capture(c, List.of(a), List.of(a), 1, budget());
        assertEquals(6, partial.accounting().population());
        assertEquals(3, partial.accounting().exported());
        assertEquals(3, partial.accounting().unique());
        assertTrue(partial.missingAuthorities().contains("COMPLETE_VOICE_AND_REST_OWNERSHIP"));
    }

    @Test
    public void exactMemberAccountingDoesNotAssertRhythmicCompleteness() {
        Object a = new Object(), b = new Object();
        var c = collector(new byte[100], labels(), a, b);
        var ledger = ScoreClockProducerSource.capture(c, List.of(a, b), List.of(a), 1, budget());
        assertEquals(6, ledger.accounting().population());
        assertEquals(6, ledger.accounting().exported());
        assertEquals(6, ledger.accounting().unique());
        assertEquals(3, ledger.accounting().assignedNotes());
        assertEquals(0, ledger.accounting().provedNonNotes());
        assertTrue(ledger.missingAuthorities().contains("EXHAUSTIVE_RHYTHMIC_GLYPH_DISPOSITIONS"));
    }
}
