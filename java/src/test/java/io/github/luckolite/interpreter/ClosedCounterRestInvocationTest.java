// Copyright 2026 Luckolite and Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

public class ClosedCounterRestInvocationTest {
    @Test
    public void laterFundingRefusalDoesNotPublishAnotherProof() {
        var raw = ClosedCounterRestInkTest.bowl();
        var notes = ClosedCounterRestInkTest.notes(raw);
        var invocation = ClosedCounterRestInvocation.open(raw, 400, 240, notes);
        assertNotNull(invocation);
        var proposal = ClosedCounterRestInkTest.proposal(raw, raw, 140, 147, 90, 110);
        assertTrue(
                invocation.disproves(
                        true, true, raw, 400, 240, notes, proposal, 140, 147, 90, 110, 10));
        long used = invocation.budget.workUsed;
        assertTrue(used > 128);
        invocation.budget.take(invocation.budget.workLimit - used, 0);
        assertFalse(
                invocation.disproves(
                        true, true, raw, 400, 240, notes, proposal, 140, 147, 90, 110, 10));
        assertTrue(invocation.budget.failed);
        assertEquals(invocation.budget.workLimit, invocation.budget.workUsed);
    }

    @Test
    public void ordinaryAndQuarterOnlyPayEntryFlags() {
        var raw = ClosedCounterRestInkTest.bowl();
        var notes = ClosedCounterRestInkTest.notes(raw);
        var i = ClosedCounterRestInvocation.open(raw, 400, 240, notes);
        long w = i.budget.workUsed, b = i.budget.bytesUsed;
        assertFalse(i.disproves(false, true, raw, 400, 240, notes, null, 140, 147, 90, 110, 10));
        assertFalse(i.disproves(true, false, raw, 400, 240, notes, null, 140, 147, 90, 110, 10));
        assertEquals(w + 4, i.budget.workUsed);
        assertEquals(b, i.budget.bytesUsed);
    }

    @Test
    public void cloneOtherRootAndWrongThreadCannotBorrowScope() throws Exception {
        var raw = ClosedCounterRestInkTest.bowl();
        var notes = ClosedCounterRestInkTest.notes(raw);
        var i = ClosedCounterRestInvocation.open(raw, 400, 240, notes);
        var clone = raw.clone();
        var other = ClosedCounterRestInkTest.notes(clone);
        assertFalse(
                i.disproves(
                        true,
                        true,
                        clone,
                        400,
                        240,
                        other,
                        ClosedCounterRestInkTest.proposal(clone, clone, 140, 147, 90, 110),
                        140,
                        147,
                        90,
                        110,
                        10));
        boolean[] result = {true};
        var t =
                new Thread(
                        () ->
                                result[0] =
                                        i.disproves(
                                                true,
                                                true,
                                                raw,
                                                400,
                                                240,
                                                notes,
                                                ClosedCounterRestInkTest.proposal(
                                                        raw, raw, 140, 147, 90, 110),
                                                140,
                                                147,
                                                90,
                                                110,
                                                10));
        t.start();
        t.join();
        assertFalse(result[0]);
    }

    @Test
    public void dimensionsAndOptionalCapacityRefuseWithoutOverflow() {
        assertNull(
                ClosedCounterRestInvocation.open(
                        new byte[1], Integer.MAX_VALUE, Integer.MAX_VALUE, java.util.List.of()));
        assertNull(
                ClosedCounterRestInvocation.open(
                        new byte[20_000_001], 20_000_001, 1, java.util.List.of()));
        assertNull(ClosedCounterRestInvocation.open(null, 400, 240, java.util.List.of()));
    }
}
