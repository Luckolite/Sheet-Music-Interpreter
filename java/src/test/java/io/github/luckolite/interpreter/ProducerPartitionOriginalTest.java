// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ProducerPartitionOriginalTest {
    static ExactComponentMembers.Budget budget() {
        return new ExactComponentMembers.Budget(1000000, 1000000);
    }

    static final class Scene {
        byte[] g = new byte[100], l = new byte[100];
        Object parent = new Object(), a = new Object(), b = new Object();
        ExactComponentMembers.Collector c;

        Scene() {
            for (int y : new int[] {3, 4}) for (int x = 2; x <= 7; x++) l[y * 10 + x] = 2;
            c = new ExactComponentMembers.Collector(g, l, 10, 10, budget(), budget());
            assertTrue(c.bindInput(l, g, 10, 10));
            var body = c.begin();
            body.add(3, 2, 7);
            body.add(4, 2, 7);
            c.record(
                    parent,
                    new ExactComponentMembers.Stats(12, 2, 7, 3, 4, 4.5f, 3.5f),
                    (byte) 2,
                    body);
        }

        void split() {
            var split = c.beginSplit(parent, l, 10);
            var aa = split.beginChild();
            aa.add(3, 2, 7);
            split.capture(a, new ExactComponentMembers.Stats(6, 2, 7, 3, 3, 4.5f, 3), aa);
            var bb = split.beginChild();
            bb.add(4, 2, 7);
            split.capture(b, new ExactComponentMembers.Stats(6, 2, 7, 4, 4, 4.5f, 4), bb);
            assertTrue(split.commit(List.of(a, b)));
        }

        void seal() {
            assertTrue(c.seal(budget()));
        }
    }

    @Test
    public void genuineParentAndEverySplitChildAreRetained() {
        var s = new Scene();
        s.split();
        s.seal();
        var p = s.c.producerPartition(s.a, budget());
        assertNotNull(p);
        assertSame(s.parent, p.parentIdentity());
        assertSame(s.a, p.selectedIdentity());
        assertEquals(12, p.parentPixels().size());
        assertEquals(2, p.leaves().size());
        assertTrue(p.residual().isEmpty());
        assertTrue(p.authenticates(s.l, s.g, 10, 10, budget()));
        assertNull(s.c.producerFootprint(s.parent, budget()));
    }

    @Test
    public void originalUnsplitComponentStillHasLiteralMembership() {
        var s = new Scene();
        s.seal();
        var p = s.c.producerPartition(s.parent, budget());
        assertEquals(1, p.leaves().size());
        assertEquals(p.parentPixels(), p.leaves().get(0).pixels());
        assertTrue(p.residual().isEmpty());
    }

    @Test
    public void equalValueForeignObjectAndPlaneNeverBorrowLineage() {
        var s = new Scene();
        s.split();
        s.seal();
        assertNull(s.c.producerPartition(new Object(), budget()));
        var p = s.c.producerPartition(s.a, budget());
        assertFalse(p.authenticates(s.l.clone(), s.g, 10, 10, budget()));
        assertFalse(p.authenticates(s.l, s.g.clone(), 10, 10, budget()));
    }

    @Test
    public void mutationAndBudgetRefuse() {
        var s = new Scene();
        s.split();
        s.seal();
        assertNull(s.c.producerPartition(s.a, new ExactComponentMembers.Budget(0, 0)));
        var p = s.c.producerPartition(s.a, budget());
        s.g[99] = 1;
        assertFalse(p.authenticates(s.l, s.g, 10, 10, budget()));
        assertNull(s.c.producerPartition(s.a, budget()));
    }

    @Test
    public void proofConstructorIsPrivateAndAllVectorsImmutable() {
        assertTrue(
                Arrays.stream(
                                ExactComponentMembers.ProducerPartition.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var s = new Scene();
        s.split();
        s.seal();
        var p = s.c.producerPartition(s.a, budget());
        assertThrows(UnsupportedOperationException.class, () -> p.parentPixels().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.leaves().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.leaves().get(0).pixels().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.residual().clear());
    }

    @Test
    public void missingCurrentLeafIsExportedAsExplicitUnknownResidual() throws Exception {
        var s = new Scene();
        s.split();
        s.seal();
        var f = s.c.getClass().getDeclaredField("records");
        f.setAccessible(true);
        ((Map<?, ?>) f.get(s.c)).remove(s.b);
        var p = s.c.producerPartition(s.a, budget());
        assertNotNull(p);
        assertEquals(6, p.residual().size());
        assertEquals(1, p.leaves().size());
        assertNull(
                LobeExtentProposal.derivePartition(s.l, s.g, 10, 10, p, 10, new StringBuilder()));
    }
}
