// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Entirely original cancellation scenes need no remote-ledger storage. */
public class LazyRemoteLedgerBudgetOriginalTest {
    static void prove(int heads, boolean raw) throws Exception {
        var scene = CancellationAuthorityTest.fixture(0, !raw, raw, 0);
        if (heads < 2)
            for (int i = 0; i < scene.labels.length; i++)
                if (scene.labels[i] == 2 && (heads == 0 || i / JoinedSignatureSharpTest.W > 200))
                    scene.labels[i] = 0;
        Object budget = CancellationBudgetTest.budget(64_000_000, 16L * 1024 * 1024);
        var actual = CancellationBudgetTest.analyze(scene, budget);
        assertEquals(List.of(), CancellationBudgetTest.keys(actual));
        assertEquals(0, CancellationBudgetTest.used(budget, "workUsed"));
        assertEquals(0, CancellationBudgetTest.used(budget, "bytesUsed"));
        assertFalse(CancellationBudgetTest.exhausted(budget));
    }

    @Test
    public void ordinaryTwoHeadSceneDoesNotAllocateLedgerStorage() throws Exception {
        prove(2, true);
    }

    @Test
    public void noHeadSceneDoesNotAllocateLedgerStorage() throws Exception {
        prove(0, true);
    }

    @Test
    public void singleOrdinaryHeadDoesNotAllocateLedgerStorage() throws Exception {
        prove(1, true);
    }

    @Test
    public void semanticNaturalsWithoutPriorNeedNoLedgerStorage() throws Exception {
        prove(2, false);
    }

    @Test
    public void unfundedOrdinarySceneDoesNotPoisonOperation() throws Exception {
        var scene = CancellationAuthorityTest.fixture(0, false, true, 0);
        Object b = CancellationBudgetTest.budget(0, 0);
        assertEquals(
                List.of(), CancellationBudgetTest.keys(CancellationBudgetTest.analyze(scene, b)));
        assertEquals(0, CancellationBudgetTest.used(b, "workUsed"));
        assertEquals(0, CancellationBudgetTest.used(b, "bytesUsed"));
        assertFalse(CancellationBudgetTest.exhausted(b));
    }
}
