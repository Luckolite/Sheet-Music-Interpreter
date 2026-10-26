// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Actual proof/whole-analyze measured exact and one-under controls; no formula mirror. */
public final class RawNaturalActualPartBudgetTest {
    static NaturalCourtesyProofControls literal() {
        var f = new NaturalCourtesyProofControls();
        f.rect(513, 111, 2, 30, (byte) 0);
        f.rect(522, 124, 2, 30, (byte) 0);
        f.rect(513, 127, 11, 3, (byte) 0);
        f.rect(513, 139, 11, 3, (byte) 0);
        return f;
    }

    static long[] measureRaw() throws Exception {
        Object b = CancellationBudgetTest.budget(64_000_000, 16L * 1024 * 1024);
        assertNotNull(CancellationBudgetTest.raw(b, literal().gray));
        return new long[] {
            CancellationBudgetTest.used(b, "workUsed"), CancellationBudgetTest.used(b, "bytesUsed")
        };
    }

    @Test
    public void rawProofFitsExactWorkAndBytes() throws Exception {
        long[] n = measureRaw();
        Object b = CancellationBudgetTest.budget(n[0], n[1]);
        assertNotNull(CancellationBudgetTest.raw(b, literal().gray));
        assertEquals(n[0], CancellationBudgetTest.used(b, "workUsed"));
        assertEquals(n[1], CancellationBudgetTest.used(b, "bytesUsed"));
        assertFalse(CancellationBudgetTest.exhausted(b));
    }

    @Test
    public void rawProofOneUnderWorkRefuses() throws Exception {
        long[] n = measureRaw();
        Object b = CancellationBudgetTest.budget(n[0] - 1, n[1]);
        assertNull(CancellationBudgetTest.raw(b, literal().gray));
        assertTrue(CancellationBudgetTest.exhausted(b));
        assertTrue(CancellationBudgetTest.used(b, "workUsed") <= n[0] - 1);
    }

    @Test
    public void rawProofOneUnderBytesRefuses() throws Exception {
        long[] n = measureRaw();
        Object b = CancellationBudgetTest.budget(n[0], n[1] - 1);
        assertNull(CancellationBudgetTest.raw(b, literal().gray));
        assertTrue(CancellationBudgetTest.exhausted(b));
        assertTrue(CancellationBudgetTest.used(b, "bytesUsed") <= n[1] - 1);
    }

    static long[] measureAnalyze() throws Exception {
        var f = CancellationAuthorityTest.fixture(1, false, true, 0);
        Object b = CancellationBudgetTest.budget(64_000_000, 16L * 1024 * 1024);
        assertEquals(
                java.util.List.of(1, 0),
                CancellationBudgetTest.keys(CancellationBudgetTest.analyze(f, b)));
        return new long[] {
            CancellationBudgetTest.used(b, "workUsed"), CancellationBudgetTest.used(b, "bytesUsed")
        };
    }

    @Test
    public void sharedEarlyFinalFitsExactWholeOperation() throws Exception {
        long[] n = measureAnalyze();
        var f = CancellationAuthorityTest.fixture(1, false, true, 0);
        Object b = CancellationBudgetTest.budget(n[0], n[1]);
        assertEquals(
                java.util.List.of(1, 0),
                CancellationBudgetTest.keys(CancellationBudgetTest.analyze(f, b)));
        assertEquals(n[0], CancellationBudgetTest.used(b, "workUsed"));
        assertEquals(n[1], CancellationBudgetTest.used(b, "bytesUsed"));
    }

    @Test
    public void sharedEarlyFinalOneUnderWorkCannotReset() throws Exception {
        long[] n = measureAnalyze();
        var f = CancellationAuthorityTest.fixture(1, false, true, 0);
        Object b = CancellationBudgetTest.budget(n[0] - 1, n[1]);
        assertEquals(
                java.util.List.of(1),
                CancellationBudgetTest.keys(CancellationBudgetTest.analyze(f, b)));
        assertTrue(CancellationBudgetTest.exhausted(b));
    }

    @Test
    public void sharedEarlyFinalOneUnderBytesCannotReset() throws Exception {
        long[] n = measureAnalyze();
        var f = CancellationAuthorityTest.fixture(1, false, true, 0);
        Object b = CancellationBudgetTest.budget(n[0], n[1] - 1);
        assertEquals(
                java.util.List.of(1),
                CancellationBudgetTest.keys(CancellationBudgetTest.analyze(f, b)));
        assertTrue(CancellationBudgetTest.exhausted(b));
    }
}
